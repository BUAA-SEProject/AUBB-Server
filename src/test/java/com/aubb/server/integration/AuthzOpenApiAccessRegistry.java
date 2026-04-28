package com.aubb.server.integration;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

final class AuthzOpenApiAccessRegistry {

    private static final List<Rule> RULES = List.of(
            rule("POST", "^/api/v1/auth/login$", "PUBLIC"),
            rule("POST", "^/api/v1/auth/(refresh|revoke)$", "PUBLIC"),
            rule("POST", "^/api/v1/auth/logout$", "RULE.AUTHENTICATED_SESSION"),
            rule("GET", "^/api/v1/auth/me$", "RULE.AUTHENTICATED_SESSION"),
            rule("GET", "^/api/v1/admin/auth/explain$", "auth.explain.read"),
            rule("POST", "^/api/v1/admin/auth/groups$", "auth.group.manage"),
            rule("POST", "^/api/v1/admin/auth/groups/\\{[^/]+}/members$", "auth.group.manage"),
            rule("GET", "^/api/v1/admin/platform-config/current$", "RULE.PLATFORM_CONFIG_READ"),
            rule("PUT", "^/api/v1/admin/platform-config/current$", "RULE.PLATFORM_CONFIG_WRITE"),
            rule("GET", "^/api/v1/admin/org-units/tree$", "org.unit.read"),
            rule("POST", "^/api/v1/admin/org-units$", "org.unit.manage"),
            rule("GET", "^/api/v1/admin/audit-logs$", "audit.read"),
            rule("GET", "^/api/v1/admin/users$", "user.read"),
            rule("GET", "^/api/v1/admin/users/\\{[^/]+}$", "user.read"),
            rule("POST", "^/api/v1/admin/users$", "user.manage"),
            rule("POST", "^/api/v1/admin/users/import$", "user.manage"),
            rule("PUT", "^/api/v1/admin/users/\\{[^/]+}/identities$", "user.identity.manage"),
            rule("PATCH", "^/api/v1/admin/users/\\{[^/]+}/status$", "user.manage"),
            rule("POST", "^/api/v1/admin/users/\\{[^/]+}/sessions/revoke$", "user.session.revoke"),
            rule("PUT", "^/api/v1/admin/users/\\{[^/]+}/profile$", "user.manage"),
            rule("PUT", "^/api/v1/admin/users/\\{[^/]+}/memberships$", "user.membership.manage"),
            rule("GET", "^/api/v1/admin/academic-terms$", "course.read"),
            rule("POST", "^/api/v1/admin/academic-terms$", "course.manage"),
            rule("GET", "^/api/v1/admin/course-catalogs$", "course.read"),
            rule("POST", "^/api/v1/admin/course-catalogs$", "course.manage"),
            rule("GET", "^/api/v1/admin/course-offerings$", "offering.read"),
            rule("GET", "^/api/v1/admin/course-offerings/\\{[^/]+}$", "offering.read"),
            rule("POST", "^/api/v1/admin/course-offerings$", "offering.manage"),
            rule("GET", "^/api/v1/me/courses$", "RULE.MY_COURSES"),
            rule("GET", "^/api/v1/me/assignments$", "assignment.read"),
            rule("GET", "^/api/v1/me/assignments/\\{[^/]+}$", "assignment.read"),
            rule(
                    "POST",
                    "^/api/v1/me/assignments/\\{[^/]+}/submission-artifacts$",
                    "RULE.SUBMISSION_ARTIFACT_UPLOAD_OWN"),
            rule("POST", "^/api/v1/me/assignments/\\{[^/]+}/submissions$", "RULE.SUBMISSION_CREATE_OWN"),
            rule("GET", "^/api/v1/me/assignments/\\{[^/]+}/submissions$", "submission.read.own"),
            rule("GET", "^/api/v1/me/submissions/\\{[^/]+}$", "submission.read.own"),
            rule("GET", "^/api/v1/me/submission-artifacts/\\{[^/]+}/download$", "submission.read.own"),
            rule(
                    "GET",
                    "^/api/v1/me/assignments/\\{[^/]+}/programming-questions/\\{[^/]+}/workspace$",
                    "RULE.WORKSPACE_OWN"),
            rule(
                    "PUT",
                    "^/api/v1/me/assignments/\\{[^/]+}/programming-questions/\\{[^/]+}/workspace$",
                    "RULE.WORKSPACE_OWN"),
            rule(
                    "POST",
                    "^/api/v1/me/assignments/\\{[^/]+}/programming-questions/\\{[^/]+}/workspace/operations$",
                    "RULE.WORKSPACE_OWN"),
            rule(
                    "GET",
                    "^/api/v1/me/assignments/\\{[^/]+}/programming-questions/\\{[^/]+}/workspace/revisions$",
                    "RULE.WORKSPACE_OWN"),
            rule(
                    "GET",
                    "^/api/v1/me/assignments/\\{[^/]+}/programming-questions/\\{[^/]+}/workspace/revisions/\\{[^/]+}$",
                    "RULE.WORKSPACE_OWN"),
            rule(
                    "POST",
                    "^/api/v1/me/assignments/\\{[^/]+}/programming-questions/\\{[^/]+}/workspace/revisions/\\{[^/]+}/restore$",
                    "RULE.WORKSPACE_OWN"),
            rule(
                    "POST",
                    "^/api/v1/me/assignments/\\{[^/]+}/programming-questions/\\{[^/]+}/workspace/reset-to-template$",
                    "RULE.WORKSPACE_OWN"),
            rule(
                    "POST",
                    "^/api/v1/me/assignments/\\{[^/]+}/programming-questions/\\{[^/]+}/sample-runs$",
                    "RULE.SAMPLE_RUN_OWN"),
            rule(
                    "GET",
                    "^/api/v1/me/assignments/\\{[^/]+}/programming-questions/\\{[^/]+}/sample-runs$",
                    "RULE.SAMPLE_RUN_OWN"),
            rule(
                    "GET",
                    "^/api/v1/me/assignments/\\{[^/]+}/programming-questions/\\{[^/]+}/sample-runs/\\{[^/]+}$",
                    "RULE.SAMPLE_RUN_OWN"),
            rule("GET", "^/api/v1/me/submissions/\\{[^/]+}/judge-jobs$", "submission.read.own"),
            rule("GET", "^/api/v1/me/submission-answers/\\{[^/]+}/judge-jobs$", "submission.read.own"),
            rule("GET", "^/api/v1/me/judge-jobs/\\{[^/]+}/report$", "submission.read.own"),
            rule("GET", "^/api/v1/me/judge-jobs/\\{[^/]+}/report/download$", "submission.read.own"),
            rule("GET", "^/api/v1/me/course-offerings/\\{[^/]+}/gradebook$", "grade.read.own"),
            rule("GET", "^/api/v1/me/course-offerings/\\{[^/]+}/gradebook/export$", "grade.read.own"),
            rule("GET", "^/api/v1/me/course-classes/\\{[^/]+}/announcements$", "announcement.read"),
            rule("GET", "^/api/v1/me/announcements/\\{[^/]+}$", "announcement.read"),
            rule("GET", "^/api/v1/me/course-classes/\\{[^/]+}/resources$", "resource.read"),
            rule("GET", "^/api/v1/me/course-resources/\\{[^/]+}/download$", "resource.read"),
            rule("POST", "^/api/v1/me/course-classes/\\{[^/]+}/discussions$", "discussion.participate"),
            rule("GET", "^/api/v1/me/course-classes/\\{[^/]+}/discussions$", "discussion.participate"),
            rule("GET", "^/api/v1/me/discussions/\\{[^/]+}$", "discussion.participate"),
            rule("POST", "^/api/v1/me/discussions/\\{[^/]+}/replies$", "discussion.participate"),
            rule("POST", "^/api/v1/me/submissions/\\{[^/]+}/answers/\\{[^/]+}/appeals$", "RULE.APPEAL_CREATE_OWN"),
            rule("GET", "^/api/v1/me/course-offerings/\\{[^/]+}/grade-appeals$", "appeal.read.own"),
            rule("GET", "^/api/v1/me/course-classes/\\{[^/]+}/labs$", "lab.read"),
            rule("GET", "^/api/v1/me/labs/\\{[^/]+}$", "lab.read"),
            rule("GET", "^/api/v1/me/labs/\\{[^/]+}/report$", "lab.read"),
            rule("GET", "^/api/v1/me/lab-report-attachments/\\{[^/]+}/download$", "lab.read"),
            rule("POST", "^/api/v1/me/labs/\\{[^/]+}/attachments$", "RULE.LAB_REPORT_WRITE_OWN"),
            rule("PUT", "^/api/v1/me/labs/\\{[^/]+}/report$", "RULE.LAB_REPORT_WRITE_OWN"),
            rule("GET", "^/api/v1/me/notifications$", "RULE.NOTIFICATION_OWN"),
            rule("GET", "^/api/v1/me/notifications/unread-count$", "RULE.NOTIFICATION_OWN"),
            rule("GET", "^/api/v1/me/notifications/stream$", "RULE.NOTIFICATION_OWN"),
            rule("POST", "^/api/v1/me/notifications/read-all$", "RULE.NOTIFICATION_OWN"),
            rule("POST", "^/api/v1/me/notifications/\\{[^/]+}/read$", "RULE.NOTIFICATION_OWN"),
            rule("POST", "^/api/v1/teacher/course-offerings/\\{[^/]+}/classes$", "class.manage"),
            rule("GET", "^/api/v1/teacher/course-offerings/\\{[^/]+}/classes$", "class.read"),
            rule("PUT", "^/api/v1/teacher/course-classes/\\{[^/]+}/features$", "class.manage"),
            rule("GET", "^/api/v1/teacher/course-offerings/\\{[^/]+}/members$", "member.read"),
            rule("POST", "^/api/v1/teacher/course-offerings/\\{[^/]+}/members/batch$", "member.manage"),
            rule("POST", "^/api/v1/teacher/course-offerings/\\{[^/]+}/members/import$", "member.import"),
            rule("PATCH", "^/api/v1/teacher/course-offerings/\\{[^/]+}/members/\\{[^/]+}/status$", "member.manage"),
            rule("POST", "^/api/v1/teacher/course-offerings/\\{[^/]+}/members/\\{[^/]+}/transfer$", "member.manage"),
            rule("POST", "^/api/v1/teacher/course-offerings/\\{[^/]+}/announcements$", "announcement.publish"),
            rule("GET", "^/api/v1/teacher/course-offerings/\\{[^/]+}/announcements$", "announcement.publish"),
            rule("POST", "^/api/v1/teacher/course-offerings/\\{[^/]+}/resources$", "resource.manage"),
            rule("GET", "^/api/v1/teacher/course-offerings/\\{[^/]+}/resources$", "resource.manage"),
            rule("GET", "^/api/v1/teacher/course-resources/\\{[^/]+}/download$", "resource.manage"),
            rule("POST", "^/api/v1/teacher/course-offerings/\\{[^/]+}/discussions$", "discussion.manage"),
            rule("GET", "^/api/v1/teacher/course-offerings/\\{[^/]+}/discussions$", "discussion.manage"),
            rule("GET", "^/api/v1/teacher/discussions/\\{[^/]+}$", "discussion.manage"),
            rule("POST", "^/api/v1/teacher/discussions/\\{[^/]+}/replies$", "discussion.manage"),
            rule("PUT", "^/api/v1/teacher/discussions/\\{[^/]+}/lock-state$", "discussion.manage"),
            rule("POST", "^/api/v1/teacher/course-offerings/\\{[^/]+}/assignments$", "assignment.create"),
            rule("GET", "^/api/v1/teacher/course-offerings/\\{[^/]+}/assignments$", "assignment.read"),
            rule("GET", "^/api/v1/teacher/assignments/\\{[^/]+}$", "assignment.read"),
            rule("PUT", "^/api/v1/teacher/assignments/\\{[^/]+}$", "assignment.update"),
            rule("PUT", "^/api/v1/teacher/assignments/\\{[^/]+}/paper$", "assignment.update"),
            rule("POST", "^/api/v1/teacher/assignments/\\{[^/]+}/publish$", "assignment.publish"),
            rule("POST", "^/api/v1/teacher/assignments/\\{[^/]+}/close$", "assignment.close"),
            rule(
                    "POST",
                    "^/api/v1/teacher/course-offerings/\\{[^/]+}/question-bank/questions$",
                    "question_bank.manage"),
            rule("GET", "^/api/v1/teacher/course-offerings/\\{[^/]+}/question-bank/questions$", "question.read"),
            rule("GET", "^/api/v1/teacher/course-offerings/\\{[^/]+}/question-bank/categories$", "question.read"),
            rule("GET", "^/api/v1/teacher/course-offerings/\\{[^/]+}/question-bank/tags$", "question.read"),
            rule("GET", "^/api/v1/teacher/question-bank/questions/\\{[^/]+}$", "question.read"),
            rule("PUT", "^/api/v1/teacher/question-bank/questions/\\{[^/]+}$", "question_bank.manage"),
            rule("POST", "^/api/v1/teacher/question-bank/questions/\\{[^/]+}/archive$", "question_bank.manage"),
            rule(
                    "GET",
                    "^/api/v1/teacher/assignments/\\{[^/]+}/submissions$",
                    "submission.read.class|submission.read.offering + submission.code.read_sensitive"),
            rule(
                    "GET",
                    "^/api/v1/teacher/submissions/\\{[^/]+}$",
                    "submission.read.class|submission.read.offering + submission.code.read_sensitive"),
            rule(
                    "GET",
                    "^/api/v1/teacher/submission-artifacts/\\{[^/]+}/download$",
                    "submission.read.class|submission.read.offering + submission.code.read_sensitive"),
            rule(
                    "GET",
                    "^/api/v1/teacher/submissions/\\{[^/]+}/judge-jobs$",
                    "submission.read.class|submission.read.offering + submission.code.read_sensitive"),
            rule(
                    "GET",
                    "^/api/v1/teacher/submission-answers/\\{[^/]+}/judge-jobs$",
                    "submission.read.class|submission.read.offering + submission.code.read_sensitive"),
            rule(
                    "GET",
                    "^/api/v1/teacher/judge-jobs/\\{[^/]+}/report$",
                    "submission.read.class|submission.read.offering + submission.code.read_sensitive"),
            rule(
                    "GET",
                    "^/api/v1/teacher/judge-jobs/\\{[^/]+}/report/download$",
                    "submission.read.class|submission.read.offering + submission.code.read_sensitive"),
            rule("POST", "^/api/v1/teacher/submissions/\\{[^/]+}/judge-jobs/requeue$", "submission.rejudge"),
            rule("POST", "^/api/v1/teacher/submission-answers/\\{[^/]+}/judge-jobs/requeue$", "submission.rejudge"),
            rule(
                    "POST",
                    "^/api/v1/teacher/course-offerings/\\{[^/]+}/judge-environment-profiles$",
                    "judge.profile.manage"),
            rule(
                    "GET",
                    "^/api/v1/teacher/course-offerings/\\{[^/]+}/judge-environment-profiles$",
                    "judge.profile.manage"),
            rule("GET", "^/api/v1/teacher/judge-environment-profiles/\\{[^/]+}$", "judge.profile.manage"),
            rule("PUT", "^/api/v1/teacher/judge-environment-profiles/\\{[^/]+}$", "judge.profile.manage"),
            rule("POST", "^/api/v1/teacher/judge-environment-profiles/\\{[^/]+}/archive$", "judge.profile.manage"),
            rule("POST", "^/api/v1/teacher/submissions/\\{[^/]+}/answers/\\{[^/]+}/grade$", "submission.grade"),
            rule("POST", "^/api/v1/teacher/assignments/\\{[^/]+}/grades/batch-adjust$", "submission.grade"),
            rule("GET", "^/api/v1/teacher/assignments/\\{[^/]+}/grades/import-template$", "submission.grade"),
            rule("POST", "^/api/v1/teacher/assignments/\\{[^/]+}/grades/import$", "submission.grade"),
            rule("POST", "^/api/v1/teacher/assignments/\\{[^/]+}/grades/publish$", "grade.publish"),
            rule("GET", "^/api/v1/teacher/assignments/\\{[^/]+}/grade-publish-batches$", "grade.publish"),
            rule("GET", "^/api/v1/teacher/assignments/\\{[^/]+}/grade-publish-batches/\\{[^/]+}$", "grade.publish"),
            rule("GET", "^/api/v1/teacher/course-offerings/\\{[^/]+}/gradebook$", "grade.export.offering"),
            rule("GET", "^/api/v1/teacher/course-offerings/\\{[^/]+}/gradebook/export$", "grade.export.offering"),
            rule("GET", "^/api/v1/teacher/course-offerings/\\{[^/]+}/gradebook/report$", "grade.export.offering"),
            rule(
                    "GET",
                    "^/api/v1/teacher/course-offerings/\\{[^/]+}/students/\\{[^/]+}/gradebook$",
                    "grade.export.offering"),
            rule(
                    "GET",
                    "^/api/v1/teacher/teaching-classes/\\{[^/]+}/gradebook$",
                    "grade.export.class|grade.export.offering"),
            rule(
                    "GET",
                    "^/api/v1/teacher/teaching-classes/\\{[^/]+}/gradebook/export$",
                    "grade.export.class|grade.export.offering"),
            rule(
                    "GET",
                    "^/api/v1/teacher/teaching-classes/\\{[^/]+}/gradebook/report$",
                    "grade.export.class|grade.export.offering"),
            rule("GET", "^/api/v1/teacher/assignments/\\{[^/]+}/grade-appeals$", "appeal.read.class"),
            rule("POST", "^/api/v1/teacher/grade-appeals/\\{[^/]+}/review$", "appeal.review"),
            rule("POST", "^/api/v1/teacher/course-offerings/\\{[^/]+}/labs$", "lab.manage"),
            rule("PUT", "^/api/v1/teacher/labs/\\{[^/]+}$", "lab.manage"),
            rule("POST", "^/api/v1/teacher/labs/\\{[^/]+}/publish$", "lab.manage"),
            rule("POST", "^/api/v1/teacher/labs/\\{[^/]+}/close$", "lab.manage"),
            rule("GET", "^/api/v1/teacher/course-offerings/\\{[^/]+}/labs$", "lab.read"),
            rule("GET", "^/api/v1/teacher/labs/\\{[^/]+}$", "lab.read"),
            rule("GET", "^/api/v1/teacher/labs/\\{[^/]+}/reports$", "lab.report.review"),
            rule("GET", "^/api/v1/teacher/lab-reports/\\{[^/]+}$", "lab.report.review"),
            rule("PUT", "^/api/v1/teacher/lab-reports/\\{[^/]+}/review$", "lab.report.review"),
            rule("POST", "^/api/v1/teacher/lab-reports/\\{[^/]+}/publish$", "lab.report.review"),
            rule("GET", "^/api/v1/teacher/lab-report-attachments/\\{[^/]+}/download$", "lab.report.review"));

    private AuthzOpenApiAccessRegistry() {}

    static Optional<String> resolve(String method, String path) {
        return RULES.stream()
                .filter(rule -> rule.matches(method, path))
                .map(Rule::access)
                .findFirst();
    }

    private static Rule rule(String method, String pathRegex, String access) {
        return new Rule(method, Pattern.compile(pathRegex), access);
    }

    private record Rule(String method, Pattern pathPattern, String access) {
        private boolean matches(String actualMethod, String actualPath) {
            return method.equalsIgnoreCase(actualMethod)
                    && pathPattern.matcher(actualPath).matches();
        }
    }
}
