package com.aubb.server.modules.organization.application;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.audit.application.AuditLogApplicationService;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.iam.GovernanceAuthorizationService;
import com.aubb.server.modules.organization.domain.OrgUnitType;
import com.aubb.server.modules.organization.domain.OrganizationPolicy;
import com.aubb.server.modules.organization.domain.OrganizationValidationResult;
import com.aubb.server.modules.organization.infrastructure.OrgUnitEntity;
import com.aubb.server.modules.organization.infrastructure.OrgUnitMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizationApplicationService {

    private final OrgUnitMapper orgUnitMapper;
    private final AuditLogApplicationService auditLogApplicationService;
    private final GovernanceAuthorizationService governanceAuthorizationService;
    private final OrganizationPolicy organizationPolicy = new OrganizationPolicy();

    @Transactional
    public OrgUnitTreeNode createOrgUnit(
            String name,
            String code,
            OrgUnitType type,
            Long parentId,
            int sortOrder,
            AuthenticatedUserPrincipal principal) {
        String normalizedCode = normalizeCode(code);
        if (orgUnitMapper.selectOne(Wrappers.<OrgUnitEntity>lambdaQuery()
                        .eq(OrgUnitEntity::getCode, normalizedCode)
                        .last("LIMIT 1"))
                != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "ORG_CODE_DUPLICATED", "组织编码已存在");
        }

        governanceAuthorizationService.assertCanCreateOrg(principal, parentId, type);

        int level;
        if (parentId == null) {
            OrganizationValidationResult validationResult = organizationPolicy.validateRoot(type);
            if (!validationResult.valid()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "ORG_HIERARCHY_INVALID", validationResult.reason());
            }
            level = validationResult.childLevel();
        } else {
            OrgUnitEntity parent = orgUnitMapper.selectById(parentId);
            if (parent == null) {
                throw new BusinessException(HttpStatus.NOT_FOUND, "ORG_PARENT_NOT_FOUND", "上级组织不存在");
            }
            OrganizationValidationResult validationResult =
                    organizationPolicy.validateChild(OrgUnitType.valueOf(parent.getType()), type, parent.getLevel());
            if (!validationResult.valid()) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "ORG_HIERARCHY_INVALID", validationResult.reason());
            }
            level = validationResult.childLevel();
        }

        OrgUnitEntity entity = new OrgUnitEntity();
        entity.setParentId(parentId);
        entity.setCode(normalizedCode);
        entity.setName(name);
        entity.setType(type.name());
        entity.setLevel(level);
        entity.setSortOrder(sortOrder);
        entity.setStatus("ACTIVE");
        orgUnitMapper.insert(entity);

        auditLogApplicationService.record(
                principal.getUserId(),
                AuditAction.ORG_UNIT_CREATED,
                "ORG_UNIT",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                Map.of("code", normalizedCode, "name", name, "type", type.name()));
        return toNode(entity);
    }

    @Transactional
    public OrgUnitSummaryView createCourseManagedClassUnit(
            String name, String code, Long courseOrgUnitId, int sortOrder, Long actorUserId) {
        String normalizedCode = normalizeCode(code);
        if (orgUnitMapper.selectOne(Wrappers.<OrgUnitEntity>lambdaQuery()
                        .eq(OrgUnitEntity::getCode, normalizedCode)
                        .last("LIMIT 1"))
                != null) {
            throw new BusinessException(HttpStatus.CONFLICT, "ORG_CODE_DUPLICATED", "组织编码已存在");
        }

        OrgUnitEntity parent = orgUnitMapper.selectById(courseOrgUnitId);
        if (parent == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "ORG_PARENT_NOT_FOUND", "上级课程组织不存在");
        }
        OrganizationValidationResult validationResult = organizationPolicy.validateChild(
                OrgUnitType.valueOf(parent.getType()), OrgUnitType.CLASS, parent.getLevel());
        if (!validationResult.valid()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ORG_HIERARCHY_INVALID", validationResult.reason());
        }

        OrgUnitEntity entity = new OrgUnitEntity();
        entity.setParentId(courseOrgUnitId);
        entity.setCode(normalizedCode);
        entity.setName(name);
        entity.setType(OrgUnitType.CLASS.name());
        entity.setLevel(validationResult.childLevel());
        entity.setSortOrder(sortOrder);
        entity.setStatus("ACTIVE");
        orgUnitMapper.insert(entity);

        auditLogApplicationService.record(
                actorUserId,
                AuditAction.ORG_UNIT_CREATED,
                "ORG_UNIT",
                String.valueOf(entity.getId()),
                AuditResult.SUCCESS,
                Map.of(
                        "code",
                        normalizedCode,
                        "name",
                        name,
                        "type",
                        OrgUnitType.CLASS.name(),
                        "source",
                        "COURSE_MODULE"));
        return toSummary(entity);
    }

    @Transactional(readOnly = true)
    public List<OrgUnitTreeNode> getTree(AuthenticatedUserPrincipal principal) {
        List<OrgUnitEntity> entities = orgUnitMapper.selectList(Wrappers.<OrgUnitEntity>lambdaQuery()
                .orderByAsc(OrgUnitEntity::getLevel)
                .orderByAsc(OrgUnitEntity::getSortOrder)
                .orderByAsc(OrgUnitEntity::getId));
        Map<Long, OrgUnitTreeNode> nodeIndex = new LinkedHashMap<>();
        for (OrgUnitEntity entity : entities) {
            nodeIndex.put(entity.getId(), toNode(entity));
        }
        for (OrgUnitEntity entity : entities) {
            if (entity.getParentId() != null) {
                OrgUnitTreeNode parent = nodeIndex.get(entity.getParentId());
                if (parent != null) {
                    parent.addChild(nodeIndex.get(entity.getId()));
                }
            }
        }

        Set<Long> visibleRoots = governanceAuthorizationService.visibleScopeRootIds(principal);
        Map<Long, OrgUnitEntity> orgUnitIndex = governanceAuthorizationService.loadOrgUnitIndex();
        List<OrgUnitTreeNode> roots = new ArrayList<>();
        for (Long rootId : visibleRoots.stream().sorted().toList()) {
            boolean coveredByAnotherRoot = visibleRoots.stream()
                    .filter(other -> !other.equals(rootId))
                    .anyMatch(other -> governanceAuthorizationService.isDescendantOrSelf(rootId, other, orgUnitIndex));
            if (!coveredByAnotherRoot) {
                OrgUnitTreeNode root = nodeIndex.get(rootId);
                if (root != null) {
                    roots.add(root);
                }
            }
        }
        return roots;
    }

    @Transactional(readOnly = true)
    public Long findOrgUnitIdByCode(String code) {
        OrgUnitEntity entity = orgUnitMapper.selectOne(Wrappers.<OrgUnitEntity>lambdaQuery()
                .eq(OrgUnitEntity::getCode, normalizeCode(code))
                .last("LIMIT 1"));
        return entity == null ? null : entity.getId();
    }

    @Transactional(readOnly = true)
    public boolean existsById(Long orgUnitId) {
        return orgUnitId != null && orgUnitMapper.selectById(orgUnitId) != null;
    }

    @Transactional(readOnly = true)
    public Map<Long, OrgUnitSummaryView> loadSummaryMap(Collection<Long> orgUnitIds) {
        if (orgUnitIds == null || orgUnitIds.isEmpty()) {
            return Map.of();
        }
        return orgUnitMapper.selectByIds(orgUnitIds).stream()
                .collect(Collectors.toMap(
                        OrgUnitEntity::getId, this::toSummary, (left, right) -> left, LinkedHashMap::new));
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "ORG_CODE_REQUIRED", "组织编码不能为空");
        }
        return code.trim().toUpperCase();
    }

    private OrgUnitSummaryView toSummary(OrgUnitEntity entity) {
        return new OrgUnitSummaryView(entity.getId(), entity.getCode(), entity.getName(), entity.getType());
    }

    private OrgUnitTreeNode toNode(OrgUnitEntity entity) {
        return new OrgUnitTreeNode(
                entity.getId(),
                entity.getParentId(),
                entity.getCode(),
                entity.getName(),
                entity.getType(),
                entity.getLevel(),
                entity.getSortOrder(),
                entity.getStatus(),
                entity.getCreatedAt());
    }
}
