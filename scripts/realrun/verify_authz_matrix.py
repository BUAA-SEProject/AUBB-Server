#!/usr/bin/env python3
import argparse
import http.client
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from pathlib import Path


class HttpFailure(RuntimeError):
    def __init__(self, method, path, status, body):
        super().__init__(f"{method} {path} -> {status}: {body[:400]}")
        self.method = method
        self.path = path
        self.status = status
        self.body = body


@dataclass
class Response:
    status: int
    headers: dict
    body: bytes

    def json(self):
        return json.loads(self.body.decode("utf-8"))

    def text(self):
        return self.body.decode("utf-8", errors="replace")


class ApiClient:
    def __init__(self, base_url, timeout=30):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout

    def request(self, method, path, token=None, json_body=None, expected_status=None, headers=None):
        final_headers = {"Accept": "application/json"}
        if headers:
            final_headers.update(headers)
        data = None
        if json_body is not None:
            data = json.dumps(json_body).encode("utf-8")
            final_headers["Content-Type"] = "application/json"
        if token:
            final_headers["Authorization"] = f"Bearer {token}"
        req = urllib.request.Request(
            self.base_url + path,
            data=data,
            headers=final_headers,
            method=method,
        )
        try:
            with urllib.request.urlopen(req, timeout=self.timeout) as resp:
                response = Response(resp.status, dict(resp.headers), resp.read())
        except urllib.error.HTTPError as exc:
            response = Response(exc.code, dict(exc.headers), exc.read())
        if expected_status is not None:
            allowed = expected_status if isinstance(expected_status, (list, tuple, set)) else [expected_status]
            if response.status not in allowed:
                raise HttpFailure(method, path, response.status, response.text())
        return response

    def open_stream(self, path, token=None, expected_status=None, headers=None):
        final_headers = {"Accept": "text/event-stream"}
        if headers:
            final_headers.update(headers)
        if token:
            final_headers["Authorization"] = f"Bearer {token}"
        parsed = urllib.parse.urlparse(self.base_url)
        request_path = (parsed.path.rstrip("/") + path) if parsed.path else path
        connection_cls = http.client.HTTPSConnection if parsed.scheme == "https" else http.client.HTTPConnection
        conn = connection_cls(parsed.hostname, parsed.port, timeout=self.timeout)
        try:
            conn.request("GET", request_path, headers=final_headers)
            resp = conn.getresponse()
            response = Response(resp.status, dict(resp.headers), b"")
            resp.close()
        finally:
            conn.close()
        if expected_status is not None:
            allowed = expected_status if isinstance(expected_status, (list, tuple, set)) else [expected_status]
            if response.status not in allowed:
                raise HttpFailure("GET", path, response.status, response.text())
        return response

    def get_json(self, path, token=None, expected_status=200):
        return self.request("GET", path, token=token, expected_status=expected_status).json()

    def post_json(self, path, body, token=None, expected_status=200):
        return self.request("POST", path, token=token, json_body=body, expected_status=expected_status).json()

    def put_json(self, path, body, token=None, expected_status=200):
        return self.request("PUT", path, token=token, json_body=body, expected_status=expected_status).json()


def append_result(results, name, passed, **kwargs):
    record = {"name": name, "passed": passed}
    record.update(kwargs)
    results.append(record)


def expect_status(results, name, response, expected_status, path, note=None):
    passed = response.status == expected_status
    append_result(
        results,
        name,
        passed,
        method=response.headers.get(":method"),
        path=path,
        expectedStatus=expected_status,
        actualStatus=response.status,
        note=note,
        bodySnippet=response.text()[:400],
    )
    if not passed:
        raise RuntimeError(f"{name} 失败: {path} 返回 {response.status}，期望 {expected_status}")


def check(results, name, action):
    try:
        detail = action() or {}
        append_result(results, name, True, **detail)
    except Exception as exc:  # pylint: disable=broad-except
        append_result(results, name, False, error=str(exc))
        raise


