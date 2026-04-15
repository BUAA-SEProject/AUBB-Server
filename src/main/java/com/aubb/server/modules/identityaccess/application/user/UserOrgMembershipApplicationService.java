package com.aubb.server.modules.identityaccess.application.user;

import com.aubb.server.modules.identityaccess.domain.MembershipSourceType;
import com.aubb.server.modules.identityaccess.domain.MembershipStatus;
import com.aubb.server.modules.identityaccess.domain.MembershipType;
import com.aubb.server.modules.identityaccess.infrastructure.UserOrgMembershipEntity;
import com.aubb.server.modules.identityaccess.infrastructure.UserOrgMembershipMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserOrgMembershipApplicationService {

    private final UserOrgMembershipMapper userOrgMembershipMapper;

    @Transactional
    public void upsertMembership(
            Long userId,
            Long orgUnitId,
            MembershipType membershipType,
            MembershipStatus membershipStatus,
            MembershipSourceType sourceType,
            OffsetDateTime startAt,
            OffsetDateTime endAt) {
        UserOrgMembershipEntity existing =
                userOrgMembershipMapper.selectOne(Wrappers.<UserOrgMembershipEntity>lambdaQuery()
                        .eq(UserOrgMembershipEntity::getUserId, userId)
                        .eq(UserOrgMembershipEntity::getOrgUnitId, orgUnitId)
                        .eq(UserOrgMembershipEntity::getMembershipType, membershipType.name())
                        .last("LIMIT 1"));
        UserOrgMembershipEntity entity = existing == null ? new UserOrgMembershipEntity() : existing;
        entity.setUserId(userId);
        entity.setOrgUnitId(orgUnitId);
        entity.setMembershipType(membershipType.name());
        entity.setMembershipStatus(membershipStatus.name());
        entity.setSourceType(sourceType.name());
        entity.setStartAt(startAt);
        entity.setEndAt(endAt);
        if (existing == null) {
            userOrgMembershipMapper.insert(entity);
        } else {
            userOrgMembershipMapper.updateById(entity);
        }
    }

    @Transactional
    public void removeMembership(Long userId, Long orgUnitId, MembershipType membershipType) {
        userOrgMembershipMapper.delete(Wrappers.<UserOrgMembershipEntity>lambdaQuery()
                .eq(UserOrgMembershipEntity::getUserId, userId)
                .eq(UserOrgMembershipEntity::getOrgUnitId, orgUnitId)
                .eq(UserOrgMembershipEntity::getMembershipType, membershipType.name()));
    }
}
