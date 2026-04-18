package com.aubb.server.modules.identityaccess.application.authz;

import com.aubb.server.modules.course.domain.member.CourseMemberRole;
import com.aubb.server.modules.identityaccess.domain.authz.PermissionCode;
import com.aubb.server.modules.identityaccess.domain.governance.GovernanceRole;
import java.util.List;

final class LegacyPermissionGrantMatrix {

    private static final List<PermissionCode> SCHOOL_ADMIN_PERMISSIONS = List.of(
            PermissionCode.ORG_UNIT_READ,
            PermissionCode.ORG_UNIT_MANAGE,
            PermissionCode.USER_READ,
            PermissionCode.USER_MANAGE,
            PermissionCode.USER_IDENTITY_MANAGE,
            PermissionCode.USER_MEMBERSHIP_MANAGE,
            PermissionCode.USER_SESSION_REVOKE,
            PermissionCode.COURSE_READ,
            PermissionCode.COURSE_MANAGE,
            PermissionCode.OFFERING_READ,
            PermissionCode.OFFERING_MANAGE,
            PermissionCode.CLASS_READ,
            PermissionCode.CLASS_MANAGE,
            PermissionCode.MEMBER_READ,
            PermissionCode.MEMBER_MANAGE,
            PermissionCode.AUDIT_READ,
            PermissionCode.AUDIT_SOURCE_READ,
            PermissionCode.AUTH_GROUP_MANAGE,
            PermissionCode.AUTH_EXPLAIN_READ);

    private static final List<PermissionCode> COLLEGE_ADMIN_PERMISSIONS = List.of(
            PermissionCode.ORG_UNIT_READ,
            PermissionCode.USER_READ,
            PermissionCode.USER_MANAGE,
            PermissionCode.USER_MEMBERSHIP_MANAGE,
            PermissionCode.USER_SESSION_REVOKE,
            PermissionCode.COURSE_READ,
            PermissionCode.COURSE_MANAGE,
            PermissionCode.OFFERING_READ,
            PermissionCode.OFFERING_MANAGE,
            PermissionCode.CLASS_READ,
            PermissionCode.CLASS_MANAGE,
            PermissionCode.MEMBER_READ,
            PermissionCode.MEMBER_MANAGE);

    private static final List<PermissionCode> COURSE_ADMIN_PERMISSIONS = List.of(
            PermissionCode.COURSE_READ,
            PermissionCode.COURSE_MANAGE,
            PermissionCode.OFFERING_READ,
            PermissionCode.OFFERING_MANAGE,
            PermissionCode.CLASS_READ,
            PermissionCode.CLASS_MANAGE,
            PermissionCode.MEMBER_READ,
            PermissionCode.MEMBER_MANAGE);

    private static final List<PermissionCode> CLASS_ADMIN_PERMISSIONS = List.of(
            PermissionCode.CLASS_READ,
            PermissionCode.CLASS_MANAGE,
            PermissionCode.USER_READ,
            PermissionCode.USER_MANAGE,
            PermissionCode.USER_MEMBERSHIP_MANAGE,
            PermissionCode.USER_SESSION_REVOKE,
            PermissionCode.MEMBER_READ,
            PermissionCode.MEMBER_MANAGE);

    private static final List<PermissionCode> INSTRUCTOR_PERMISSIONS = List.of(
            PermissionCode.OFFERING_READ,
            PermissionCode.OFFERING_MANAGE,
            PermissionCode.CLASS_READ,
            PermissionCode.CLASS_MANAGE,
            PermissionCode.MEMBER_READ,
            PermissionCode.MEMBER_MANAGE,
            PermissionCode.ASSIGNMENT_READ,
            PermissionCode.ASSIGNMENT_CREATE,
            PermissionCode.ASSIGNMENT_UPDATE,
            PermissionCode.ASSIGNMENT_PUBLISH,
            PermissionCode.ASSIGNMENT_CLOSE,
            PermissionCode.QUESTION_READ,
            PermissionCode.QUESTION_MANAGE,
            PermissionCode.JUDGE_PROFILE_MANAGE,
            PermissionCode.SUBMISSION_READ_OFFERING,
            PermissionCode.SUBMISSION_CODE_READ_SENSITIVE,
            PermissionCode.SUBMISSION_GRADE,
            PermissionCode.SUBMISSION_REJUDGE,
            PermissionCode.GRADE_EXPORT_OFFERING,
            PermissionCode.GRADE_OVERRIDE,
            PermissionCode.GRADE_PUBLISH,
            PermissionCode.APPEAL_READ_CLASS,
            PermissionCode.APPEAL_REVIEW,
            PermissionCode.LAB_READ,
            PermissionCode.LAB_MANAGE,
            PermissionCode.LAB_REPORT_REVIEW);

