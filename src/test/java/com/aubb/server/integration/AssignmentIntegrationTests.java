package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;

class AssignmentIntegrationTests extends AbstractNonRateLimitedIntegrationTest {

    private String latestTeacherToken;

    @Test
    void teacherCreatesPublishesAndClosesAssignment() throws Exception {
        String schoolAdminToken = IntegrationTestData.login(mockMvc, "school-admin");
        String engAdminToken = IntegrationTestData.login(mockMvc, "eng-admin");
        String teacherToken = IntegrationTestData.login(mockMvc, "teacher-main");

        Long termId = IntegrationTestData.createTerm(mockMvc, schoolAdminToken);
        Long catalogId = IntegrationTestData.createCatalog(mockMvc, engAdminToken);
        Long offeringId = IntegrationTestData.createOffering(mockMvc, engAdminToken, catalogId, termId);
        refreshTeacherToken("class.manage", "member.manage", "task.create");
        Long classId = IntegrationTestData.createTeachingClass(
                mockMvc, resolveTeacherToken(teacherToken), offeringId, "CLS-2024", "24级班", 2024);

        Long assignmentId = IntegrationTestData.createAssignment(
                mockMvc, resolveTeacherToken(teacherToken), offeringId, classId, "链表实验一");

        mockMvc.perform(post("/api/v1/teacher/assignments/{assignmentId}/publish", assignmentId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").isNotEmpty());

        mockMvc.perform(post("/api/v1/teacher/assignments/{assignmentId}/close", assignmentId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/assignments", offeringId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("链表实验一"))
                .andExpect(jsonPath("$.items[0].teachingClass.id").value(classId));

        assertThat(IntegrationTestData.queryForCount(
                        jdbcTemplate, "SELECT COUNT(*) FROM assignments WHERE offering_id = 1"))
                .isEqualTo(1);
        assertThat(IntegrationTestData.queryForCount(
                        jdbcTemplate, "SELECT COUNT(*) FROM audit_logs WHERE action = 'ASSIGNMENT_PUBLISHED'"))
                .isEqualTo(1);
        assertThat(IntegrationTestData.queryForCount(
                        jdbcTemplate, "SELECT COUNT(*) FROM audit_logs WHERE action = 'ASSIGNMENT_CLOSED'"))
                .isEqualTo(1);
    }

    @Test
    void teacherListsMultipleAssignmentsUnderSameOffering() throws Exception {
        String schoolAdminToken = IntegrationTestData.login(mockMvc, "school-admin");
        String engAdminToken = IntegrationTestData.login(mockMvc, "eng-admin");
        String teacherToken = IntegrationTestData.login(mockMvc, "teacher-main");

        Long termId = IntegrationTestData.createTerm(mockMvc, schoolAdminToken);
        Long catalogId = IntegrationTestData.createCatalog(mockMvc, engAdminToken);
        Long offeringId = IntegrationTestData.createOffering(mockMvc, engAdminToken, catalogId, termId);
        refreshTeacherToken("class.manage", "member.manage", "task.create");
        Long classId = IntegrationTestData.createTeachingClass(
                mockMvc, resolveTeacherToken(teacherToken), offeringId, "CLS-2024", "24级班", 2024);

        Long assignmentIdA = IntegrationTestData.createAssignment(
                mockMvc, resolveTeacherToken(teacherToken), offeringId, classId, "链表实验一");
        Long assignmentIdB = IntegrationTestData.createAssignment(
                mockMvc, resolveTeacherToken(teacherToken), offeringId, classId, "链表实验二");

        mockMvc.perform(get("/api/v1/teacher/course-offerings/{offeringId}/assignments", offeringId)
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(assignmentIdB))
                .andExpect(jsonPath("$.items[1].id").value(assignmentIdA));
    }

    @Test
    void studentSeesOnlyPublishedAssignmentsForOwnCourseAndClass() throws Exception {
        String schoolAdminToken = IntegrationTestData.login(mockMvc, "school-admin");
        String engAdminToken = IntegrationTestData.login(mockMvc, "eng-admin");
        String teacherToken = IntegrationTestData.login(mockMvc, "teacher-main");
        String studentAToken = IntegrationTestData.login(mockMvc, "student-a");
        String studentBToken = IntegrationTestData.login(mockMvc, "student-b");

        Long termId = IntegrationTestData.createTerm(mockMvc, schoolAdminToken);
        Long catalogId = IntegrationTestData.createCatalog(mockMvc, engAdminToken);
        Long offeringId = IntegrationTestData.createOffering(mockMvc, engAdminToken, catalogId, termId);
        refreshTeacherToken("class.manage", "member.manage", "task.create");
        Long classAId = IntegrationTestData.createTeachingClass(
                mockMvc, resolveTeacherToken(teacherToken), offeringId, "CLS-A", "A班", 2024);
        Long classBId = IntegrationTestData.createTeachingClass(
                mockMvc, resolveTeacherToken(teacherToken), offeringId, "CLS-B", "B班", 2025);

        IntegrationTestData.addMember(mockMvc, resolveTeacherToken(teacherToken), offeringId, 4L, "STUDENT", classAId);
        IntegrationTestData.addMember(mockMvc, resolveTeacherToken(teacherToken), offeringId, 5L, "STUDENT", classBId);
        IntegrationTestData.addMember(mockMvc, resolveTeacherToken(teacherToken), offeringId, 6L, "TA", classAId);
        studentAToken = IntegrationTestData.login(mockMvc, "student-a");
        studentBToken = IntegrationTestData.login(mockMvc, "student-b");

        Long offeringAssignmentId =
                IntegrationTestData.createAssignment(mockMvc, resolveTeacherToken(teacherToken), offeringId, "课程公共任务");
        Long classAssignmentId = IntegrationTestData.createAssignment(
                mockMvc, resolveTeacherToken(teacherToken), offeringId, classAId, "A班专属任务");
        Long draftAssignmentId = IntegrationTestData.createAssignment(
                mockMvc, resolveTeacherToken(teacherToken), offeringId, classBId, "B班草稿任务");

        IntegrationTestData.publishAssignment(mockMvc, resolveTeacherToken(teacherToken), offeringAssignmentId);
        IntegrationTestData.publishAssignment(mockMvc, resolveTeacherToken(teacherToken), classAssignmentId);

        IntegrationTestAwait.awaitCount(() -> IntegrationTestData.queryForCount(jdbcTemplate, """
                                SELECT COUNT(*)
                                FROM notification_receipts nr
                                JOIN notifications n ON n.id = nr.notification_id
                                WHERE nr.recipient_user_id = 4
                                  AND n.type = 'ASSIGNMENT_PUBLISHED'
                                """), 2);
        IntegrationTestAwait.awaitCount(() -> IntegrationTestData.queryForCount(jdbcTemplate, """
                                SELECT COUNT(*)
                                FROM notification_receipts nr
                                JOIN notifications n ON n.id = nr.notification_id
                                WHERE nr.recipient_user_id = 5
                                  AND n.type = 'ASSIGNMENT_PUBLISHED'
                                """), 1);

        mockMvc.perform(get("/api/v1/me/assignments").header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].title").exists());

        mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", classAssignmentId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("A班专属任务"));

        mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", offeringAssignmentId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("课程公共任务"));

        mockMvc.perform(get("/api/v1/me/assignments").header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("课程公共任务"));

        mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", offeringAssignmentId)
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("课程公共任务"));

        mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", classAssignmentId)
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", draftAssignmentId)
                        .header("Authorization", "Bearer " + studentBToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void paginatesMyAssignmentsAndKeepsOfferingScopeAccurate() throws Exception {
        String schoolAdminToken = IntegrationTestData.login(mockMvc, "school-admin");
        String engAdminToken = IntegrationTestData.login(mockMvc, "eng-admin");
        String teacherToken = IntegrationTestData.login(mockMvc, "teacher-main");
        String studentAToken = IntegrationTestData.login(mockMvc, "student-a");

        Long termId = IntegrationTestData.createTerm(mockMvc, schoolAdminToken);
        Long catalogId = IntegrationTestData.createCatalog(mockMvc, engAdminToken);
        Long offeringId = IntegrationTestData.createOffering(mockMvc, engAdminToken, catalogId, termId);
        Long otherOfferingId = IntegrationTestData.createOffering(
                mockMvc, engAdminToken, catalogId, termId, "CS101-2026SP-02", "数据结构（2026春）-2");
        refreshTeacherToken("class.manage", "member.manage", "task.create");
        Long classAId = IntegrationTestData.createTeachingClass(
                mockMvc, resolveTeacherToken(teacherToken), offeringId, "CLS-A", "A班", 2024);
        Long classBId = IntegrationTestData.createTeachingClass(
                mockMvc, resolveTeacherToken(teacherToken), otherOfferingId, "CLS-B", "B班", 2025);

        IntegrationTestData.addMember(mockMvc, resolveTeacherToken(teacherToken), offeringId, 4L, "STUDENT", classAId);
        studentAToken = IntegrationTestData.login(mockMvc, "student-a");

        Long offeringAssignmentId =
                IntegrationTestData.createAssignment(mockMvc, resolveTeacherToken(teacherToken), offeringId, "课程公共任务");
        Long classAssignmentOneId = IntegrationTestData.createAssignment(
                mockMvc, resolveTeacherToken(teacherToken), offeringId, classAId, "A班任务一");
        Long classAssignmentTwoId = IntegrationTestData.createAssignment(
                mockMvc, resolveTeacherToken(teacherToken), offeringId, classAId, "A班任务二");
        Long hiddenOtherOfferingAssignmentId = IntegrationTestData.createAssignment(
                mockMvc, resolveTeacherToken(teacherToken), otherOfferingId, classBId, "其他开课任务");

        IntegrationTestData.publishAssignment(mockMvc, resolveTeacherToken(teacherToken), offeringAssignmentId);
        IntegrationTestData.publishAssignment(mockMvc, resolveTeacherToken(teacherToken), classAssignmentOneId);
        IntegrationTestData.publishAssignment(mockMvc, resolveTeacherToken(teacherToken), classAssignmentTwoId);
        IntegrationTestData.publishAssignment(
                mockMvc, resolveTeacherToken(teacherToken), hiddenOtherOfferingAssignmentId);

        mockMvc.perform(get("/api/v1/me/assignments")
                        .header("Authorization", "Bearer " + studentAToken)
                        .param("page", "1")
                        .param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].title").value("课程公共任务"))
                .andExpect(jsonPath("$.items[1].title").value("A班任务一"));

        mockMvc.perform(get("/api/v1/me/assignments")
                        .header("Authorization", "Bearer " + studentAToken)
                        .param("page", "2")
                        .param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("A班任务二"));

        mockMvc.perform(get("/api/v1/me/assignments")
                        .header("Authorization", "Bearer " + studentAToken)
                        .param("offeringId", String.valueOf(offeringId))
                        .param("page", "2")
                        .param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].title").value("A班任务一"));

        mockMvc.perform(get("/api/v1/me/assignments")
                        .header("Authorization", "Bearer " + resolveTeacherToken(teacherToken))
                        .param("offeringId", String.valueOf(otherOfferingId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("其他开课任务"));
    }

    @Test
    void studentCannotManageAssignments() throws Exception {
        String schoolAdminToken = IntegrationTestData.login(mockMvc, "school-admin");
        String engAdminToken = IntegrationTestData.login(mockMvc, "eng-admin");
        String teacherToken = IntegrationTestData.login(mockMvc, "teacher-main");
        String studentAToken = IntegrationTestData.login(mockMvc, "student-a");

        Long termId = IntegrationTestData.createTerm(mockMvc, schoolAdminToken);
        Long catalogId = IntegrationTestData.createCatalog(mockMvc, engAdminToken);
        Long offeringId = IntegrationTestData.createOffering(mockMvc, engAdminToken, catalogId, termId);
        refreshTeacherToken("class.manage", "member.manage", "task.create");
        Long classAId = IntegrationTestData.createTeachingClass(
                mockMvc, resolveTeacherToken(teacherToken), offeringId, "CLS-A", "A班", 2024);
        IntegrationTestData.addMember(mockMvc, resolveTeacherToken(teacherToken), offeringId, 4L, "STUDENT", classAId);
        studentAToken = IntegrationTestData.login(mockMvc, "student-a");

        mockMvc.perform(post("/api/v1/teacher/course-offerings/{offeringId}/assignments", offeringId)
                        .header("Authorization", "Bearer " + studentAToken)
                        .contentType("application/json")
                        .content("""
                                {
                                  "title":"越权任务",
                                  "description":"不应创建成功",
                                  "teachingClassId":%s,
                                  "openAt":"2026-03-01T08:00:00+08:00",
                                  "dueAt":"2026-03-08T23:59:59+08:00",
                                  "maxSubmissions":3
                                }
                                """.formatted(classAId)))
                .andExpect(status().isForbidden());
    }

    @Test
    void activeStudentWithoutRoleBindingsCannotReadMyAssignment() throws Exception {
        String schoolAdminToken = IntegrationTestData.login(mockMvc, "school-admin");
        String engAdminToken = IntegrationTestData.login(mockMvc, "eng-admin");
        String teacherToken = IntegrationTestData.login(mockMvc, "teacher-main");
        String studentAToken = IntegrationTestData.login(mockMvc, "student-a");

        Long termId = IntegrationTestData.createTerm(mockMvc, schoolAdminToken);
        Long catalogId = IntegrationTestData.createCatalog(mockMvc, engAdminToken);
        Long offeringId = IntegrationTestData.createOffering(mockMvc, engAdminToken, catalogId, termId);
        refreshTeacherToken("class.manage", "member.manage", "task.create");
        Long classAId = IntegrationTestData.createTeachingClass(
                mockMvc, resolveTeacherToken(teacherToken), offeringId, "CLS-A", "A班", 2024);
        IntegrationTestData.addMember(mockMvc, resolveTeacherToken(teacherToken), offeringId, 4L, "STUDENT", classAId);
        studentAToken = IntegrationTestData.login(mockMvc, "student-a");

        Long assignmentId = IntegrationTestData.createAssignment(
                mockMvc, resolveTeacherToken(teacherToken), offeringId, classAId, "角色绑定收口任务");
        IntegrationTestData.publishAssignment(mockMvc, resolveTeacherToken(teacherToken), assignmentId);

        jdbcTemplate.update("DELETE FROM role_bindings WHERE user_id = ?", 4L);

        mockMvc.perform(get("/api/v1/me/assignments").header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));

        mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void historicalStudentWithoutRoleBindingsCanStillListAndReadOwnAssignment() throws Exception {
        String schoolAdminToken = IntegrationTestData.login(mockMvc, "school-admin");
        String engAdminToken = IntegrationTestData.login(mockMvc, "eng-admin");
        String teacherToken = IntegrationTestData.login(mockMvc, "teacher-main");
        String studentAToken = IntegrationTestData.login(mockMvc, "student-a");

        Long termId = IntegrationTestData.createTerm(mockMvc, schoolAdminToken);
        Long catalogId = IntegrationTestData.createCatalog(mockMvc, engAdminToken);
        Long offeringId = IntegrationTestData.createOffering(mockMvc, engAdminToken, catalogId, termId);
        refreshTeacherToken("class.manage", "member.manage", "task.create");
        Long classAId = IntegrationTestData.createTeachingClass(
                mockMvc, resolveTeacherToken(teacherToken), offeringId, "CLS-A", "A班", 2024);
        IntegrationTestData.addMember(mockMvc, resolveTeacherToken(teacherToken), offeringId, 4L, "STUDENT", classAId);
        studentAToken = IntegrationTestData.login(mockMvc, "student-a");

        Long assignmentId = IntegrationTestData.createAssignment(
                mockMvc, resolveTeacherToken(teacherToken), offeringId, classAId, "历史作业可读任务");
        IntegrationTestData.publishAssignment(mockMvc, resolveTeacherToken(teacherToken), assignmentId);

        jdbcTemplate.update(
                "UPDATE course_members SET member_status = 'DROPPED' WHERE user_id = ? AND offering_id = ?",
                4L,
                offeringId);
        jdbcTemplate.update("DELETE FROM role_bindings WHERE user_id = ?", 4L);

        mockMvc.perform(get("/api/v1/me/assignments").header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(assignmentId))
                .andExpect(jsonPath("$.items[0].title").value("历史作业可读任务"));

        mockMvc.perform(get("/api/v1/me/assignments/{assignmentId}", assignmentId)
                        .header("Authorization", "Bearer " + studentAToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(assignmentId))
                .andExpect(jsonPath("$.title").value("历史作业可读任务"));
    }

    private String resolveTeacherToken(String token) {
        if (latestTeacherToken == null) {
            return token;
        }
        String[] segments = token.split("\\.");
        if (segments.length < 2) {
            return token;
        }
        String payload = new String(
                java.util.Base64.getUrlDecoder().decode(segments[1]), java.nio.charset.StandardCharsets.UTF_8);
        String subject = JsonPath.read(payload, "$.sub");
        return "teacher-main".equals(subject) ? latestTeacherToken : token;
    }

    private void refreshTeacherToken(String... expectedPermissionCodes) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(3).toNanos();
        while (true) {
            String candidate = IntegrationTestData.login(mockMvc, "teacher-main");
            if (isRoleBindingSnapshotReady(candidate)
                    && tokenContainsAllPermissions(candidate, expectedPermissionCodes)) {
                latestTeacherToken = candidate;
                return;
            }
            if (System.nanoTime() >= deadline) {
                latestTeacherToken = candidate;
                assertThat(isRoleBindingSnapshotReady(candidate)).isTrue();
                assertThat(readPermissionCodes(candidate)).contains(expectedPermissionCodes);
                return;
            }
            try {
                Thread.sleep(50L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待教师权限快照收敛时被中断", exception);
            }
        }
    }

    private boolean isRoleBindingSnapshotReady(String token) {
        return Boolean.TRUE.equals(readTokenClaim(token, "$.roleBindingSnapshot"));
    }

    private boolean tokenContainsAllPermissions(String token, String... expectedPermissionCodes) {
        return readPermissionCodes(token).containsAll(java.util.List.of(expectedPermissionCodes));
    }

    private java.util.List<String> readPermissionCodes(String token) {
        java.util.List<String> permissionCodes = readTokenClaim(token, "$.permissionCodes");
        return permissionCodes == null ? java.util.List.of() : permissionCodes;
    }

    private <T> T readTokenClaim(String token, String path) {
        String[] segments = token.split("\\.");
        if (segments.length < 2) {
            return null;
        }
        String payload = new String(
                java.util.Base64.getUrlDecoder().decode(segments[1]), java.nio.charset.StandardCharsets.UTF_8);
        return JsonPath.read(payload, path);
    }
}
