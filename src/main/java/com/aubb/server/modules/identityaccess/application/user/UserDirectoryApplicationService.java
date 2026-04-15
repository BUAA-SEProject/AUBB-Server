package com.aubb.server.modules.identityaccess.application.user;

import com.aubb.server.common.api.PageResponse;
import com.aubb.server.modules.identityaccess.domain.AcademicIdentityType;
import com.aubb.server.modules.identityaccess.domain.AcademicProfileStatus;
import com.aubb.server.modules.identityaccess.infrastructure.AcademicProfileEntity;
import com.aubb.server.modules.identityaccess.infrastructure.AcademicProfileMapper;
import com.aubb.server.modules.identityaccess.infrastructure.UserEntity;
import com.aubb.server.modules.identityaccess.infrastructure.UserMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserDirectoryApplicationService {

    private final UserMapper userMapper;
    private final AcademicProfileMapper academicProfileMapper;

    @Transactional(readOnly = true)
    public Map<Long, UserDirectoryEntryView> loadByIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UserEntity> users = userMapper.selectByIds(userIds);
        Map<Long, AcademicProfileView> profiles =
                loadProfiles(users.stream().map(UserEntity::getId).toList());
        return users.stream()
                .collect(Collectors.toMap(
                        UserEntity::getId,
                        user -> toView(user, profiles.get(user.getId())),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    @Transactional(readOnly = true)
    public Map<String, UserDirectoryEntryView> loadByUsernames(Collection<String> usernames) {
        if (usernames == null || usernames.isEmpty()) {
            return Map.of();
        }
        List<String> normalizedUsernames = usernames.stream()
                .filter(Objects::nonNull)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        if (normalizedUsernames.isEmpty()) {
            return Map.of();
        }
        List<UserEntity> users = userMapper.selectList(
                Wrappers.<UserEntity>lambdaQuery().in(UserEntity::getUsername, normalizedUsernames));
        Map<Long, AcademicProfileView> profiles =
                loadProfiles(users.stream().map(UserEntity::getId).toList());
        return users.stream()
                .collect(Collectors.toMap(
                        user -> user.getUsername().toLowerCase(Locale.ROOT),
                        user -> toView(user, profiles.get(user.getId())),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDirectoryEntryView> searchCandidates(
            String keyword, Collection<Long> excludeUserIds, long page, long pageSize) {
        String normalizedKeyword = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        List<Long> excluded = excludeUserIds == null
                ? List.of()
                : excludeUserIds.stream().filter(Objects::nonNull).toList();
        List<UserEntity> users =
                userMapper.selectList(Wrappers.<UserEntity>lambdaQuery().orderByAsc(UserEntity::getUsername));
        Map<Long, AcademicProfileView> profiles =
                loadProfiles(users.stream().map(UserEntity::getId).toList());
        List<UserDirectoryEntryView> matched = users.stream()
                .filter(user -> !excluded.contains(user.getId()))
                .map(user -> toView(user, profiles.get(user.getId())))
                .filter(entry -> matchesKeyword(entry, normalizedKeyword))
                .toList();
        long safePage = Math.max(page, 1);
        long safePageSize = Math.max(pageSize, 1);
        long offset = (safePage - 1) * safePageSize;
        return new PageResponse<>(
                matched.stream().skip(offset).limit(safePageSize).toList(), matched.size(), safePage, safePageSize);
    }

    private boolean matchesKeyword(UserDirectoryEntryView entry, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        return containsIgnoreCase(entry.username(), keyword)
                || containsIgnoreCase(entry.displayName(), keyword)
                || (entry.academicProfile() != null
                        && containsIgnoreCase(entry.academicProfile().academicId(), keyword))
                || (entry.academicProfile() != null
                        && containsIgnoreCase(entry.academicProfile().realName(), keyword));
    }

    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    private Map<Long, AcademicProfileView> loadProfiles(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return academicProfileMapper
                .selectList(Wrappers.<AcademicProfileEntity>lambdaQuery().in(AcademicProfileEntity::getUserId, userIds))
                .stream()
                .collect(Collectors.toMap(
                        AcademicProfileEntity::getUserId,
                        profile -> new AcademicProfileView(
                                profile.getId(),
                                profile.getUserId(),
                                profile.getAcademicId(),
                                profile.getRealName(),
                                AcademicIdentityType.valueOf(profile.getIdentityType()),
                                AcademicProfileStatus.valueOf(profile.getProfileStatus()),
                                profile.getPhone()),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private UserDirectoryEntryView toView(UserEntity user, AcademicProfileView profile) {
        return new UserDirectoryEntryView(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getEmail(), user.getPhone(), profile);
    }
}
