package com.aubb.server.modules.identityaccess.application.authz.core;

import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DefaultRolePermissionConstraintResolver {

    private static final Set<String> TEACHING_ROLES =
            Set.of("offering_coordinator", "offering_teacher", "class_teacher", "offering_ta", "class_ta", "grader");

    public RoleBindingConstraints resolve(String roleCode, PermissionDefinition permission) {
        RoleBindingConstraints defaults = RoleBindingConstraints.none();
        if (roleCode == null || permission == null) {
            return defaults;
        }
        if ("student".equals(roleCode)) {
            return studentDefaults(permission.code());
        }
        if (TEACHING_ROLES.contains(roleCode)) {
            return new RoleBindingConstraints(
                    false, false, permission.isWriteOperation(), false, isTeachingScopedPermission(permission.code()));
        }
        return defaults;
    }

    private RoleBindingConstraints studentDefaults(String permissionCode) {
        return switch (permissionCode) {
            case "task.read", "announcement.read", "resource.read", "discussion.participate", "lab.read" ->
                new RoleBindingConstraints(false, true, false, false, true);
            case "submission.read", "appeal.read" -> new RoleBindingConstraints(true, false, false, false, true);
            case "grade.read" -> new RoleBindingConstraints(true, true, false, false, true);
            case "ide.read", "ide.save", "ide.run", "ide.submit" ->
                new RoleBindingConstraints(false, false, false, true, true);
            default -> RoleBindingConstraints.none();
        };
    }

    private boolean isTeachingScopedPermission(String permissionCode) {
        return permissionCode.startsWith("task.")
                || permissionCode.startsWith("submission.")
                || permissionCode.startsWith("grade.")
                || permissionCode.startsWith("appeal.")
                || permissionCode.startsWith("announcement.")
                || permissionCode.startsWith("resource.")
                || permissionCode.startsWith("discussion.")
                || permissionCode.startsWith("lab.")
                || permissionCode.startsWith("ide.");
    }
}
