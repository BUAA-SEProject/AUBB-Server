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
    QUESTION_BANK_MANAGE("question_bank.manage"),
    /**
     * @deprecated 旧授权组数据里仍可能持久化为 {@code question.manage}，仅保留解析兼容，不再用于新逻辑。
     */
    @Deprecated(forRemoval = false)
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

    public boolean isWriteAction() {
        return switch (this) {
            case ORG_UNIT_MANAGE,
                    USER_MANAGE,
                    USER_IDENTITY_MANAGE,
                    USER_MEMBERSHIP_MANAGE,
                    USER_SESSION_REVOKE,
                    COURSE_MANAGE,
                    OFFERING_MANAGE,
                    CLASS_MANAGE,
                    MEMBER_MANAGE,
                    ASSIGNMENT_CREATE,
                    ASSIGNMENT_UPDATE,
                    ASSIGNMENT_PUBLISH,
                    ASSIGNMENT_CLOSE,
                    QUESTION_BANK_MANAGE,
                    QUESTION_MANAGE,
                    JUDGE_PROFILE_MANAGE,
                    JUDGE_HIDDEN_MANAGE,
                    SUBMISSION_GRADE,
                    SUBMISSION_REJUDGE,
                    GRADE_OVERRIDE,
                    GRADE_PUBLISH,
                    APPEAL_REVIEW,
                    LAB_MANAGE,
                    LAB_REPORT_REVIEW,
                    AUTH_GROUP_MANAGE -> true;
            default -> false;
        };
    }

    public static PermissionCode fromCode(String code) {
        if ("question.manage".equalsIgnoreCase(code) || "question_bank.manage".equalsIgnoreCase(code)) {
            return QUESTION_BANK_MANAGE;
        }
        return Arrays.stream(values())
                .filter(permissionCode -> permissionCode.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported permission code: " + code));
    }
}
