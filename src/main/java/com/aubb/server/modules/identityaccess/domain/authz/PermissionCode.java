package com.aubb.server.modules.identityaccess.domain.authz;

import java.util.Arrays;

public enum PermissionCode {
    ORG_UNIT_READ("org.unit.read"),
    ORG_UNIT_MANAGE("org.unit.manage"),
    USER_READ("user.read"),
    USER_MANAGE("user.manage"),
    USER_IDENTITY_MANAGE("user.identity.manage"),
    USER_MEMBERSHIP_MANAGE("user.membership.manage"),
    USER_SESSION_REVOKE("user.session.revoke"),
    COURSE_READ("course.read"),
    COURSE_MANAGE("course.manage"),
    OFFERING_READ("offering.read"),
    OFFERING_MANAGE("offering.manage"),
    CLASS_READ("class.read"),
    CLASS_MANAGE("class.manage"),
    MEMBER_READ("member.read"),
    MEMBER_MANAGE("member.manage"),
    ASSIGNMENT_READ("assignment.read"),
    ASSIGNMENT_CREATE("assignment.create"),
    ASSIGNMENT_UPDATE("assignment.update"),
    ASSIGNMENT_PUBLISH("assignment.publish"),
    ASSIGNMENT_CLOSE("assignment.close"),
    QUESTION_READ("question.read"),
    QUESTION_MANAGE("question.manage"),
    JUDGE_PROFILE_MANAGE("judge.profile.manage"),
    JUDGE_HIDDEN_READ("judge.hidden.read"),
    JUDGE_HIDDEN_MANAGE("judge.hidden.manage"),
    SUBMISSION_READ_OWN("submission.read.own"),
    SUBMISSION_READ_CLASS("submission.read.class"),
    SUBMISSION_READ_OFFERING("submission.read.offering"),
    SUBMISSION_CODE_READ_SENSITIVE("submission.code.read_sensitive"),
    SUBMISSION_GRADE("submission.grade"),
    SUBMISSION_REJUDGE("submission.rejudge"),
    GRADE_READ_OWN("grade.read.own"),
    GRADE_READ_UNPUBLISHED("grade.read.unpublished"),
    GRADE_EXPORT_CLASS("grade.export.class"),
    GRADE_EXPORT_OFFERING("grade.export.offering"),
    GRADE_OVERRIDE("grade.override"),
    GRADE_PUBLISH("grade.publish"),
    APPEAL_READ_OWN("appeal.read.own"),
    APPEAL_READ_CLASS("appeal.read.class"),
    APPEAL_REVIEW("appeal.review"),
    LAB_READ("lab.read"),
    LAB_MANAGE("lab.manage"),
    LAB_REPORT_REVIEW("lab.report.review"),
    AUDIT_READ("audit.read"),
    AUDIT_SOURCE_READ("audit.source.read"),
    AUTH_GROUP_MANAGE("auth.group.manage"),
    AUTH_EXPLAIN_READ("auth.explain.read");

    private final String code;

    PermissionCode(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static PermissionCode fromCode(String code) {
        return Arrays.stream(values())
                .filter(permissionCode -> permissionCode.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported permission code: " + code));
    }
}