def create_user(api, admin_token, payload):
    return api.post_json("/api/v1/admin/users", payload, token=admin_token, expected_status=201)


def login(api, username, password):
    response = api.post_json(
        "/api/v1/auth/login",
        {"username": username, "password": password},
        expected_status=200,
    )
    return response["accessToken"]


def header_value(response, header_name):
    lowered = header_name.lower()
    for key, value in response.headers.items():
        if key.lower() == lowered:
            return value
    return None


UTC_PLUS_8 = timezone(timedelta(hours=8))


def iso_offset(dt):
    return dt.astimezone(UTC_PLUS_8).replace(microsecond=0).isoformat()


def active_assignment_window(now):
    return iso_offset(now - timedelta(days=1)), iso_offset(now + timedelta(days=7))


def active_term_window(now):
    start = (now - timedelta(days=30)).date().isoformat()
    end = (now + timedelta(days=90)).date().isoformat()
    return start, end


def main():
    parser = argparse.ArgumentParser(description="真实运行权限矩阵验证")
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--admin-username", default="bootstrap-admin")
    parser.add_argument("--admin-password", default="BootstrapAdmin!123")
    args = parser.parse_args()

    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    api = ApiClient(args.base_url)
    results = []
    run_suffix = str(int(time.time()))
    now = datetime.now(UTC_PLUS_8)
    active_open_at, active_due_at = active_assignment_window(now)
    term_start_date, term_end_date = active_term_window(now)
    offering_start_at = iso_offset(now - timedelta(days=30))
    offering_end_at = iso_offset(now + timedelta(days=90))

    def must(action_name, func):
        return check(results, action_name, func)

    must("readiness-up", lambda: {
        "path": "/actuator/health/readiness",
        "status": api.request("GET", "/actuator/health/readiness", expected_status=200).status,
    })
    must("openapi-public", lambda: {
        "path": "/v3/api-docs",
        "status": api.request("GET", "/v3/api-docs", expected_status=200).status,
    })
    must("swagger-ui-public", lambda: {
        "path": "/swagger-ui/index.html",
        "status": api.request("GET", "/swagger-ui/index.html", expected_status=200).status,
    })

    failure = None
    summary = None
    try:
        admin_username = args.admin_username
        admin_password = args.admin_password
        admin_token = login(api, admin_username, admin_password)
        append_result(results, "bootstrap-admin-login", True, username=admin_username)

        notification_stream = api.open_stream(
            "/api/v1/me/notifications/stream",
            token=admin_token,
            expected_status=200,
        )
        notification_stream_content_type = header_value(notification_stream, "Content-Type") or ""
        append_result(
            results,
            "notification-stream-handshake",
            notification_stream.status == 200 and "text/event-stream" in notification_stream_content_type,
            path="/api/v1/me/notifications/stream",
            status=notification_stream.status,
            contentType=notification_stream_content_type,
        )
        if notification_stream.status != 200 or "text/event-stream" not in notification_stream_content_type:
            raise RuntimeError("通知 SSE stream 未返回 200 或 text/event-stream")

        org_tree = api.get_json("/api/v1/admin/org-units/tree", token=admin_token)
        school_unit_id = org_tree[0]["id"]

        college = api.post_json(
            "/api/v1/admin/org-units",
            {
                "name": f"Engineering {run_suffix}",
                "code": f"COL-ENG-{run_suffix}",
                "type": "COLLEGE",
                "parentId": school_unit_id,
                "sortOrder": 1,
            },
            token=admin_token,
            expected_status=201,
        )
        college_id = college["id"]
        append_result(results, "create-college", True, collegeId=college_id)

        eng_admin = create_user(
            api,
            admin_token,
            {
                "username": f"eng-admin-{run_suffix}",
                "displayName": "Engineering Admin",
                "email": f"eng-admin-{run_suffix}@example.edu",
                "password": "Password123",
                "primaryOrgUnitId": college_id,
                "identityAssignments": [{"roleCode": "COLLEGE_ADMIN", "scopeOrgUnitId": college_id}],
                "accountStatus": "ACTIVE",
            },
        )
        teacher = create_user(
            api,
            admin_token,
            {
                "username": f"teacher-main-{run_suffix}",
                "displayName": "Teacher Main",
                "email": f"teacher-main-{run_suffix}@example.edu",
                "password": "Password123",
                "primaryOrgUnitId": college_id,
                "accountStatus": "ACTIVE",
            },
        )
        class_instructor = create_user(
            api,
            admin_token,
            {
                "username": f"class-instructor-{run_suffix}",
                "displayName": "Class Instructor",
                "email": f"class-instructor-{run_suffix}@example.edu",
                "password": "Password123",
                "primaryOrgUnitId": college_id,
                "accountStatus": "ACTIVE",
            },
        )
        offering_ta = create_user(
            api,
            admin_token,
            {
                "username": f"offering-ta-{run_suffix}",
                "displayName": "Offering TA",
                "email": f"offering-ta-{run_suffix}@example.edu",
                "password": "Password123",
                "primaryOrgUnitId": college_id,
                "accountStatus": "ACTIVE",
            },
        )
        class_ta = create_user(
            api,
            admin_token,
            {
                "username": f"class-ta-{run_suffix}",
                "displayName": "Class TA",
                "email": f"class-ta-{run_suffix}@example.edu",
                "password": "Password123",
                "primaryOrgUnitId": college_id,
                "accountStatus": "ACTIVE",
            },
        )
        student_a1 = create_user(
            api,
            admin_token,
            {
                "username": f"student-a1-{run_suffix}",
                "displayName": "Student A1",
                "email": f"student-a1-{run_suffix}@example.edu",
                "password": "Password123",
                "primaryOrgUnitId": college_id,
                "accountStatus": "ACTIVE",
            },
        )
        student_a2 = create_user(
            api,
            admin_token,
            {
                "username": f"student-a2-{run_suffix}",
                "displayName": "Student A2",
                "email": f"student-a2-{run_suffix}@example.edu",
                "password": "Password123",
                "primaryOrgUnitId": college_id,
                "accountStatus": "ACTIVE",
            },
        )
        append_result(
            results,
            "create-users",
            True,
            userIds={
                "engAdmin": eng_admin["id"],
                "teacher": teacher["id"],
                "classInstructor": class_instructor["id"],
                "offeringTa": offering_ta["id"],
                "classTa": class_ta["id"],
                "studentA1": student_a1["id"],
                "studentA2": student_a2["id"],
            },
        )

        eng_admin_token = login(api, eng_admin["username"], "Password123")
        teacher_token = login(api, teacher["username"], "Password123")
        offering_ta_old_token = login(api, offering_ta["username"], "Password123")

        term = api.post_json(
            "/api/v1/admin/academic-terms",
            {
                "termCode": f"2026-SPRING-{run_suffix}",
                "termName": "2026 春季学期",
                "schoolYear": "2025-2026",
                "semester": "SPRING",
                "startDate": term_start_date,
                "endDate": term_end_date,
            },
            token=admin_token,
            expected_status=201,
        )
        catalog = api.post_json(
            "/api/v1/admin/course-catalogs",
            {
                "courseCode": f"CS101-{run_suffix}",
                "courseName": "数据结构",
                "courseType": "REQUIRED",
                "credit": 3.0,
                "totalHours": 48,
                "departmentUnitId": college_id,
                "description": "真实权限矩阵验证课程",
            },
            token=eng_admin_token,
            expected_status=201,
        )
        offering = api.post_json(
            "/api/v1/admin/course-offerings",
            {
                "catalogId": catalog["id"],
                "termId": term["id"],
                "offeringCode": f"CS101-2026SP-{run_suffix}",
                "offeringName": "数据结构（权限矩阵）",
                "primaryCollegeUnitId": college_id,
                "secondaryCollegeUnitIds": [],
                "deliveryMode": "HYBRID",
                "language": "ZH",
                "capacity": 120,
                "instructorUserIds": [teacher["id"]],
                "startAt": offering_start_at,
                "endAt": offering_end_at,
            },
            token=eng_admin_token,
            expected_status=201,
        )
        append_result(results, "create-offering", True, offeringId=offering["id"])

        class_a1 = api.post_json(
            f"/api/v1/teacher/course-offerings/{offering['id']}/classes",
            {
                "classCode": f"A1-{run_suffix}",
                "className": "A1班",
                "entryYear": 2024,
                "capacity": 60,
                "scheduleSummary": "周二 1-2 节",
            },
            token=teacher_token,
            expected_status=201,
        )
        class_a2 = api.post_json(
            f"/api/v1/teacher/course-offerings/{offering['id']}/classes",
            {
                "classCode": f"A2-{run_suffix}",
                "className": "A2班",
                "entryYear": 2024,
                "capacity": 60,
                "scheduleSummary": "周三 3-4 节",
            },
            token=teacher_token,
            expected_status=201,
        )
        append_result(results, "create-classes", True, classIds=[class_a1["id"], class_a2["id"]])

        graph_question = api.post_json(
            f"/api/v1/teacher/course-offerings/{offering['id']}/question-bank/questions",
            {
                "title": f"图论题 {run_suffix}",
                "prompt": "请说明 Dijkstra 的适用前提。",
                "questionType": "SHORT_ANSWER",
                "defaultScore": 15,
                "categoryName": "图论",
                "tags": ["graph"],
            },
            token=teacher_token,
            expected_status=201,
        )
        tree_question = api.post_json(
            f"/api/v1/teacher/course-offerings/{offering['id']}/question-bank/questions",
            {
                "title": f"树结构题 {run_suffix}",
                "prompt": "写出完全二叉树的层次性质。",
                "questionType": "SHORT_ANSWER",
                "defaultScore": 10,
                "categoryName": "树结构",
                "tags": ["tree"],
            },
            token=teacher_token,
            expected_status=201,
        )
        paper_assignment = api.post_json(
            f"/api/v1/teacher/course-offerings/{offering['id']}/assignments",
            {
                "title": f"试卷重组任务 {run_suffix}",
                "description": "用于验证 dedicated paper endpoint",
                "teachingClassId": class_a1["id"],
                "openAt": active_open_at,
                "dueAt": active_due_at,
                "maxSubmissions": 2,
                "paper": {
                    "sections": [
                        {
                            "title": "原试卷",
                            "questions": [{"bankQuestionId": tree_question["id"], "score": 10}],
                        }
                    ]
                },
            },
            token=teacher_token,
            expected_status=201,
        )
        replaced_paper = api.put_json(
            f"/api/v1/teacher/assignments/{paper_assignment['id']}/paper",
            {
                "sections": [
                    {
                        "title": "重组后的试卷",
                        "description": "仅保留一题",
                        "questions": [{"bankQuestionId": graph_question["id"], "score": 30}],
                    }
                ]
            },
            token=teacher_token,
            expected_status=200,
        )
        append_result(
            results,
            "replace-assignment-paper-via-dedicated-endpoint",
            replaced_paper.get("paper", {}).get("sectionCount") == 1
            and replaced_paper.get("paper", {}).get("questionCount") == 1
            and replaced_paper.get("paper", {}).get("sections", [{}])[0].get("questions", [{}])[0].get("sourceQuestionId")
            == graph_question["id"],
            assignmentId=paper_assignment["id"],
            sourceQuestionId=replaced_paper.get("paper", {}).get("sections", [{}])[0].get("questions", [{}])[0].get("sourceQuestionId"),
        )
        if (
            replaced_paper.get("paper", {}).get("sectionCount") != 1
            or replaced_paper.get("paper", {}).get("questionCount") != 1
            or replaced_paper.get("paper", {}).get("sections", [{}])[0].get("questions", [{}])[0].get("sourceQuestionId")
            != graph_question["id"]
        ):
            raise RuntimeError("assignment paper dedicated endpoint 返回结果不符合预期")

        def add_member(user_id, role_code, teaching_class_id):
            return api.post_json(
                f"/api/v1/teacher/course-offerings/{offering['id']}/members/batch",
                {
                    "members": [
                        {
                            "userId": user_id,
                            "memberRole": role_code,
                            "teachingClassId": teaching_class_id,
                            "remark": "realrun",
                        }
                    ]
                },
                token=teacher_token,
                expected_status=200,
            )

        add_member(class_instructor["id"], "CLASS_INSTRUCTOR", class_a1["id"])
        add_member(offering_ta["id"], "OFFERING_TA", None)
        add_member(class_ta["id"], "TA", class_a1["id"])
        add_member(student_a1["id"], "STUDENT", class_a1["id"])
        add_member(student_a2["id"], "STUDENT", class_a2["id"])
        append_result(results, "add-course-members", True)

        grade_corrector_group = api.post_json(
            "/api/v1/admin/auth/groups",
            {
                "templateCode": "grade-corrector",
                "scopeType": "OFFERING",
                "scopeRefId": offering["id"],
                "displayName": f"纠错组-{run_suffix}",
            },
            token=admin_token,
            expected_status=201,
        )
        api.post_json(
            f"/api/v1/admin/auth/groups/{grade_corrector_group['id']}/members",
            {"userId": offering_ta["id"]},
            token=admin_token,
            expected_status=201,
        )
        old_me = api.request("GET", "/api/v1/auth/me", token=offering_ta_old_token, expected_status=401)
        append_result(
            results,
            "auth-group-membership-revokes-old-session",
            old_me.status == 401,
            actualStatus=old_me.status,
            path="/api/v1/auth/me",
        )
        if old_me.status != 401:
            raise RuntimeError("组成员变更后旧会话未失效")

        explain_grade_override = api.get_json(
            "/api/v1/admin/auth/explain?userId=%s&permission=GRADE_OVERRIDE&scopeType=OFFERING&scopeRefId=%s"
            % (offering_ta["id"], offering["id"]),
            token=admin_token,
            expected_status=200,
        )
        append_result(
            results,
            "authz-explain-grade-override-after-group-grant",
            explain_grade_override["allowed"] is True,
            path="/api/v1/admin/auth/explain",
            allowed=explain_grade_override["allowed"],
        )
        if explain_grade_override["allowed"] is not True:
            raise RuntimeError("auth explain 未反映 grade-corrector 授权")

        explain_class_ta_offering_scope = api.get_json(
            "/api/v1/admin/auth/explain?userId=%s&permission=SUBMISSION_READ_OFFERING&scopeType=OFFERING&scopeRefId=%s"
            % (class_ta["id"], offering["id"]),
            token=admin_token,
            expected_status=200,
        )
        append_result(
            results,
            "authz-explain-class-ta-cannot-read-offering-scope",
            explain_class_ta_offering_scope["allowed"] is False,
            path="/api/v1/admin/auth/explain",
            allowed=explain_class_ta_offering_scope["allowed"],
        )
        if explain_class_ta_offering_scope["allowed"] is not False:
            raise RuntimeError("class TA 被错误授予 offering 级 submission.read")

        class_instructor_token = login(api, class_instructor["username"], "Password123")
        offering_ta_token = login(api, offering_ta["username"], "Password123")
        class_ta_token = login(api, class_ta["username"], "Password123")
        student_a1_token = login(api, student_a1["username"], "Password123")
        student_a2_token = login(api, student_a2["username"], "Password123")

        own_assignment = api.post_json(
            f"/api/v1/teacher/course-offerings/{offering['id']}/assignments",
            {
                "title": f"A1 任务 {run_suffix}",
                "description": "A1 班作业",
                "teachingClassId": class_a1["id"],
                "openAt": active_open_at,
                "dueAt": active_due_at,
                "maxSubmissions": 2,
            },
            token=class_instructor_token,
            expected_status=201,
        )
        append_result(results, "class-instructor-create-own-class-assignment", True, assignmentId=own_assignment["id"])

        cross_class_response = api.request(
            "POST",
            f"/api/v1/teacher/course-offerings/{offering['id']}/assignments",
            token=class_instructor_token,
            json_body={
                "title": f"A2 越权任务 {run_suffix}",
                "description": "不应允许",
                "teachingClassId": class_a2["id"],
                "openAt": active_open_at,
                "dueAt": active_due_at,
                "maxSubmissions": 2,
            },
            expected_status=403,
        )
        append_result(
            results,
            "class-instructor-cannot-create-sibling-class-assignment",
            cross_class_response.status == 403,
            path=f"/api/v1/teacher/course-offerings/{offering['id']}/assignments",
            actualStatus=cross_class_response.status,
            bodySnippet=cross_class_response.text()[:400],
        )
        if cross_class_response.status != 403:
            raise RuntimeError("class instructor 越权创建兄弟班作业未被拒绝")

        teacher_assignment_a1 = api.post_json(
            f"/api/v1/teacher/course-offerings/{offering['id']}/assignments",
            {
                "title": f"提交验证 A1 {run_suffix}",
                "description": "A1 提交验证",
                "teachingClassId": class_a1["id"],
                "openAt": active_open_at,
                "dueAt": active_due_at,
                "maxSubmissions": 2,
            },
            token=teacher_token,
            expected_status=201,
        )
        teacher_assignment_a2 = api.post_json(
            f"/api/v1/teacher/course-offerings/{offering['id']}/assignments",
            {
                "title": f"提交验证 A2 {run_suffix}",
                "description": "A2 提交验证",
                "teachingClassId": class_a2["id"],
                "openAt": active_open_at,
                "dueAt": active_due_at,
                "maxSubmissions": 2,
            },
            token=teacher_token,
            expected_status=201,
        )
        api.post_json(
            f"/api/v1/teacher/assignments/{teacher_assignment_a1['id']}/publish",
            {},
            token=teacher_token,
            expected_status=200,
        )
        api.post_json(
            f"/api/v1/teacher/assignments/{teacher_assignment_a2['id']}/publish",
            {},
            token=teacher_token,
            expected_status=200,
        )

        submission_a1 = api.post_json(
            f"/api/v1/me/assignments/{teacher_assignment_a1['id']}/submissions",
            {"contentText": "submission-a1", "artifactIds": []},
            token=student_a1_token,
            expected_status=201,
        )
        submission_a2 = api.post_json(
            f"/api/v1/me/assignments/{teacher_assignment_a2['id']}/submissions",
            {"contentText": "submission-a2", "artifactIds": []},
            token=student_a2_token,
            expected_status=201,
        )
        append_result(
            results,
            "students-create-submissions",
            True,
            submissionIds=[submission_a1["id"], submission_a2["id"]],
        )

        offering_ta_a1 = api.request(
            "GET",
            f"/api/v1/teacher/submissions/{submission_a1['id']}",
            token=offering_ta_token,
            expected_status=200,
        )
        offering_ta_a2 = api.request(
            "GET",
            f"/api/v1/teacher/submissions/{submission_a2['id']}",
            token=offering_ta_token,
            expected_status=200,
        )
        append_result(
            results,
            "offering-ta-can-read-both-class-submissions",
            offering_ta_a1.status == 200 and offering_ta_a2.status == 200,
            statuses=[offering_ta_a1.status, offering_ta_a2.status],
        )
        if offering_ta_a1.status != 200 or offering_ta_a2.status != 200:
            raise RuntimeError("offering TA 未能读取全部班级提交")

        class_ta_a1 = api.request(
            "GET",
            f"/api/v1/teacher/submissions/{submission_a1['id']}",
            token=class_ta_token,
            expected_status=200,
        )
        class_ta_a2 = api.request(
            "GET",
            f"/api/v1/teacher/submissions/{submission_a2['id']}",
            token=class_ta_token,
            expected_status=403,
        )
        append_result(
            results,
            "class-ta-stays-class-scoped-on-submission-read",
            class_ta_a1.status == 200 and class_ta_a2.status == 403,
            statuses=[class_ta_a1.status, class_ta_a2.status],
        )
        if class_ta_a1.status != 200 or class_ta_a2.status != 403:
            raise RuntimeError("class TA 提交读取边界不符合预期")

        class_gradebook_export = api.request(
            "GET",
            f"/api/v1/teacher/teaching-classes/{class_a1['id']}/gradebook/export",
            token=class_ta_token,
            expected_status=200,
            headers={"Accept": "text/csv"},
        )
        offering_gradebook_for_class_ta = api.request(
            "GET",
            f"/api/v1/teacher/course-offerings/{offering['id']}/gradebook/export",
            token=class_ta_token,
            expected_status=403,
            headers={"Accept": "text/csv"},
        )
        offering_gradebook_for_offering_ta = api.request(
            "GET",
            f"/api/v1/teacher/course-offerings/{offering['id']}/gradebook/export",
            token=offering_ta_token,
            expected_status=200,
            headers={"Accept": "text/csv"},
        )
        append_result(
            results,
            "gradebook-export-respects-class-vs-offering-scope",
            class_gradebook_export.status == 200
            and offering_gradebook_for_class_ta.status == 403
            and offering_gradebook_for_offering_ta.status == 200,
            statuses=[
                class_gradebook_export.status,
                offering_gradebook_for_class_ta.status,
                offering_gradebook_for_offering_ta.status,
            ],
            classExportBytes=len(class_gradebook_export.body),
            offeringExportBytes=len(offering_gradebook_for_offering_ta.body),
        )
        if (
            class_gradebook_export.status != 200
            or offering_gradebook_for_class_ta.status != 403
            or offering_gradebook_for_offering_ta.status != 200
        ):
            raise RuntimeError("成绩册导出作用域边界不符合预期")

        student_forbidden = api.request(
            "GET",
            f"/api/v1/teacher/submissions/{submission_a1['id']}",
            token=student_a1_token,
            expected_status=403,
        )
        append_result(
            results,
            "student-cannot-read-teacher-submission-endpoint",
            student_forbidden.status == 403,
            actualStatus=student_forbidden.status,
            bodySnippet=student_forbidden.text()[:400],
        )
        if student_forbidden.status != 403:
            raise RuntimeError("学生访问教师提交接口未被拒绝")

        denied_audit_logs = api.get_json(
            "/api/v1/admin/audit-logs?action=AUTHZ_DENIED&page=1&pageSize=20",
            token=admin_token,
            expected_status=200,
        )
        denied_entries = denied_audit_logs.get("items", [])
        matching_entry = next(
            (
                item
                for item in denied_entries
                if item.get("actorUserId") == student_a1["id"]
                and item.get("targetType") == "AUTHORIZATION"
                and item.get("targetId") == f"/api/v1/teacher/submissions/{submission_a1['id']}"
            ),
            None,
        )
        append_result(
            results,
            "authz-denied-audit-visible-to-school-admin",
            matching_entry is not None,
            deniedCount=len(denied_entries),
            matchedTargetId=None if matching_entry is None else matching_entry.get("targetId"),
        )
        if matching_entry is None:
            raise RuntimeError("未在审计日志中找到 AUTHZ_DENIED 记录")
    except Exception as exc:  # pylint: disable=broad-except
        failure = str(exc)

    passed_count = sum(1 for item in results if item["passed"])
    failed_items = [item for item in results if not item["passed"]]
    if failure is not None and not failed_items:
        append_result(results, "script-runtime-failure", False, error=failure)
        failed_items = [item for item in results if not item["passed"]]
    summary = {
        "baseUrl": args.base_url,
        "adminUsername": args.admin_username,
        "checkCount": len(results),
        "passedCount": passed_count,
        "failedCount": len(failed_items),
        "status": "passed" if not failed_items else "failed",
        "checks": [item["name"] for item in results],
    }

    (out_dir / "results.json").write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    (out_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")

    print(json.dumps(summary, ensure_ascii=False, indent=2))
    if failed_items:
        sys.exit(1)


if __name__ == "__main__":
    main()
