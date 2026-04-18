package com.aubb.server.modules.identityaccess.application.authz;

import static org.assertj.core.api.Assertions.assertThat;

import com.aubb.server.modules.identityaccess.application.auth.AuthenticatedUserPrincipal;
import com.aubb.server.modules.identityaccess.application.authz.guard.AccountStatusGuard;
import com.aubb.server.modules.identityaccess.application.authz.guard.ArchivedWriteGuard;
import com.aubb.server.modules.identityaccess.application.authz.guard.PolicyGuardRegistry;
import com.aubb.server.modules.identityaccess.application.authz.guard.SelfActionGuard;
import com.aubb.server.modules.identityaccess.domain.account.AccountStatus;
import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;
import com.aubb.server.modules.identityaccess.domain.authz.PermissionCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyGuardTests {

    private static final ScopeRef OFFERING_SCOPE = new ScopeRef(AuthorizationScopeType.OFFERING, 12L);

    @Test
    void shouldDenyWhenTeacherTriesToGradeOwnSubmission() {
        AuthorizationService authorizationService = authorizationServiceFor(PermissionGrantView.allow(
                PermissionCode.SUBMISSION_GRADE, OFFERING_SCOPE, "AUTHZ_GROUP", "offering-ta"));

        AuthorizationDecision decision = authorizationService.decide(
                AuthorizationRequest.forPermission(activePrincipal(), PermissionCode.SUBMISSION_GRADE, OFFERING_SCOPE)
                        .withResourceOwnerUserId(1L));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("SELF_ACTION_FORBIDDEN");
    }

    @Test
    void shouldDenyWhenAccountIsInactive() {
        AuthorizationService authorizationService = authorizationServiceFor(
                PermissionGrantView.allow(PermissionCode.ASSIGNMENT_PUBLISH, OFFERING_SCOPE, "AUTHZ_GROUP", "teacher"));

        AuthorizationDecision decision = authorizationService.decide(AuthorizationRequest.forPermission(
                inactivePrincipal(), PermissionCode.ASSIGNMENT_PUBLISH, OFFERING_SCOPE));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("ACCOUNT_INACTIVE");
    }

    @Test
    void shouldDenyWritePermissionWhenResourceArchived() {
        AuthorizationService authorizationService = authorizationServiceFor(
                PermissionGrantView.allow(PermissionCode.ASSIGNMENT_CLOSE, OFFERING_SCOPE, "AUTHZ_GROUP", "teacher"));

        AuthorizationDecision decision = authorizationService.decide(
                AuthorizationRequest.forPermission(activePrincipal(), PermissionCode.ASSIGNMENT_CLOSE, OFFERING_SCOPE)
                        .withArchivedResource(true));

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reasonCode()).isEqualTo("ARCHIVED_RESOURCE_READ_ONLY");
    }

    @Test
    void shouldAllowReadPermissionOnArchivedResource() {
        AuthorizationService authorizationService = authorizationServiceFor(
                PermissionGrantView.allow(PermissionCode.ASSIGNMENT_READ, OFFERING_SCOPE, "AUTHZ_GROUP", "teacher"));

        AuthorizationDecision decision = authorizationService.decide(
                AuthorizationRequest.forPermission(activePrincipal(), PermissionCode.ASSIGNMENT_READ, OFFERING_SCOPE)
                        .withArchivedResource(true));

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reasonCode()).isNull();
    }

    private AuthorizationService authorizationServiceFor(PermissionGrantView grant) {
        return new AuthorizationService(
                List.of(_principal -> List.of(grant)),
                new PolicyGuardRegistry(
                        List.of(new AccountStatusGuard(), new ArchivedWriteGuard(), new SelfActionGuard())));
    }

    private AuthenticatedUserPrincipal activePrincipal() {
        return principalWithStatus(AccountStatus.ACTIVE);
    }

    private AuthenticatedUserPrincipal inactivePrincipal() {
        return principalWithStatus(AccountStatus.DISABLED);
    }

    private AuthenticatedUserPrincipal principalWithStatus(AccountStatus status) {
        return new AuthenticatedUserPrincipal(1L, "teacher", "Teacher", 10L, status, null, List.of());
    }
}
