package com.aubb.server.modules.identityaccess.application.authz.core;

import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public record AuthorizationScopePath(Long schoolId, Long collegeId, Long courseId, Long offeringId, Long classId) {

    public static AuthorizationScopePath platform() {
        return new AuthorizationScopePath(null, null, null, null, null);
    }

    public static AuthorizationScopePath forSchool(Long schoolId) {
        return new AuthorizationScopePath(requireId("schoolId", schoolId), null, null, null, null);
    }

    public static AuthorizationScopePath forCollege(Long schoolId, Long collegeId) {
        return new AuthorizationScopePath(
                requireId("schoolId", schoolId), requireId("collegeId", collegeId), null, null, null);
    }

    public static AuthorizationScopePath forCourse(Long schoolId, Long collegeId, Long courseId) {
        return new AuthorizationScopePath(
                requireId("schoolId", schoolId),
                requireId("collegeId", collegeId),
                requireId("courseId", courseId),
                null,
                null);
    }

    public static AuthorizationScopePath forOffering(Long schoolId, Long collegeId, Long courseId, Long offeringId) {
        return new AuthorizationScopePath(
                requireId("schoolId", schoolId),
                requireId("collegeId", collegeId),
                requireId("courseId", courseId),
                requireId("offeringId", offeringId),
                null);
    }

    public static AuthorizationScopePath forClass(
            Long schoolId, Long collegeId, Long courseId, Long offeringId, Long classId) {
        return new AuthorizationScopePath(
                requireId("schoolId", schoolId),
                requireId("collegeId", collegeId),
                requireId("courseId", courseId),
                requireId("offeringId", offeringId),
                requireId("classId", classId));
    }

    public boolean isCoveredBy(AuthorizationScope scope) {
        if (scope == null) {
            return false;
        }
        return switch (scope.type()) {
            case PLATFORM -> true;
            case SCHOOL -> Objects.equals(schoolId, scope.id());
            case COLLEGE -> Objects.equals(collegeId, scope.id());
            case COURSE -> Objects.equals(courseId, scope.id());
            case OFFERING -> Objects.equals(offeringId, scope.id());
            case CLASS -> Objects.equals(classId, scope.id());
        };
    }

    public boolean hasTeachingScope() {
        return offeringId != null || classId != null;
    }

    public AuthorizationScope leafScope() {
        if (classId != null) {
            return AuthorizationScope.of(AuthorizationScopeType.CLASS, classId);
        }
        if (offeringId != null) {
            return AuthorizationScope.of(AuthorizationScopeType.OFFERING, offeringId);
        }
        if (courseId != null) {
            return AuthorizationScope.of(AuthorizationScopeType.COURSE, courseId);
        }
        if (collegeId != null) {
            return AuthorizationScope.of(AuthorizationScopeType.COLLEGE, collegeId);
        }
        if (schoolId != null) {
            return AuthorizationScope.of(AuthorizationScopeType.SCHOOL, schoolId);
        }
        return AuthorizationScope.platform();
    }

    public List<AuthorizationScope> scopes() {
        List<AuthorizationScope> scopes = new ArrayList<>();
        scopes.add(AuthorizationScope.platform());
        if (schoolId != null) {
            scopes.add(AuthorizationScope.of(AuthorizationScopeType.SCHOOL, schoolId));
        }
        if (collegeId != null) {
            scopes.add(AuthorizationScope.of(AuthorizationScopeType.COLLEGE, collegeId));
        }
        if (courseId != null) {
            scopes.add(AuthorizationScope.of(AuthorizationScopeType.COURSE, courseId));
        }
        if (offeringId != null) {
            scopes.add(AuthorizationScope.of(AuthorizationScopeType.OFFERING, offeringId));
        }
        if (classId != null) {
            scopes.add(AuthorizationScope.of(AuthorizationScopeType.CLASS, classId));
        }
        return List.copyOf(scopes);
    }

    private static Long requireId(String label, Long value) {
        if (value == null) {
            throw new IllegalArgumentException(label + " must not be null");
        }
        return value;
    }
}
