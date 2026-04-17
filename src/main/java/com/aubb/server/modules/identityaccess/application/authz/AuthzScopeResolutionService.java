package com.aubb.server.modules.identityaccess.application.authz;

import com.aubb.server.common.exception.BusinessException;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingCollegeMapEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingCollegeMapMapper;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingEntity;
import com.aubb.server.modules.course.infrastructure.offering.CourseOfferingMapper;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassEntity;
import com.aubb.server.modules.course.infrastructure.teaching.TeachingClassMapper;
import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;
import com.aubb.server.modules.organization.domain.OrgUnitType;
import com.aubb.server.modules.organization.infrastructure.OrgUnitEntity;
import com.aubb.server.modules.organization.infrastructure.OrgUnitMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthzScopeResolutionService {

    private final CourseOfferingMapper courseOfferingMapper;
    private final CourseOfferingCollegeMapMapper courseOfferingCollegeMapMapper;
    private final TeachingClassMapper teachingClassMapper;
    private final OrgUnitMapper orgUnitMapper;

    @Transactional(readOnly = true)
    public ScopeRef resolveScope(AuthorizationScopeType scopeType, Long scopeRefId) {
        return switch (scopeType) {
            case SCHOOL, COLLEGE, COURSE -> new ScopeRef(scopeType, scopeRefId);
            case OFFERING -> resolveOfferingScope(scopeRefId);
            case CLASS -> resolveTeachingClassScope(scopeRefId);
        };
    }

    @Transactional(readOnly = true)
    public Long findTeachingClassIdByOrgClassUnitId(Long orgClassUnitId) {
        if (orgClassUnitId == null) {
            return null;
        }
        TeachingClassEntity teachingClass = teachingClassMapper.selectOne(Wrappers.<TeachingClassEntity>lambdaQuery()
                .eq(TeachingClassEntity::getOrgClassUnitId, orgClassUnitId)
                .last("LIMIT 1"));
        return teachingClass == null ? null : teachingClass.getId();
    }

    private ScopeRef resolveOfferingScope(Long offeringId) {
        CourseOfferingEntity offering = courseOfferingMapper.selectById(offeringId);
        if (offering == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "COURSE_OFFERING_NOT_FOUND", "开课不存在");
        }
        List<ScopeRef> ancestors = new ArrayList<>();
        collectOrgScopes(ancestors, offering.getOrgCourseUnitId());
        collectOrgScopes(ancestors, offering.getPrimaryCollegeUnitId());
        courseOfferingCollegeMapMapper
                .selectList(Wrappers.<CourseOfferingCollegeMapEntity>lambdaQuery()
                        .eq(CourseOfferingCollegeMapEntity::getOfferingId, offeringId))
                .stream()
                .map(CourseOfferingCollegeMapEntity::getCollegeUnitId)
                .forEach(collegeUnitId -> collectOrgScopes(ancestors, collegeUnitId));
        return new ScopeRef(AuthorizationScopeType.OFFERING, offeringId, deduplicateScopes(ancestors));
    }

    private ScopeRef resolveTeachingClassScope(Long teachingClassId) {
        TeachingClassEntity teachingClass = teachingClassMapper.selectById(teachingClassId);
        if (teachingClass == null) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "TEACHING_CLASS_NOT_FOUND", "教学班不存在");
        }
        ScopeRef offeringScope = resolveOfferingScope(teachingClass.getOfferingId());
        List<ScopeRef> ancestors = new ArrayList<>();
        ancestors.add(new ScopeRef(AuthorizationScopeType.OFFERING, teachingClass.getOfferingId()));
        ancestors.addAll(offeringScope.ancestors());
        return new ScopeRef(AuthorizationScopeType.CLASS, teachingClassId, deduplicateScopes(ancestors));
    }

    private void collectOrgScopes(List<ScopeRef> scopes, Long orgUnitId) {
        Long cursor = orgUnitId;
        while (cursor != null) {
            OrgUnitEntity orgUnit = orgUnitMapper.selectById(cursor);
            if (orgUnit == null) {
                return;
            }
            AuthorizationScopeType scopeType = mapScopeType(orgUnit.getType());
            if (scopeType != null) {
                scopes.add(new ScopeRef(scopeType, orgUnit.getId()));
            }
            cursor = orgUnit.getParentId();
        }
    }

    private List<ScopeRef> deduplicateScopes(List<ScopeRef> scopes) {
        Map<String, ScopeRef> ordered = new LinkedHashMap<>();
        for (ScopeRef scope : scopes) {
            ordered.putIfAbsent(scope.type().name() + ":" + scope.refId(), scope);
        }
        return List.copyOf(ordered.values());
    }

    private AuthorizationScopeType mapScopeType(String orgType) {
        if (orgType == null) {
            return null;
        }
        OrgUnitType orgUnitType = OrgUnitType.valueOf(orgType);
        return switch (orgUnitType) {
            case SCHOOL -> AuthorizationScopeType.SCHOOL;
            case COLLEGE -> AuthorizationScopeType.COLLEGE;
            case COURSE -> AuthorizationScopeType.COURSE;
            case CLASS -> null;
        };
    }
}
