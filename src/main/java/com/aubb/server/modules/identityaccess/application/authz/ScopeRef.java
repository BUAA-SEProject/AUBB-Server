package com.aubb.server.modules.identityaccess.application.authz;

import com.aubb.server.modules.identityaccess.domain.authz.AuthorizationScopeType;
import java.util.List;
import java.util.Objects;

public record ScopeRef(AuthorizationScopeType type, Long refId, List<ScopeRef> ancestors) {

    public ScopeRef {
        ancestors = ancestors == null ? List.of() : List.copyOf(ancestors);
    }

    public ScopeRef(AuthorizationScopeType type, Long refId) {
        this(type, refId, List.of());
    }

    public boolean isCoveredBy(ScopeRef candidate) {
        if (candidate == null) {
            return false;
        }
        if (type == candidate.type() && Objects.equals(refId, candidate.refId())) {
            return true;
        }
        return ancestors.stream()
                .anyMatch(ancestor ->
                        ancestor.type() == candidate.type() && Objects.equals(ancestor.refId(), candidate.refId()));
    }
}
