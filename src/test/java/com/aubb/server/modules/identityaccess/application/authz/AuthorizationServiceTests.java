package com.aubb.server.modules.identityaccess.application.authz;

import static org.assertj.core.api.Assertions.assertThat;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.domain.account.AccountStatus;
import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;
import com.aubb.server.modules.identityaccess.domain.authz.PermissionCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class AuthorizationServiceTests {

    private final AuthenticatedUserPrincipal principal =
            new AuthenticatedUserPrincipal(1L, "teacher", "Teacher", 10L, AccountStatus.ACTIVE, null, List.of());

    @Test
    void shouldAllowWhenPermissionMatchesAncestorScope() {
        ScopeRef classScope = new ScopeRef(
                AuthorizationScopeType.CLASS,
                101L,
                List.of(
                        new ScopeRef(AuthorizationScopeType.OFFERING, 12L),
                        new ScopeRef(AuthorizationScopeType.COURSE, 5L)));
        PermissionGrantView grant = PermissionGrantView.allow(
                PermissionCode.SUBMISSION_GRADE,
                new ScopeRef(AuthorizationScopeType.OFFERING, 12L),
                "AUTHZ_GROUP",
                "offering-ta");
        AuthorizationService authorizationService = new AuthorizationService(List.of(_principal -> List.of(grant)));

        AuthorizationDecision decision = authorizationService.decide(
                AuthorizationRequest.forPermission(principal, PermissionCode.SUBMISSION_GRADE, classScope));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reasonCode()).isNull();
        assertThat(decision.grants()).containsExactly(grant);
    }

    @Test
    void shouldDenyWhenPermissionDoesNotMatch() {
        ScopeRef classScope = new ScopeRef(AuthorizationScopeType.CLASS, 101L);
        PermissionGrantView grant = PermissionGrantView.allow(
                PermissionCode.ASSIGNMENT_PUBLISH,
                new ScopeRef(AuthorizationScopeType.CLASS, 101L),
                "AUTHZ_GROUP",
                "class-instructor");
        AuthorizationService authorizationService = new AuthorizationService(List.of(_principal -> List.of(grant)));

        AuthorizationDecision decision = authorizationService.decide(
                AuthorizationRequest.forPermission(principal, PermissionCode.SUBMISSION_GRADE, classScope));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("NO_PERMISSION");
        assertThat(decision.grants()).isEmpty();
    }

    @Test
    void shouldDenyWhenScopeIsOutsideGrant() {
        ScopeRef classScope = new ScopeRef(
                AuthorizationScopeType.CLASS,
                102L,
                List.of(
                        new ScopeRef(AuthorizationScopeType.OFFERING, 13L),
                        new ScopeRef(AuthorizationScopeType.COURSE, 5L)));
        PermissionGrantView grant = PermissionGrantView.allow(
                PermissionCode.SUBMISSION_GRADE,
                new ScopeRef(AuthorizationScopeType.OFFERING, 12L),
                "AUTHZ_GROUP",
                "offering-ta");
        AuthorizationService authorizationService = new AuthorizationService(List.of(_principal -> List.of(grant)));

        AuthorizationDecision decision = authorizationService.decide(
                AuthorizationRequest.forPermission(principal, PermissionCode.SUBMISSION_GRADE, classScope));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("NO_PERMISSION");
    }
}