    private static final List<PermissionCode> CLASS_INSTRUCTOR_PERMISSIONS = List.of(
            PermissionCode.CLASS_READ,
            PermissionCode.MEMBER_READ,
            PermissionCode.ASSIGNMENT_READ,
            PermissionCode.ASSIGNMENT_CREATE,
            PermissionCode.ASSIGNMENT_UPDATE,
            PermissionCode.ASSIGNMENT_PUBLISH,
            PermissionCode.ASSIGNMENT_CLOSE,
            PermissionCode.SUBMISSION_READ_CLASS,
            PermissionCode.SUBMISSION_CODE_READ_SENSITIVE,
            PermissionCode.SUBMISSION_GRADE,
            PermissionCode.GRADE_EXPORT_CLASS,
            PermissionCode.GRADE_OVERRIDE,
            PermissionCode.APPEAL_READ_CLASS,
            PermissionCode.APPEAL_REVIEW,
            PermissionCode.LAB_READ,
            PermissionCode.LAB_MANAGE,
            PermissionCode.LAB_REPORT_REVIEW);

    private static final List<PermissionCode> OFFERING_TA_PERMISSIONS = List.of(
            PermissionCode.OFFERING_READ,
            PermissionCode.CLASS_READ,
            PermissionCode.MEMBER_READ,
            PermissionCode.ASSIGNMENT_READ,
            PermissionCode.SUBMISSION_READ_OFFERING,
            PermissionCode.SUBMISSION_CODE_READ_SENSITIVE,
            PermissionCode.SUBMISSION_GRADE,
            PermissionCode.GRADE_EXPORT_OFFERING,
            PermissionCode.APPEAL_READ_CLASS,
            PermissionCode.LAB_READ,
            PermissionCode.LAB_REPORT_REVIEW);

    private static final List<PermissionCode> CLASS_TA_PERMISSIONS = List.of(
            PermissionCode.CLASS_READ,
            PermissionCode.MEMBER_READ,
            PermissionCode.ASSIGNMENT_READ,
            PermissionCode.SUBMISSION_READ_CLASS,
            PermissionCode.SUBMISSION_CODE_READ_SENSITIVE,
            PermissionCode.SUBMISSION_GRADE,
            PermissionCode.GRADE_EXPORT_CLASS,
            PermissionCode.APPEAL_READ_CLASS,
            PermissionCode.LAB_READ,
            PermissionCode.LAB_REPORT_REVIEW);

    private static final List<PermissionCode> STUDENT_PERMISSIONS = List.of(
            PermissionCode.CLASS_READ,
            PermissionCode.ASSIGNMENT_READ,
            PermissionCode.SUBMISSION_READ_OWN,
            PermissionCode.GRADE_READ_OWN,
            PermissionCode.APPEAL_READ_OWN,
            PermissionCode.LAB_READ);

    private static final List<PermissionCode> OBSERVER_PERMISSIONS =
            List.of(PermissionCode.OFFERING_READ, PermissionCode.CLASS_READ, PermissionCode.ASSIGNMENT_READ);

    private static final List<PermissionCode> CLASS_MEMBER_OFFERING_VISIBILITY_PERMISSIONS =
            List.of(PermissionCode.OFFERING_READ, PermissionCode.ASSIGNMENT_READ);

    private LegacyPermissionGrantMatrix() {}

    static List<PermissionCode> forGovernanceRole(GovernanceRole role) {
        return switch (role) {
            case SCHOOL_ADMIN -> SCHOOL_ADMIN_PERMISSIONS;
            case COLLEGE_ADMIN -> COLLEGE_ADMIN_PERMISSIONS;
            case COURSE_ADMIN -> COURSE_ADMIN_PERMISSIONS;
            case CLASS_ADMIN -> CLASS_ADMIN_PERMISSIONS;
        };
    }

    static List<PermissionCode> forCourseMember(CourseMemberRole role) {
        return switch (role) {
            case INSTRUCTOR -> INSTRUCTOR_PERMISSIONS;
            case CLASS_INSTRUCTOR -> CLASS_INSTRUCTOR_PERMISSIONS;
            case OFFERING_TA -> OFFERING_TA_PERMISSIONS;
            case TA -> CLASS_TA_PERMISSIONS;
            case STUDENT -> STUDENT_PERMISSIONS;
            case OBSERVER -> OBSERVER_PERMISSIONS;
        };
    }

    static List<PermissionCode> offeringVisibilityForClassMember(CourseMemberRole role) {
        return switch (role) {
            case CLASS_INSTRUCTOR, TA, STUDENT, OBSERVER -> CLASS_MEMBER_OFFERING_VISIBILITY_PERMISSIONS;
            case INSTRUCTOR, OFFERING_TA -> List.of();
        };
    }
}
