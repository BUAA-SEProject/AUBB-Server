package com.aubb.server.modules.identityaccess.application.authz.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;
import org.junit.jupiter.api.Test;

class AuthorizationScopePathTests {

    @Test
    void platformScopeShouldCoverAllDescendantScopes() {
        AuthorizationScopePath path = AuthorizationScopePath.forClass(1L, 2L, 3L, 4L, 5L);

        assertThat(path.isCoveredBy(AuthorizationScope.platform())).isTrue();
        assertThat(path.isCoveredBy(AuthorizationScope.of(AuthorizationScopeType.SCHOOL, 1L)))
                .isTrue();
        assertThat(path.isCoveredBy(AuthorizationScope.of(AuthorizationScopeType.COLLEGE, 2L)))
                .isTrue();
        assertThat(path.isCoveredBy(AuthorizationScope.of(AuthorizationScopeType.COURSE, 3L)))
                .isTrue();
        assertThat(path.isCoveredBy(AuthorizationScope.of(AuthorizationScopeType.OFFERING, 4L)))
                .isTrue();
        assertThat(path.isCoveredBy(AuthorizationScope.of(AuthorizationScopeType.CLASS, 5L)))
                .isTrue();
    }

    @Test
    void classScopeShouldNotElevateToOfferingAncestor() {
        AuthorizationScopePath path = AuthorizationScopePath.forOffering(1L, 2L, 3L, 4L);

        assertThat(path.isCoveredBy(AuthorizationScope.of(AuthorizationScopeType.CLASS, 5L)))
                .isFalse();
    }

    @Test
    void differentOrganizationBranchesShouldNotMatch() {
        AuthorizationScopePath path = AuthorizationScopePath.forClass(1L, 2L, 3L, 4L, 5L);

        assertThat(path.isCoveredBy(AuthorizationScope.of(AuthorizationScopeType.SCHOOL, 99L)))
                .isFalse();
        assertThat(path.isCoveredBy(AuthorizationScope.of(AuthorizationScopeType.COLLEGE, 98L)))
                .isFalse();
        assertThat(path.isCoveredBy(AuthorizationScope.of(AuthorizationScopeType.COURSE, 97L)))
                .isFalse();
    }
}
