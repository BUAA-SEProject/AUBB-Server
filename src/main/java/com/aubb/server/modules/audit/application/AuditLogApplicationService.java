package com.aubb.server.modules.audit.application;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.common.web.RequestContextSupport;
import com.aubb.server.modules.audit.domain.AuditAction;
import com.aubb.server.modules.audit.domain.AuditDecision;
import com.aubb.server.modules.audit.domain.AuditResult;
import com.aubb.server.modules.audit.infrastructure.AuditLogEntity;
import com.aubb.server.modules.audit.infrastructure.AuditLogMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogApplicationService {

    private final AuditLogMapper auditLogMapper;
    private final RequestContextSupport requestContextSupport;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(
            Long actorUserId,
            AuditAction action,
            String targetType,
            String targetId,
            AuditResult result,
            Map<String, Object> metadata) {
        record(new AuditLogCommand(actorUserId, action, targetType, targetId, result, null, null, null, metadata));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditLogCommand command) {
        AuditLogEntity entity = new AuditLogEntity();
        entity.setActorUserId(command.actorUserId());
        entity.setAction(command.action().name());
        entity.setTargetType(command.targetType());
        entity.setTargetId(command.targetId());
        entity.setResult(command.result().name());
        entity.setRequestId(requestContextSupport.requestId());
        entity.setIp(requestContextSupport.clientIp());
        entity.setScopeType(command.scopeType());
        entity.setScopeId(command.scopeId());
        entity.setDecision(
                command.decision() == null ? null : command.decision().name());
        entity.setUserAgent(requestContextSupport.userAgent());
        entity.setMetadata(command.metadata());
        auditLogMapper.insert(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDecision(
            Long actorUserId,
            AuditAction action,
            String targetType,
            String targetId,
            String scopeType,
            Long scopeId,
            AuditDecision decision,
            Map<String, Object> metadata) {
        record(new AuditLogCommand(
                actorUserId,
                action,
                targetType,
                targetId,
                decision == AuditDecision.ALLOW ? AuditResult.SUCCESS : AuditResult.FAILURE,
                scopeType,
                scopeId,
                decision,
                metadata));
    }

    public PageResponse<AuditLogView> search(
            Long actorUserId,
            String action,
            String targetType,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            long page,
            long pageSize) {
        var countQuery = buildQuery(actorUserId, action, targetType, startAt, endAt);
        long total = auditLogMapper.selectCount(countQuery);
        long safePage = Math.max(page, 1);
        long safePageSize = Math.max(pageSize, 1);
        long offset = (safePage - 1) * safePageSize;
        var pageQuery =
                buildQuery(actorUserId, action, targetType, startAt, endAt).orderByDesc(AuditLogEntity::getCreatedAt);
        pageQuery.last("LIMIT " + safePageSize + " OFFSET " + offset);

        List<AuditLogView> items =
                auditLogMapper.selectList(pageQuery).stream().map(this::toView).toList();
        return new PageResponse<>(items, total, safePage, safePageSize);
    }

    private com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AuditLogEntity> buildQuery(
            Long actorUserId, String action, String targetType, OffsetDateTime startAt, OffsetDateTime endAt) {
        return Wrappers.<AuditLogEntity>lambdaQuery()
                .eq(actorUserId != null, AuditLogEntity::getActorUserId, actorUserId)
                .eq(action != null && !action.isBlank(), AuditLogEntity::getAction, action)
                .eq(targetType != null && !targetType.isBlank(), AuditLogEntity::getTargetType, targetType)
                .ge(startAt != null, AuditLogEntity::getCreatedAt, startAt)
                .le(endAt != null, AuditLogEntity::getCreatedAt, endAt);
    }

    private AuditLogView toView(AuditLogEntity entity) {
        return new AuditLogView(
                entity.getId(),
                entity.getActorUserId(),
                entity.getAction(),
                entity.getTargetType(),
                entity.getTargetId(),
                entity.getResult(),
                entity.getRequestId(),
                entity.getIp(),
                entity.getScopeType(),
                entity.getScopeId(),
                entity.getDecision(),
                entity.getUserAgent(),
                entity.getMetadata(),
                entity.getCreatedAt());
    }
}
