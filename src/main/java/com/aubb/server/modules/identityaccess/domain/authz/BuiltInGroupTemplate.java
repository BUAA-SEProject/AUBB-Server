package com.aubb.server.modules.identityaccess.domain.authz;

import java.util.Arrays;

public enum BuiltInGroupTemplate {
    SCHOOL_ADMIN("school-admin", "学校管理员", AuthorizationScopeType.SCHOOL, true),
    COLLEGE_ADMIN("college-admin", "学院管理员", AuthorizationScopeType.COLLEGE, true),
    COURSE_ADMIN("course-admin", "课程管理员", AuthorizationScopeType.COURSE, true),
    CLASS_ADMIN("class-admin", "班级管理员", AuthorizationScopeType.CLASS, true),
    OFFERING_INSTRUCTOR("offering-instructor", "开课教师", AuthorizationScopeType.OFFERING, true),
    CLASS_INSTRUCTOR("class-instructor", "班级教师", AuthorizationScopeType.CLASS, true),
    OFFERING_TA("offering-ta", "开课助教", AuthorizationScopeType.OFFERING, true),
    CLASS_TA("class-ta", "班级助教", AuthorizationScopeType.CLASS, true),
    STUDENT("student", "学生", AuthorizationScopeType.CLASS, true),
    OBSERVER("observer", "观察者", AuthorizationScopeType.OFFERING, true),
    AUDIT_READONLY("audit-readonly", "审计只读组", AuthorizationScopeType.SCHOOL, false),
    GRADE_CORRECTOR("grade-corrector", "成绩纠错组", AuthorizationScopeType.OFFERING, false);

    private final String code;
    private final String displayName;
    private final AuthorizationScopeType scopeType;
    private final boolean systemManaged;

    BuiltInGroupTemplate(String code, String displayName, AuthorizationScopeType scopeType, boolean systemManaged) {
        this.code = code;
        this.displayName = displayName;
        this.scopeType = scopeType;
        this.systemManaged = systemManaged;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public AuthorizationScopeType scopeType() {
        return scopeType;
    }

    public boolean systemManaged() {
        return systemManaged;
    }

    public static BuiltInGroupTemplate fromCode(String code) {
        return Arrays.stream(values())
                .filter(template -> template.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported group template: " + code));
    }
}
