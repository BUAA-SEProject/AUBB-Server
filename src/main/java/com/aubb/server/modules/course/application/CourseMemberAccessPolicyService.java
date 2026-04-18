package com.aubb.server.modules.course.application;

import com.aubb.server.modules.course.domain.member.CourseMemberRole;
import com.aubb.server.modules.course.domain.member.CourseMemberStatus;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberEntity;
import com.aubb.server.modules.course.infrastructure.member.CourseMemberMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseMemberAccessPolicyService {

    private static final Set<String> ACTIVE_STATUSES = Set.of(CourseMemberStatus.ACTIVE.name());
    private static final Set<String> HISTORY_READABLE_STUDENT_STATUSES = Set.of(
            CourseMemberStatus.DROPPED.name(),
            CourseMemberStatus.TRANSFERRED.name(),
            CourseMemberStatus.COMPLETED.name());

    private final CourseMemberMapper courseMemberMapper;

    @Transactional(readOnly = true)
    public boolean hasActiveStudentMembership(Long userId, Long offeringId, Long teachingClassId) {
        return hasMemberRoleWithStatuses(
                userId, offeringId, teachingClassId, CourseMemberRole.STUDENT, ACTIVE_STATUSES);
    }

    @Transactional(readOnly = true)
    public boolean hasHistoricalReadableStudentMembership(Long userId, Long offeringId, Long teachingClassId) {
        return hasMemberRoleWithStatuses(
                userId,
                offeringId,
                teachingClassId,
                CourseMemberRole.STUDENT,
                HISTORY_READABLE_STUDENT_STATUSES);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveMemberRole(Long userId, Long offeringId, Long teachingClassId, CourseMemberRole role) {
        return hasMemberRoleWithStatuses(userId, offeringId, teachingClassId, role, ACTIVE_STATUSES);
    }

    private boolean hasMemberRoleWithStatuses(
            Long userId, Long offeringId, Long teachingClassId, CourseMemberRole role, Set<String> statuses) {
        if (userId == null || offeringId == null || role == null || statuses == null || statuses.isEmpty()) {
            return false;
        }
        return courseMemberMapper.selectOne(Wrappers.<CourseMemberEntity>lambdaQuery()
                        .eq(CourseMemberEntity::getUserId, userId)
                        .eq(CourseMemberEntity::getOfferingId, offeringId)
                        .eq(CourseMemberEntity::getMemberRole, role.name())
                        .eq(teachingClassId != null, CourseMemberEntity::getTeachingClassId, teachingClassId)
                        .in(CourseMemberEntity::getMemberStatus, statuses)
                        .last("LIMIT 1"))
                != null;
    }
}
