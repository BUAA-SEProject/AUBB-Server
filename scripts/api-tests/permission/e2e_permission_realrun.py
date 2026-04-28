#!/usr/bin/env python3
"""真实 HTTP API 权限系统 E2E 测试。"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from typing import Any
from urllib.parse import urlencode


ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "scripts" / "realrun"))

from verify_authz_matrix import ApiClient, HttpFailure  # pylint: disable=wrong-import-position


UTC_PLUS_8 = timezone(timedelta(hours=8))
DEFAULT_PASSWORD = "Password123"
AUTHZ_DENIED_ACTION = "AUTHZ_DENIED"


@dataclass
class CaseResult:
    case_id: str
    title: str
    actor: str
    http_method: str
    api_path: str
    expected_status: list[int]
    actual_status: int | None
    expected_result: str
    actual_result: str
    passed: bool
    expected_filtered_fields: list[str] = field(default_factory=list)
    actual_filtered_fields: list[str] = field(default_factory=list)
    audit_required: bool = False
    audit_passed: bool | None = None
    audit_detail: str | None = None
    side_effect_check: str | None = None
    notes: list[str] = field(default_factory=list)


def iso_offset(moment: datetime) -> str:
    return moment.astimezone(UTC_PLUS_8).replace(microsecond=0).isoformat()


def term_window(start_day_offset: int, end_day_offset: int) -> tuple[str, str]:
    today = datetime.now(UTC_PLUS_8).date()
    return (
        (today + timedelta(days=start_day_offset)).isoformat(),
        (today + timedelta(days=end_day_offset)).isoformat(),
    )


def assignment_window(start_day_offset: int, end_day_offset: int) -> tuple[str, str]:
    now = datetime.now(UTC_PLUS_8)
    return iso_offset(now + timedelta(days=start_day_offset)), iso_offset(now + timedelta(days=end_day_offset))


def ensure_dir(path: Path) -> None:
    path.mkdir(parents=True, exist_ok=True)


def api_get(api: ApiClient, path: str, token: str, expected_status: int | list[int] = 200) -> Any:
    return api.request("GET", path, token=token, expected_status=expected_status)


def api_post(api: ApiClient, path: str, token: str, body: dict[str, Any], expected_status: int | list[int]) -> Any:
    return api.request("POST", path, token=token, json_body=body, expected_status=expected_status)


def api_put(api: ApiClient, path: str, token: str, body: dict[str, Any], expected_status: int | list[int]) -> Any:
    return api.request("PUT", path, token=token, json_body=body, expected_status=expected_status)


def api_patch(api: ApiClient, path: str, token: str, body: dict[str, Any], expected_status: int | list[int]) -> Any:
    return api.request("PATCH", path, token=token, json_body=body, expected_status=expected_status)


def response_json(response: Any) -> Any:
    if not response.body:
        return None
    return response.json()


def query_string(params: dict[str, Any]) -> str:
    filtered = {key: value for key, value in params.items() if value is not None}
    return urlencode(filtered, doseq=True)


def sql_exec(statement: str) -> str:
    result = subprocess.run(
        [
            "docker",
            "compose",
            "exec",
            "-T",
            "postgres",
            "psql",
            "-U",
            "aubb",
            "-d",
            "aubb",
            "-v",
            "ON_ERROR_STOP=1",
            "-At",
            "-F",
            ",",
            "-c",
            statement,
        ],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def sql_literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def sql_long(statement: str) -> int | None:
    output = sql_exec(statement)
    if not output:
        return None
    first = output.splitlines()[0].strip()
    return int(first) if first else None


def sql_exists(statement: str) -> bool:
    output = sql_exec(statement)
    return output.strip().lower() == "t"


def lookup_org_unit_id(code: str) -> int | None:
    return sql_long(f"SELECT id FROM org_units WHERE code = {sql_literal(code)} ORDER BY id ASC LIMIT 1;")


def lookup_user_id(username: str) -> int | None:
    return sql_long(
        "SELECT users.id "
        "FROM users "
        "LEFT JOIN academic_profiles ON academic_profiles.user_id = users.id "
        f"WHERE lower(users.username) = lower({sql_literal(username)}) "
        f"   OR lower(academic_profiles.academic_id) = lower({sql_literal(username)}) "
        "ORDER BY users.id ASC LIMIT 1;"
    )


def lookup_term_id(term_code: str) -> int | None:
    return sql_long(
        f"SELECT id FROM academic_terms WHERE lower(term_code) = lower({sql_literal(term_code)}) ORDER BY id ASC LIMIT 1;"
    )


def lookup_course_catalog_id(course_code: str) -> int | None:
    return sql_long(
        f"SELECT id FROM course_catalogs WHERE lower(course_code) = lower({sql_literal(course_code)}) ORDER BY id ASC LIMIT 1;"
    )


def lookup_offering_id(offering_code: str) -> int | None:
    return sql_long(
        f"SELECT id FROM course_offerings WHERE lower(offering_code) = lower({sql_literal(offering_code)}) ORDER BY id ASC LIMIT 1;"
    )


def lookup_teaching_class_id(offering_id: int, class_code: str) -> int | None:
    return sql_long(
        "SELECT id FROM teaching_classes "
        f"WHERE offering_id = {offering_id} AND lower(class_code) = lower({sql_literal(class_code)}) "
        "ORDER BY id ASC LIMIT 1;"
    )


def lookup_question_id(offering_id: int, title: str) -> int | None:
    return sql_long(
        "SELECT id FROM question_bank_questions "
        f"WHERE offering_id = {offering_id} AND title = {sql_literal(title)} "
        "ORDER BY id ASC LIMIT 1;"
    )


def lookup_assignment_id(offering_id: int, title: str) -> int | None:
    return sql_long(
        "SELECT id FROM assignments "
        f"WHERE offering_id = {offering_id} AND title = {sql_literal(title)} "
        "ORDER BY id ASC LIMIT 1;"
    )


def lookup_submission_id(assignment_id: int, submitter_user_id: int) -> int | None:
    return sql_long(
        "SELECT id FROM submissions "
        f"WHERE assignment_id = {assignment_id} AND submitter_user_id = {submitter_user_id} "
        "ORDER BY id ASC LIMIT 1;"
    )


def login(api: ApiClient, username: str, password: str = DEFAULT_PASSWORD) -> dict[str, Any]:
    response = api.post_json(
        "/api/v1/auth/login",
        {"username": username, "password": password},
        expected_status=200,
    )
    return response


def get_access_token(login_result: dict[str, Any]) -> str:
    return login_result["accessToken"]


def latest_audit_id(api: ApiClient, admin_token: str) -> int:
    payload = api.get_json("/api/v1/admin/audit-logs?page=1&pageSize=1", token=admin_token, expected_status=200)
    items = payload.get("items", [])
    return int(items[0]["id"]) if items else 0


def fetch_new_audits(api: ApiClient, admin_token: str, after_id: int) -> list[dict[str, Any]]:
    payload = api.get_json("/api/v1/admin/audit-logs?page=1&pageSize=200", token=admin_token, expected_status=200)
    return [item for item in payload.get("items", []) if int(item["id"]) > after_id]


def fetch_authz_denied_audits(api: ApiClient, admin_token: str, after_id: int) -> list[dict[str, Any]]:
    return [item for item in fetch_new_audits(api, admin_token, after_id) if item.get("action") == AUTHZ_DENIED_ACTION]


def first_question_id(assignment_view: dict[str, Any]) -> int:
    return int(assignment_view["paper"]["sections"][0]["questions"][0]["id"])


def first_answer_id(submission_view: dict[str, Any]) -> int:
    return int(submission_view["answers"][0]["id"])


def create_user(
    api: ApiClient,
    admin_token: str,
    username: str,
    display_name: str,
    email: str,
    academic_id: str,
    identity_type: str,
    primary_org_unit_id: int,
    *,
    identity_assignments: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    payload = {
        "username": username,
        "displayName": display_name,
        "email": email,
        "password": DEFAULT_PASSWORD,
        "primaryOrgUnitId": primary_org_unit_id,
        "accountStatus": "ACTIVE",
        "academicProfile": {
            "academicId": academic_id,
            "realName": display_name,
            "identityType": identity_type,
            "profileStatus": "ACTIVE",
        },
    }
    if identity_assignments:
        payload["identityAssignments"] = identity_assignments
    return api.post_json("/api/v1/admin/users", payload, token=admin_token, expected_status=201)


def make_short_answer_question(api: ApiClient, teacher_token: str, offering_id: int, title: str, prompt: str) -> dict[str, Any]:
    return api.post_json(
        f"/api/v1/teacher/course-offerings/{offering_id}/question-bank/questions",
        {
            "title": title,
            "prompt": prompt,
            "questionType": "SHORT_ANSWER",
            "defaultScore": 100,
            "categoryName": "Permission",
            "tags": ["permission", "e2e"],
        },
        token=teacher_token,
        expected_status=201,
    )


def make_programming_question(api: ApiClient, teacher_token: str, offering_id: int, title: str) -> dict[str, Any]:
    return api.post_json(
        f"/api/v1/teacher/course-offerings/{offering_id}/question-bank/questions",
        {
            "title": title,
            "prompt": "实现两个整数求和，保留隐藏测试与自定义判题脚本。",
            "questionType": "PROGRAMMING",
            "defaultScore": 100,
            "categoryName": "Programming",
            "tags": ["programming", "hidden"],
            "config": {
                "supportedLanguages": ["PYTHON3"],
                "maxFileCount": 1,
                "maxFileSizeMb": 1,
                "acceptedExtensions": [".py"],
                "allowMultipleFiles": False,
                "allowSampleRun": True,
                "sampleStdinText": "1 2\n",
                "sampleExpectedStdout": "3\n",
                "templateEntryFilePath": "main.py",
                "templateDirectories": [],
                "templateFiles": [
                    {
                        "path": "main.py",
                        "content": "a, b = map(int, input().split())\nprint(a + b)\n",
                    }
                ],
                "timeLimitMs": 1000,
                "memoryLimitMb": 128,
                "outputLimitKb": 64,
                "compileArgs": [],
                "runArgs": [],
                "judgeMode": "CUSTOM_SCRIPT",
                "customJudgeScript": (
                    "import sys\n"
                    "actual = sys.stdin.readline().strip()\n"
                    "expected = sys.stdin.readline().strip()\n"
                    "sys.exit(0 if actual == expected else 1)\n"
                ),
                "referenceAnswer": "3",
                "judgeCases": [
                    {"stdinText": "1 2\n", "expectedStdout": "3\n", "score": 40},
                    {"stdinText": "10 32\n", "expectedStdout": "42\n", "score": 60},
                ],
            },
        },
        token=teacher_token,
        expected_status=201,
    )


def create_assignment(
    api: ApiClient,
    teacher_token: str,
    offering_id: int,
    title: str,
    question_id: int,
    score: int,
    *,
    teaching_class_id: int | None,
    open_at: str,
    due_at: str,
) -> dict[str, Any]:
    body = {
        "title": title,
        "description": title,
        "openAt": open_at,
        "dueAt": due_at,
        "maxSubmissions": 3,
        "paper": {
            "sections": [
                {
                    "title": "Section-1",
                    "questions": [{"bankQuestionId": question_id, "score": score}],
                }
            ]
        },
    }
    if teaching_class_id is not None:
        body["teachingClassId"] = teaching_class_id
    return api.post_json(
        f"/api/v1/teacher/course-offerings/{offering_id}/assignments",
        body,
        token=teacher_token,
        expected_status=201,
    )


def publish_assignment(api: ApiClient, teacher_token: str, assignment_id: int) -> dict[str, Any]:
    return api.post_json(
        f"/api/v1/teacher/assignments/{assignment_id}/publish",
        {},
        token=teacher_token,
        expected_status=200,
    )


def close_assignment(api: ApiClient, teacher_token: str, assignment_id: int) -> dict[str, Any]:
    return api.post_json(
        f"/api/v1/teacher/assignments/{assignment_id}/close",
        {},
        token=teacher_token,
        expected_status=200,
    )


def create_submission(
    api: ApiClient,
    user_token: str,
    assignment_id: int,
    assignment_question_id: int,
    answer_text: str,
) -> dict[str, Any]:
    return api.post_json(
        f"/api/v1/me/assignments/{assignment_id}/submissions",
        {
            "answers": [
                {
                    "assignmentQuestionId": assignment_question_id,
                    "answerText": answer_text,
                }
            ]
        },
        token=user_token,
        expected_status=201,
    )


def grade_answer(
    api: ApiClient,
    teacher_token: str,
    submission_id: int,
    answer_id: int,
    score: int,
    feedback_text: str,
) -> dict[str, Any]:
    return api.post_json(
        f"/api/v1/teacher/submissions/{submission_id}/answers/{answer_id}/grade",
        {"score": score, "feedbackText": feedback_text},
        token=teacher_token,
        expected_status=200,
    )


def publish_assignment_grades(api: ApiClient, teacher_token: str, assignment_id: int) -> dict[str, Any]:
    return api.post_json(
        f"/api/v1/teacher/assignments/{assignment_id}/grades/publish",
        {},
        token=teacher_token,
        expected_status=200,
    )


def teacher_assignment_detail(api: ApiClient, teacher_token: str, assignment_id: int) -> dict[str, Any]:
    return api.get_json(
        f"/api/v1/teacher/assignments/{assignment_id}",
        token=teacher_token,
        expected_status=200,
    )


def teacher_submission_detail(api: ApiClient, teacher_token: str, submission_id: int) -> dict[str, Any]:
    return api.get_json(
        f"/api/v1/teacher/submissions/{submission_id}",
        token=teacher_token,
        expected_status=200,
    )


def list_assignment_submissions_count(api: ApiClient, teacher_token: str, assignment_id: int) -> int:
    payload = api.get_json(
        f"/api/v1/teacher/assignments/{assignment_id}/submissions?page=1&pageSize=50",
        token=teacher_token,
        expected_status=200,
    )
    return int(payload["total"])


@dataclass
class FixtureContext:
    api: ApiClient
    admin_login: dict[str, Any]
    admin_token: str
    ids: dict[str, int] = field(default_factory=dict)
    views: dict[str, dict[str, Any]] = field(default_factory=dict)
    tokens: dict[str, str] = field(default_factory=dict)
    login_results: dict[str, dict[str, Any]] = field(default_factory=dict)
    creations: dict[str, list[str]] = field(
        default_factory=lambda: {"bootstrap": [], "api": [], "sql": [], "reused": [], "reset": []}
    )


def refresh_login(context: FixtureContext, login_key: str, username: str) -> str:
    login_result = login(context.api, username)
    context.login_results[login_key] = login_result
    context.tokens[login_key] = get_access_token(login_result)
    return context.tokens[login_key]


def record_fixture(context: FixtureContext, channel: str, label: str) -> None:
    items = context.creations[channel]
    if label not in items:
        items.append(label)


def ensure_org_unit(
    context: FixtureContext,
    *,
    code: str,
    name: str,
    unit_type: str,
    parent_id: int,
    sort_order: int,
) -> int:
    existing_id = lookup_org_unit_id(code)
    if existing_id is not None:
        record_fixture(context, "reused", code)
        return existing_id
    created = context.api.post_json(
        "/api/v1/admin/org-units",
        {"name": name, "code": code, "type": unit_type, "parentId": parent_id, "sortOrder": sort_order},
        token=context.admin_token,
        expected_status=201,
    )
    record_fixture(context, "api", code)
    return int(created["id"])


def ensure_user(
    context: FixtureContext,
    *,
    username: str,
    display_name: str,
    email: str,
    academic_id: str,
    identity_type: str,
    primary_org_unit_id: int,
    identity_assignments: list[dict[str, Any]] | None = None,
) -> int:
    existing_id = lookup_user_id(username)
    if existing_id is None:
        view = create_user(
            context.api,
            context.admin_token,
            username,
            display_name,
            email,
            academic_id,
            identity_type,
            primary_org_unit_id,
            identity_assignments=identity_assignments,
        )
        record_fixture(context, "api", username)
        return int(view["id"])
    if identity_assignments is not None:
        context.api.put_json(
            f"/api/v1/admin/users/{existing_id}/identities",
            {"identityAssignments": identity_assignments},
            token=context.admin_token,
            expected_status=200,
        )
    record_fixture(context, "reused", username)
    return existing_id


def ensure_term(
    context: FixtureContext,
    *,
    term_code: str,
    term_name: str,
    school_year: str,
    semester: str,
    start_date: str,
    end_date: str,
) -> int:
    existing_id = lookup_term_id(term_code)
    if existing_id is not None:
        record_fixture(context, "reused", term_code)
        return existing_id
    created = context.api.post_json(
        "/api/v1/admin/academic-terms",
        {
            "termCode": term_code,
            "termName": term_name,
            "schoolYear": school_year,
            "semester": semester,
            "startDate": start_date,
            "endDate": end_date,
        },
        token=context.admin_token,
        expected_status=201,
    )
    record_fixture(context, "api", term_code)
    return int(created["id"])


def ensure_course_catalog(
    context: FixtureContext,
    *,
    actor_token: str,
    course_code: str,
    course_name: str,
    department_unit_id: int,
    description: str,
) -> int:
    existing_id = lookup_course_catalog_id(course_code)
    if existing_id is not None:
        record_fixture(context, "reused", course_code)
        return existing_id
    created = context.api.post_json(
        "/api/v1/admin/course-catalogs",
        {
            "courseCode": course_code,
            "courseName": course_name,
            "courseType": "REQUIRED",
            "credit": 4.0,
            "totalHours": 64,
            "departmentUnitId": department_unit_id,
            "description": description,
        },
        token=actor_token,
        expected_status=201,
    )
    record_fixture(context, "api", course_code)
    return int(created["id"])


def ensure_course_offering(
    context: FixtureContext,
    *,
    actor_token: str,
    catalog_id: int,
    term_id: int,
    offering_code: str,
    offering_name: str,
    primary_college_unit_id: int,
    instructor_user_ids: list[int],
    start_at: str,
    end_at: str,
) -> int:
    existing_id = lookup_offering_id(offering_code)
    if existing_id is not None:
        record_fixture(context, "reused", offering_code)
        return existing_id
    created = context.api.post_json(
        "/api/v1/admin/course-offerings",
        {
            "catalogId": catalog_id,
            "termId": term_id,
            "offeringCode": offering_code,
            "offeringName": offering_name,
            "primaryCollegeUnitId": primary_college_unit_id,
            "secondaryCollegeUnitIds": [],
            "deliveryMode": "HYBRID",
            "language": "ZH",
            "capacity": 120,
            "instructorUserIds": instructor_user_ids,
            "startAt": start_at,
            "endAt": end_at,
        },
        token=actor_token,
        expected_status=201,
    )
    record_fixture(context, "api", offering_code)
    return int(created["id"])


def ensure_teaching_class(
    context: FixtureContext,
    *,
    teacher_token: str,
    offering_id: int,
    class_code: str,
    schedule_summary: str,
) -> int:
    existing_id = lookup_teaching_class_id(offering_id, class_code)
    if existing_id is not None:
        record_fixture(context, "reused", class_code)
        return existing_id
    created = context.api.post_json(
        f"/api/v1/teacher/course-offerings/{offering_id}/classes",
        {
            "classCode": class_code,
            "className": class_code,
            "entryYear": 2024,
            "capacity": 60,
            "scheduleSummary": schedule_summary,
        },
        token=teacher_token,
        expected_status=201,
    )
    record_fixture(context, "api", class_code)
    return int(created["id"])


def ensure_question(
    context: FixtureContext,
    *,
    teacher_token: str,
    offering_id: int,
    title: str,
    creator,
) -> int:
    existing_id = lookup_question_id(offering_id, title)
    if existing_id is not None:
        record_fixture(context, "reused", title)
        return existing_id
    created = creator(context.api, teacher_token, offering_id, title)
    record_fixture(context, "api", title)
    return int(created["id"])


def ensure_assignment(
    context: FixtureContext,
    *,
    teacher_token: str,
    offering_id: int,
    title: str,
    question_id: int,
    teaching_class_id: int | None,
    open_at: str,
    due_at: str,
) -> tuple[int, dict[str, Any], bool]:
    existing_id = lookup_assignment_id(offering_id, title)
    if existing_id is None:
        created = create_assignment(
            context.api,
            teacher_token,
            offering_id,
            title,
            question_id,
            100,
            teaching_class_id=teaching_class_id,
            open_at=open_at,
            due_at=due_at,
        )
        record_fixture(context, "api", title)
        return int(created["id"]), created, True
    record_fixture(context, "reused", title)
    return existing_id, teacher_assignment_detail(context.api, teacher_token, existing_id), False


def ensure_submission(
    context: FixtureContext,
    *,
    actor_token: str,
    teacher_token: str,
    assignment_id: int,
    assignment_question_id: int,
    submitter_user_id: int,
    answer_text: str,
    label: str,
) -> tuple[int, dict[str, Any], bool]:
    existing_id = lookup_submission_id(assignment_id, submitter_user_id)
    if existing_id is None:
        created = create_submission(context.api, actor_token, assignment_id, assignment_question_id, answer_text)
        record_fixture(context, "api", label)
        return int(created["id"]), created, True
    record_fixture(context, "reused", label)
    return existing_id, teacher_submission_detail(context.api, teacher_token, existing_id), False


def reset_fixture_member_baseline(context: FixtureContext) -> None:
    offer_a = context.ids["Offer-A-2025F"]
    offer_b = context.ids["Offer-B-2026S"]
    delete_offer_a_user_ids = ", ".join(
        str(context.ids[key]) for key in ["U-TA2", "U-TAO1", "U-TAC1", "U-ST1", "U-ST2", "U-M1", "U-STX1"]
    )
    delete_offer_b_user_ids = ", ".join(str(context.ids[key]) for key in ["U-ST3", "U-M1"])
    sql_exec(
        f"""
        DELETE FROM course_members
        WHERE offering_id = {offer_a}
          AND user_id IN ({delete_offer_a_user_ids})
          AND member_role IN ('CLASS_INSTRUCTOR', 'OFFERING_TA', 'TA', 'STUDENT');
        DELETE FROM course_members
        WHERE offering_id = {offer_b}
          AND user_id IN ({delete_offer_b_user_ids})
          AND member_role IN ('OFFERING_TA', 'STUDENT');
        UPDATE course_offerings offering
        SET selected_count = (
            SELECT COUNT(*)
            FROM course_members member
            WHERE member.offering_id = offering.id
              AND member.member_role = 'STUDENT'
              AND member.member_status = 'ACTIVE'
        )
        WHERE offering.id IN ({offer_a}, {offer_b});
        """
    )
    record_fixture(context, "reset", "course-members-baseline")


def reset_fixture_assignment_baseline(context: FixtureContext, active_open_at: str, active_due_at: str, closed_open_at: str, closed_due_at: str) -> None:
    published_without_grade_ids = ", ".join(
        str(context.ids[key])
        for key in [
            "Task-A1-Published",
            "Task-A2-Published",
            "Task-Programming-A1",
            "Task-B1-Published",
        ]
    )
    sql_exec(
        f"""
        UPDATE assignments
        SET open_at = {sql_literal(active_open_at)}::timestamptz,
            due_at = {sql_literal(active_due_at)}::timestamptz,
            status = 'PUBLISHED',
            published_at = COALESCE(published_at, now()),
            closed_at = NULL,
            grade_published_at = NULL,
            grade_published_by_user_id = NULL
        WHERE id IN ({published_without_grade_ids});
        UPDATE assignments
        SET open_at = {sql_literal(active_open_at)}::timestamptz,
            due_at = {sql_literal(active_due_at)}::timestamptz,
            status = 'PUBLISHED',
            published_at = COALESCE(published_at, now()),
            closed_at = NULL
        WHERE id = {context.ids["Task-Offering-Published"]};
        UPDATE assignments
        SET open_at = {sql_literal(active_open_at)}::timestamptz,
            due_at = {sql_literal(active_due_at)}::timestamptz,
            status = 'DRAFT',
            published_at = NULL,
            closed_at = NULL,
            grade_published_at = NULL,
            grade_published_by_user_id = NULL
        WHERE id = {context.ids["Task-A2-Draft"]};
        UPDATE assignments
        SET open_at = {sql_literal(closed_open_at)}::timestamptz,
            due_at = {sql_literal(closed_due_at)}::timestamptz,
            status = 'CLOSED',
            published_at = COALESCE(published_at, now()),
            closed_at = COALESCE(closed_at, now())
        WHERE id = {context.ids["Task-A1-Closed"]};
        UPDATE course_offerings
        SET status = 'ONGOING',
            start_at = {sql_literal(iso_offset(datetime.now(UTC_PLUS_8) - timedelta(days=30)))}::timestamptz,
            end_at = {sql_literal(iso_offset(datetime.now(UTC_PLUS_8) + timedelta(days=90)))}::timestamptz,
            archived_at = NULL
        WHERE id IN ({context.ids["Offer-A-2025F"]}, {context.ids["Offer-A-2026S"]}, {context.ids["Offer-B-2026S"]});
        """
    )
    record_fixture(context, "reset", "assignment-status-baseline")


def find_member_id(
    context: FixtureContext,
    actor: str,
    offering_key: str,
    username: str,
    member_role: str,
    *,
    teaching_class_key: str | None = None,
    member_status: str | None = None,
) -> int:
    params: dict[str, Any] = {
        "page": 1,
        "pageSize": 200,
        "keyword": username,
        "memberRole": member_role,
    }
    if teaching_class_key is not None:
        params["teachingClassId"] = context.ids[teaching_class_key]
    if member_status is not None:
        params["memberStatus"] = member_status
    payload = context.api.get_json(
        f"/api/v1/teacher/course-offerings/{context.ids[offering_key]}/members?{query_string(params)}",
        token=context.tokens[actor],
        expected_status=200,
    )
    normalized_username = username.strip().lower()
    for item in payload["items"]:
        same_user = item["user"]["username"].strip().lower() == normalized_username
        same_role = item["memberRole"] == member_role
        same_status = member_status is None or item["memberStatus"] == member_status
        same_class = teaching_class_key is None or int(item["teachingClassId"]) == context.ids[teaching_class_key]
        if same_user and same_role and same_status and same_class:
            return int(item["id"])
    raise AssertionError(
        "未通过真实成员列表找到 memberId: offering=%s username=%s role=%s class=%s status=%s items=%s"
        % (
            offering_key,
            username,
            member_role,
            teaching_class_key,
            member_status,
            json.dumps(payload["items"], ensure_ascii=False),
        )
    )


def update_member_status(
    context: FixtureContext,
    actor: str,
    offering_key: str,
    username: str,
    member_role: str,
    target_status: str,
    remark: str,
    *,
    teaching_class_key: str | None = None,
) -> dict[str, Any]:
    member_id = find_member_id(
        context,
        actor,
        offering_key,
        username,
        member_role,
        teaching_class_key=teaching_class_key,
    )
    response = api_patch(
        context.api,
        f"/api/v1/teacher/course-offerings/{context.ids[offering_key]}/members/{member_id}/status",
        context.tokens[actor],
        {"memberStatus": target_status, "remark": remark},
        expected_status=200,
    )
    return response_json(response)


def transfer_member(
    context: FixtureContext,
    actor: str,
    offering_key: str,
    username: str,
    member_role: str,
    source_class_key: str,
    target_class_key: str,
    remark: str,
) -> dict[str, Any]:
    member_id = find_member_id(
        context,
        actor,
        offering_key,
        username,
        member_role,
        teaching_class_key=source_class_key,
    )
    return context.api.post_json(
        f"/api/v1/teacher/course-offerings/{context.ids[offering_key]}/members/{member_id}/transfer",
        {"targetTeachingClassId": context.ids[target_class_key], "remark": remark},
        token=context.tokens[actor],
        expected_status=200,
    )


def setup_fixtures(api: ApiClient, admin_login: dict[str, Any]) -> FixtureContext:
    context = FixtureContext(api=api, admin_login=admin_login, admin_token=get_access_token(admin_login))

    org_tree = api.get_json("/api/v1/admin/org-units/tree", token=context.admin_token, expected_status=200)
    school = org_tree[0]
    context.ids["S1"] = int(school["id"])
    context.creations["bootstrap"].extend(["S1", "U-SA1", "platform-config"])
    context.ids["C1"] = ensure_org_unit(
        context, code="C1", name="计算机学院", unit_type="COLLEGE", parent_id=context.ids["S1"], sort_order=1
    )
    context.ids["C2"] = ensure_org_unit(
        context, code="C2", name="人工智能学院", unit_type="COLLEGE", parent_id=context.ids["S1"], sort_order=2
    )

    user_specs = [
        ("U-CA1", "学院管理员1", "ADMIN", context.ids["C1"], [{"roleCode": "COLLEGE_ADMIN", "scopeOrgUnitId": context.ids["C1"]}]),
        ("U-CA2", "学院管理员2", "ADMIN", context.ids["C2"], [{"roleCode": "COLLEGE_ADMIN", "scopeOrgUnitId": context.ids["C2"]}]),
        ("U-TA1", "教师1", "TEACHER", context.ids["C1"], None),
        ("U-TA2", "教师2", "TEACHER", context.ids["C1"], None),
        ("U-TA3", "教师3", "TEACHER", context.ids["C2"], None),
        ("U-TAO1", "开课助教1", "TEACHER", context.ids["C1"], None),
        ("U-TAC1", "班级助教1", "TEACHER", context.ids["C1"], None),
        ("U-ST1", "学生1", "STUDENT", context.ids["C1"], None),
        ("U-ST2", "学生2", "STUDENT", context.ids["C1"], None),
        ("U-ST3", "学生3", "STUDENT", context.ids["C2"], None),
        ("U-M1", "多身份用户1", "STUDENT", context.ids["C1"], None),
        ("U-STX1", "转班样本学生", "STUDENT", context.ids["C1"], None),
    ]
    for username, display_name, identity_type, org_id, identity_assignments in user_specs:
        context.ids[username] = ensure_user(
            context,
            username=username,
            display_name=display_name,
            email=f"{username.lower().replace('-', '')}@example.edu",
            academic_id=username,
            identity_type=identity_type,
            primary_org_unit_id=org_id,
            identity_assignments=identity_assignments,
        )

    ca1_login = login(api, "U-CA1")
    ca2_login = login(api, "U-CA2")
    context.login_results["U-CA1"] = ca1_login
    context.login_results["U-CA2"] = ca2_login
    context.tokens["U-CA1"] = get_access_token(ca1_login)
    context.tokens["U-CA2"] = get_access_token(ca2_login)

    archived_term_start, archived_term_end = term_window(-240, -120)
    active_term_start, active_term_end = term_window(-30, 120)
    context.ids["TERM-2025F"] = ensure_term(
        context,
        term_code="2025F",
        term_name="2025 秋季学期",
        school_year="2025-2026",
        semester="AUTUMN",
        start_date=archived_term_start,
        end_date=archived_term_end,
    )
    context.ids["TERM-2026S"] = ensure_term(
        context,
        term_code="2026S",
        term_name="2026 春季学期",
        school_year="2025-2026",
        semester="SPRING",
        start_date=active_term_start,
        end_date=active_term_end,
    )

    context.ids["Course-A"] = ensure_course_catalog(
        context,
        actor_token=context.tokens["U-CA1"],
        course_code="Course-A",
        course_name="数据结构",
        department_unit_id=context.ids["C1"],
        description="权限 E2E Course-A",
    )
    context.ids["Course-B"] = ensure_course_catalog(
        context,
        actor_token=context.tokens["U-CA2"],
        course_code="Course-B",
        course_name="机器学习",
        department_unit_id=context.ids["C2"],
        description="权限 E2E Course-B",
    )

    offering_start = iso_offset(datetime.now(UTC_PLUS_8) - timedelta(days=30))
    offering_end = iso_offset(datetime.now(UTC_PLUS_8) + timedelta(days=90))

    context.ids["Offer-A-2025F"] = ensure_course_offering(
        context,
        actor_token=context.tokens["U-CA1"],
        catalog_id=context.ids["Course-A"],
        term_id=context.ids["TERM-2025F"],
        offering_code="Offer-A-2025F",
        offering_name="数据结构 2025 秋",
        primary_college_unit_id=context.ids["C1"],
        instructor_user_ids=[context.ids["U-TA1"]],
        start_at=offering_start,
        end_at=offering_end,
    )
    context.ids["Offer-A-2026S"] = ensure_course_offering(
        context,
        actor_token=context.tokens["U-CA1"],
        catalog_id=context.ids["Course-A"],
        term_id=context.ids["TERM-2026S"],
        offering_code="Offer-A-2026S",
        offering_name="数据结构 2026 春",
        primary_college_unit_id=context.ids["C1"],
        instructor_user_ids=[context.ids["U-TA3"]],
        start_at=offering_start,
        end_at=offering_end,
    )
    context.ids["Offer-B-2026S"] = ensure_course_offering(
        context,
        actor_token=context.tokens["U-CA2"],
        catalog_id=context.ids["Course-B"],
        term_id=context.ids["TERM-2026S"],
        offering_code="Offer-B-2026S",
        offering_name="机器学习 2026 春",
        primary_college_unit_id=context.ids["C2"],
        instructor_user_ids=[context.ids["U-TA3"]],
        start_at=offering_start,
        end_at=offering_end,
    )

    teacher_1_login = login(api, "U-TA1")
    teacher_3_login = login(api, "U-TA3")
    context.login_results["U-TA1"] = teacher_1_login
    context.login_results["U-TA3"] = teacher_3_login
    context.tokens["U-TA1"] = get_access_token(teacher_1_login)
    context.tokens["U-TA3"] = get_access_token(teacher_3_login)

    context.ids["A1"] = ensure_teaching_class(
        context, teacher_token=context.tokens["U-TA1"], offering_id=context.ids["Offer-A-2025F"], class_code="A1", schedule_summary="周二 1-2 节"
    )
    context.ids["A2"] = ensure_teaching_class(
        context, teacher_token=context.tokens["U-TA1"], offering_id=context.ids["Offer-A-2025F"], class_code="A2", schedule_summary="周三 3-4 节"
    )
    context.ids["A3"] = ensure_teaching_class(
        context, teacher_token=context.tokens["U-TA3"], offering_id=context.ids["Offer-A-2026S"], class_code="A3", schedule_summary="周四 1-2 节"
    )
    context.ids["B1"] = ensure_teaching_class(
        context, teacher_token=context.tokens["U-TA3"], offering_id=context.ids["Offer-B-2026S"], class_code="B1", schedule_summary="周五 1-2 节"
    )

    reset_fixture_member_baseline(context)
    api.post_json(
        f"/api/v1/teacher/course-offerings/{context.ids['Offer-A-2025F']}/members/batch",
        {
            "members": [
                {"userId": context.ids["U-TA2"], "memberRole": "CLASS_INSTRUCTOR", "teachingClassId": context.ids["A1"], "remark": "fixture"},
                {"userId": context.ids["U-TAO1"], "memberRole": "OFFERING_TA", "remark": "fixture"},
                {"userId": context.ids["U-TAC1"], "memberRole": "TA", "teachingClassId": context.ids["A1"], "remark": "fixture"},
                {"userId": context.ids["U-ST1"], "memberRole": "STUDENT", "teachingClassId": context.ids["A1"], "remark": "fixture"},
                {"userId": context.ids["U-ST2"], "memberRole": "STUDENT", "teachingClassId": context.ids["A2"], "remark": "fixture"},
                {"userId": context.ids["U-M1"], "memberRole": "STUDENT", "teachingClassId": context.ids["A1"], "remark": "fixture"},
                {"userId": context.ids["U-STX1"], "memberRole": "STUDENT", "teachingClassId": context.ids["A1"], "remark": "fixture"},
            ]
        },
        token=context.tokens["U-TA1"],
        expected_status=200,
    )
    api.post_json(
        f"/api/v1/teacher/course-offerings/{context.ids['Offer-B-2026S']}/members/batch",
        {
            "members": [
                {"userId": context.ids["U-ST3"], "memberRole": "STUDENT", "teachingClassId": context.ids["B1"], "remark": "fixture"},
                {"userId": context.ids["U-M1"], "memberRole": "OFFERING_TA", "remark": "fixture"},
            ]
        },
        token=context.tokens["U-TA3"],
        expected_status=200,
    )
    record_fixture(context, "api", "course-members")

    for username in ["U-TA2", "U-TAO1", "U-TAC1", "U-ST1", "U-ST2", "U-ST3", "U-M1", "U-STX1"]:
        login_result = login(api, username)
        context.login_results[username] = login_result
        context.tokens[username] = get_access_token(login_result)

    context.ids["Question-A-Short"] = ensure_question(
        context,
        teacher_token=context.tokens["U-TA1"],
        offering_id=context.ids["Offer-A-2025F"],
        title="Question-A-Short",
        creator=lambda fixture_api, token, offering_id, title: make_short_answer_question(
            fixture_api, token, offering_id, title, "请说明稳定排序的定义。"
        ),
    )
    context.ids["Question-A-Programming"] = ensure_question(
        context,
        teacher_token=context.tokens["U-TA1"],
        offering_id=context.ids["Offer-A-2025F"],
        title="Question-A-Programming",
        creator=make_programming_question,
    )
    context.ids["Question-B-Short"] = ensure_question(
        context,
        teacher_token=context.tokens["U-TA3"],
        offering_id=context.ids["Offer-B-2026S"],
        title="Question-B-Short",
        creator=lambda fixture_api, token, offering_id, title: make_short_answer_question(
            fixture_api, token, offering_id, title, "请说明梯度下降的基本思想。"
        ),
    )

    active_open_at, active_due_at = assignment_window(-1, 7)
    closed_open_at, closed_due_at = assignment_window(-10, -2)

    assignment_specs = [
        ("Task-Offering-Published", context.tokens["U-TA1"], context.ids["Offer-A-2025F"], context.ids["Question-A-Short"], None, active_open_at, active_due_at),
        ("Task-A1-Published", context.tokens["U-TA1"], context.ids["Offer-A-2025F"], context.ids["Question-A-Short"], context.ids["A1"], active_open_at, active_due_at),
        ("Task-A2-Published", context.tokens["U-TA1"], context.ids["Offer-A-2025F"], context.ids["Question-A-Short"], context.ids["A2"], active_open_at, active_due_at),
        ("Task-A2-Draft", context.tokens["U-TA1"], context.ids["Offer-A-2025F"], context.ids["Question-A-Short"], context.ids["A2"], active_open_at, active_due_at),
        ("Task-A1-Closed", context.tokens["U-TA1"], context.ids["Offer-A-2025F"], context.ids["Question-A-Short"], context.ids["A1"], closed_open_at, closed_due_at),
        ("Task-Programming-A1", context.tokens["U-TA1"], context.ids["Offer-A-2025F"], context.ids["Question-A-Programming"], context.ids["A1"], active_open_at, active_due_at),
        ("Task-B1-Published", context.tokens["U-TA3"], context.ids["Offer-B-2026S"], context.ids["Question-B-Short"], context.ids["B1"], active_open_at, active_due_at),
    ]
    assignment_tokens: dict[str, str] = {}
    for key, teacher_token, offering_id, question_id, teaching_class_id, open_at, due_at in assignment_specs:
        assignment_id, assignment_view, _ = ensure_assignment(
            context,
            teacher_token=teacher_token,
            offering_id=offering_id,
            title=key,
            question_id=question_id,
            teaching_class_id=teaching_class_id,
            open_at=open_at,
            due_at=due_at,
        )
        context.ids[key] = assignment_id
        context.views[key] = assignment_view
        assignment_tokens[key] = teacher_token

    reset_fixture_assignment_baseline(context, active_open_at, active_due_at, closed_open_at, closed_due_at)
    for key, teacher_token in assignment_tokens.items():
        context.views[key] = teacher_assignment_detail(context.api, teacher_token, context.ids[key])

    submission_specs = [
        ("Sub-ST1-Offering", context.tokens["U-ST1"], context.tokens["U-TA1"], "Task-Offering-Published", "U-ST1", "稳定排序不会改变相同关键字元素的相对顺序。"),
        ("Sub-ST1-A1", context.tokens["U-ST1"], context.tokens["U-TA1"], "Task-A1-Published", "U-ST1", "A1 学生作答。"),
        ("Sub-ST2-A2", context.tokens["U-ST2"], context.tokens["U-TA1"], "Task-A2-Published", "U-ST2", "A2 学生作答。"),
        ("Sub-STX1-A1", context.tokens["U-STX1"], context.tokens["U-TA1"], "Task-A1-Published", "U-STX1", "转班样本学生在 A1 的历史提交。"),
        ("Sub-ST3-B1", context.tokens["U-ST3"], context.tokens["U-TA3"], "Task-B1-Published", "U-ST3", "B1 学生作答。"),
    ]
    for key, actor_token, teacher_token, assignment_key, submitter_key, answer_text in submission_specs:
        submission_id, submission_view, _ = ensure_submission(
            context,
            actor_token=actor_token,
            teacher_token=teacher_token,
            assignment_id=context.ids[assignment_key],
            assignment_question_id=first_question_id(context.views[assignment_key]),
            submitter_user_id=context.ids[submitter_key],
            answer_text=answer_text,
            label=key,
        )
        context.ids[key] = submission_id
        context.views[key] = submission_view

    published_answer = context.views["Sub-ST1-Offering"]["answers"][0]
    if published_answer.get("finalScore") != 95 or published_answer.get("feedbackText") != "已发布成绩样本":
        grade_answer(
            api,
            context.tokens["U-TA1"],
            context.ids["Sub-ST1-Offering"],
            first_answer_id(context.views["Sub-ST1-Offering"]),
            95,
            "已发布成绩样本",
        )
        record_fixture(context, "api", "Grade-ST1-Published")
        context.views["Sub-ST1-Offering"] = teacher_submission_detail(api, context.tokens["U-TA1"], context.ids["Sub-ST1-Offering"])
    else:
        record_fixture(context, "reused", "Grade-ST1-Published")

    gradebook_payload = api.get_json(
        f"/api/v1/me/course-offerings/{context.ids['Offer-A-2025F']}/gradebook",
        token=context.tokens["U-ST1"],
        expected_status=200,
    )
    offering_grade_row = next(
        item["grade"] for item in gradebook_payload["assignments"] if item["assignment"]["title"] == "Task-Offering-Published"
    )
    if not bool(offering_grade_row.get("gradePublished")):
        publish_assignment_grades(api, context.tokens["U-TA1"], context.ids["Task-Offering-Published"])
        record_fixture(context, "api", "Task-Offering-Published.grades-published")
    else:
        record_fixture(context, "reused", "Task-Offering-Published.grades-published")

    draft_answer = context.views["Sub-ST1-A1"]["answers"][0]
    if draft_answer.get("finalScore") != 88 or draft_answer.get("feedbackText") != "未发布成绩草稿样本":
        grade_answer(
            api,
            context.tokens["U-TA1"],
            context.ids["Sub-ST1-A1"],
            first_answer_id(context.views["Sub-ST1-A1"]),
            88,
            "未发布成绩草稿样本",
        )
        record_fixture(context, "api", "Grade-ST1-Draft")
        context.views["Sub-ST1-A1"] = teacher_submission_detail(api, context.tokens["U-TA1"], context.ids["Sub-ST1-A1"])
    else:
        record_fixture(context, "reused", "Grade-ST1-Draft")

    context.ids["Offer-A-Archived"] = ensure_course_offering(
        context,
        actor_token=context.tokens["U-CA1"],
        catalog_id=context.ids["Course-A"],
        term_id=context.ids["TERM-2025F"],
        offering_code="Offer-A-Archived",
        offering_name="数据结构 已归档样本",
        primary_college_unit_id=context.ids["C1"],
        instructor_user_ids=[context.ids["U-TA1"]],
        start_at=offering_start,
        end_at=offering_end,
    )
    sql_exec(
        "UPDATE course_offerings SET status = 'ARCHIVED', archived_at = COALESCE(archived_at, now()) WHERE id = %d;"
        % context.ids["Offer-A-Archived"]
    )
    record_fixture(context, "sql", "Offer-A-Archived.status=ARCHIVED")

    return context


def append_case(results: list[CaseResult], case: CaseResult) -> None:
    results.append(case)


def run_cases(context: FixtureContext) -> list[CaseResult]:
    api = context.api
    admin_token = context.admin_token
    results: list[CaseResult] = []

    def record(
        case_id: str,
        title: str,
        actor: str,
        method: str,
        path: str,
        expected_status: list[int],
        expected_result: str,
        action,
        *,
        expected_fields: list[str] | None = None,
        audit_required: bool = False,
    ) -> None:
        before_audit = latest_audit_id(api, admin_token)
        try:
            actual_status, actual_result, filtered_fields, side_effect_check, notes = action()
            passed = actual_status in expected_status
            audit_passed = None
            audit_detail = None
            if audit_required:
                denied_audits = fetch_authz_denied_audits(api, admin_token, before_audit)
                audit_passed = len(denied_audits) > 0
                audit_detail = "发现 %d 条 AUTHZ_DENIED" % len(denied_audits) if audit_passed else "未发现 AUTHZ_DENIED"
                passed = passed and audit_passed
            append_case(
                results,
                CaseResult(
                    case_id=case_id,
                    title=title,
                    actor=actor,
                    http_method=method,
                    api_path=path,
                    expected_status=expected_status,
                    actual_status=actual_status,
                    expected_result=expected_result,
                    actual_result=actual_result,
                    passed=passed,
                    expected_filtered_fields=expected_fields or [],
                    actual_filtered_fields=filtered_fields,
                    audit_required=audit_required,
                    audit_passed=audit_passed,
                    audit_detail=audit_detail,
                    side_effect_check=side_effect_check,
                    notes=notes,
                ),
            )
        except Exception as exc:  # pylint: disable=broad-except
            audit_passed = None
            audit_detail = None
            if audit_required:
                denied_audits = fetch_authz_denied_audits(api, admin_token, before_audit)
                audit_passed = len(denied_audits) > 0
                audit_detail = "发现 %d 条 AUTHZ_DENIED" % len(denied_audits) if audit_passed else "未发现 AUTHZ_DENIED"
            append_case(
                results,
                CaseResult(
                    case_id=case_id,
                    title=title,
                    actor=actor,
                    http_method=method,
                    api_path=path,
                    expected_status=expected_status,
                    actual_status=None,
                    expected_result=expected_result,
                    actual_result=str(exc),
                    passed=False,
                    expected_filtered_fields=expected_fields or [],
                    audit_required=audit_required,
                    audit_passed=audit_passed,
                    audit_detail=audit_detail,
                    notes=["场景执行异常"],
                ),
            )

    record(
        "L001",
        "学生列表过滤仅返回本班可见已发布任务",
        "U-ST1",
        "GET",
        "/api/v1/me/assignments",
        [200],
        "仅返回 A1/开课级已发布任务，不返回 A2 草稿与兄弟班资源",
        lambda: list_assignments_case(context),
    )
    record(
        "P079",
        "班级助教搜索接口不过滤兄弟班学生",
        "U-TAC1",
        "GET",
        f"/api/v1/teacher/course-offerings/{context.ids['Offer-A-2025F']}/members?keyword=U-ST2",
        [200],
        "搜索结果 total=0，不出现 A2 学生",
        lambda: class_ta_search_case(context),
        audit_required=False,
    )
    record(
        "P004",
        "学院管理员访问同校其他学院开课实体",
        "U-CA1",
        "GET",
        f"/api/v1/admin/course-offerings/{context.ids['Offer-B-2026S']}",
        [403, 404],
        "C1 学院管理员不能读取 C2 开课实体详情",
        lambda: forbidden_detail_case(context, "U-CA1", f"/api/v1/admin/course-offerings/{context.ids['Offer-B-2026S']}"),
        audit_required=True,
    )
    record(
        "P007",
        "班级教师访问同开课实体其他班级提交",
        "U-TA2",
        "GET",
        f"/api/v1/teacher/submissions/{context.ids['Sub-ST2-A2']}",
        [403, 404],
        "A1 班教师不能读取 A2 学生提交",
        lambda: forbidden_detail_case(context, "U-TA2", f"/api/v1/teacher/submissions/{context.ids['Sub-ST2-A2']}"),
        audit_required=True,
    )
    record(
        "P013",
        "多身份用户在不同开课实体隔离",
        "U-M1",
        "GET",
        f"/api/v1/teacher/submissions/{context.ids['Sub-ST3-B1']}",
        [200],
        "U-M1 在 Offer-B 具助教权限，在 Offer-A 仅学生权限",
        lambda: multi_identity_case(context),
        audit_required=False,
    )
    record(
        "P021",
        "开课级助教访问本开课全部班级提交",
        "U-TAO1",
        "GET",
        f"/api/v1/teacher/submissions/{context.ids['Sub-ST1-A1']}",
        [200],
        "U-TAO1 能读取 A1 与 A2 提交",
        lambda: offering_ta_case(context),
        audit_required=False,
    )
    record(
        "P026",
        "班级助教访问兄弟班级提交",
        "U-TAC1",
        "GET",
        f"/api/v1/teacher/submissions/{context.ids['Sub-ST2-A2']}",
        [403, 404],
        "A1 助教不能读取 A2 提交",
        lambda: forbidden_detail_case(context, "U-TAC1", f"/api/v1/teacher/submissions/{context.ids['Sub-ST2-A2']}"),
        audit_required=True,
    )
    record(
        "P031",
        "同课程不同学期开课实体教师隔离",
        "U-TA1",
        "GET",
        f"/api/v1/teacher/course-offerings/{context.ids['Offer-A-2026S']}/members",
        [403, 404],
        "Offer-A-2025F 教师不能读取 Offer-A-2026S 成员",
        lambda: forbidden_detail_case(context, "U-TA1", f"/api/v1/teacher/course-offerings/{context.ids['Offer-A-2026S']}/members?page=1&pageSize=20"),
        audit_required=True,
    )
    record(
        "P041",
        "班级助教导出全开课实体成绩",
        "U-TAC1",
        "GET",
        f"/api/v1/teacher/course-offerings/{context.ids['Offer-A-2025F']}/gradebook/export",
        [403, 404],
        "班级助教仅可导出本班，不可导出全开课实体",
        lambda: class_ta_export_case(context),
        audit_required=True,
    )
    record(
        "P044",
        "学生查看隐藏测试与评测脚本",
        "U-ST1",
        "GET",
        f"/api/v1/me/assignments/{context.ids['Task-Programming-A1']}",
        [200],
        "返回成功，但不得泄露 hidden cases/customJudgeScript",
        lambda: student_hidden_fields_case(context),
        expected_fields=["paper.sections[].questions[].config.judgeCases", "paper.sections[].questions[].config.customJudgeScript"],
        audit_required=False,
    )
    record(
        "P051",
        "学生查看未发布成绩",
        "U-ST1",
        "GET",
        f"/api/v1/me/course-offerings/{context.ids['Offer-A-2025F']}/gradebook",
        [200],
        "未发布成绩仅能看到 gradePublished=false，不能看到 draft 分值",
        lambda: unpublished_grade_case(context),
        expected_fields=["assignments[].grade.finalScore"],
        audit_required=False,
    )
    record(
        "P057",
        "教师为自己提升到学院管理员",
        "U-TA1",
        "PUT",
        f"/api/v1/admin/users/{context.ids['U-TA1']}/identities",
        [403, 404],
        "教师不能调用管理员身份修改接口给自己提权，且无副作用",
        lambda: self_escalation_case(context),
        audit_required=True,
    )
    record(
        "P077",
        "学生通过递增 ID 访问他人提交",
        "U-ST1",
        "GET",
        f"/api/v1/me/submissions/{context.ids['Sub-ST2-A2']}",
        [403, 404],
        "返回拒绝，且不泄露他人提交明细",
        lambda: forbidden_detail_case(context, "U-ST1", f"/api/v1/me/submissions/{context.ids['Sub-ST2-A2']}"),
        audit_required=True,
    )

    old_st1_token = context.tokens["U-ST1"]
    update_member_status(
        context,
        "U-TA1",
        "Offer-A-2025F",
        "U-ST1",
        "STUDENT",
        "DROPPED",
        "real-api-drop",
        teaching_class_key="A1",
    )
    context.creations["api"].append("U-ST1@A1.member_status=DROPPED")
    refresh_login(context, "U-ST1-dropped", "U-ST1")
    record(
        "P067",
        "学生退课后继续提交",
        "U-ST1(dropped)",
        "POST",
        f"/api/v1/me/assignments/{context.ids['Task-A1-Published']}/submissions",
        [403],
        "退课学生不能继续提交，且提交总数不增加",
        lambda: dropped_student_submit_case(context, old_st1_token),
        audit_required=False,
    )
    record(
        "P068",
        "学生退课后查看本人历史数据",
        "U-ST1(dropped)",
        "GET",
        f"/api/v1/me/submissions/{context.ids['Sub-ST1-A1']}",
        [200],
        "退课学生可只读访问本人历史提交与历史成绩",
        lambda: dropped_student_history_case(context),
        audit_required=False,
    )

    old_stx1_token = context.tokens["U-STX1"]
    transfer_member(
        context,
        "U-TA1",
        "Offer-A-2025F",
        "U-STX1",
        "STUDENT",
        "A1",
        "A2",
        "real-api-transfer",
    )
    context.creations["api"].append("U-STX1 transferred A1->A2")
    refresh_login(context, "U-STX1-transferred", "U-STX1")
    record(
        "P069",
        "学生转班后查看旧班历史提交",
        "U-STX1(transferred)",
        "GET",
        f"/api/v1/me/submissions/{context.ids['Sub-STX1-A1']}",
        [200],
        "转班学生可只读访问自己在旧班的历史提交",
        lambda: transferred_student_own_history_case(context, old_stx1_token),
        audit_required=False,
    )
    record(
        "P070",
        "学生转班后访问旧班他人数据",
        "U-STX1(transferred)",
        "GET",
        f"/api/v1/me/submissions/{context.ids['Sub-ST1-A1']}",
        [403, 404],
        "转班学生不能访问旧班他人提交",
        lambda: transferred_student_peer_data_case(context),
        audit_required=True,
    )

    old_tac1_token = context.tokens["U-TAC1"]
    api.post_json(
        f"/api/v1/teacher/course-offerings/{context.ids['Offer-A-2025F']}/members/batch",
        {
            "members": [
                {
                    "userId": context.ids["U-TAC1"],
                    "memberRole": "OFFERING_TA",
                    "remark": "upgraded-to-offering-ta",
                }
            ]
        },
        token=context.tokens["U-TA1"],
        expected_status=200,
    )
    context.creations["api"].append("U-TAC1 upgraded to OFFERING_TA")
    refresh_login(context, "U-TAC1-new", "U-TAC1")
    record(
        "P071",
        "班级助教升级为开课级助教后访问兄弟班级",
        "U-TAC1(old/new)",
        "GET",
        f"/api/v1/teacher/submissions/{context.ids['Sub-ST2-A2']}",
        [200],
        "升级后新 token 可访问 A2 数据，旧 token 不应维持旧快照继续放行",
        lambda: upgraded_ta_case(context, old_tac1_token),
        audit_required=False,
    )

    old_tao1_token = context.tokens["U-TAO1"]
    update_member_status(
        context,
        "U-TA1",
        "Offer-A-2025F",
        "U-TAO1",
        "OFFERING_TA",
        "REMOVED",
        "downgraded-to-class-ta",
    )
    api.post_json(
        f"/api/v1/teacher/course-offerings/{context.ids['Offer-A-2025F']}/members/batch",
        {
            "members": [
                {
                    "userId": context.ids["U-TAO1"],
                    "memberRole": "TA",
                    "teachingClassId": context.ids["A1"],
                    "remark": "downgraded-to-class-ta",
                }
            ]
        },
        token=context.tokens["U-TA1"],
        expected_status=200,
    )
    context.creations["api"].append("U-TAO1 downgraded from OFFERING_TA to A1 TA")
    refresh_login(context, "U-TAO1-new", "U-TAO1")

    record(
        "P072",
        "开课级助教降为班级助教后访问兄弟班级",
        "U-TAO1(old/new)",
        "GET",
        f"/api/v1/teacher/submissions/{context.ids['Sub-ST2-A2']}",
        [401, 403, 404],
        "旧 token 与新 token 都不应再访问 A2 数据",
        lambda: downgraded_ta_case(context, old_tao1_token),
        audit_required=False,
    )
    record(
        "P075",
        "角色移除后旧 JWT 继续访问高权限导出接口",
        "U-TAO1(old)",
        "GET",
        f"/api/v1/teacher/course-offerings/{context.ids['Offer-A-2025F']}/gradebook/export",
        [401, 403, 404],
        "旧 token 不应继续导出全开课实体成绩",
        lambda: removed_role_old_jwt_case(context, old_tao1_token),
        audit_required=False,
    )

    old_m1_token = context.tokens["U-M1"]
    api.post_json(
        f"/api/v1/teacher/course-offerings/{context.ids['Offer-A-2025F']}/members/batch",
        {
            "members": [
                {
                    "userId": context.ids["U-M1"],
                    "memberRole": "TA",
                    "teachingClassId": context.ids["A2"],
                    "remark": "grant-a2-ta",
                }
            ]
        },
        token=context.tokens["U-TA1"],
        expected_status=200,
    )
    context.creations["api"].append("U-M1 granted A2 TA")
    refresh_login(context, "U-M1-new", "U-M1")
    record(
        "P076",
        "角色新增后权限即时生效且与会话收敛一致",
        "U-M1(old/new)",
        "GET",
        f"/api/v1/teacher/submissions/{context.ids['Sub-ST2-A2']}",
        [200],
        "旧 token 不应继续沿用旧权限快照；重新登录后应立即获得新角色能力",
        lambda: newly_granted_role_case(context, old_m1_token),
        audit_required=False,
    )
    record(
        "P082",
        "越权访问触发审计日志",
        "U-ST1(dropped)",
        "GET",
        f"/api/v1/teacher/submissions/{context.ids['Sub-ST2-A2']}",
        [403, 404],
        "越权请求被拒绝且写入 AUTHZ_DENIED 审计日志",
        lambda: forbidden_detail_case(context, "U-ST1-dropped", f"/api/v1/teacher/submissions/{context.ids['Sub-ST2-A2']}"),
        audit_required=True,
    )

    return results


def list_assignments_case(context: FixtureContext) -> tuple[int, str, list[str], str, list[str]]:
    payload = context.api.get_json(
        f"/api/v1/me/assignments?offeringId={context.ids['Offer-A-2025F']}&page=1&pageSize=50",
        token=context.tokens["U-ST1"],
        expected_status=200,
    )
    titles = [item["title"] for item in payload["items"]]
    visible = set(titles)
    expected_present = {"Task-Offering-Published", "Task-A1-Published", "Task-A1-Closed", "Task-Programming-A1"}
    expected_absent = {"Task-A2-Draft", "Task-A2-Published", "Task-B1-Published"}
    passed = expected_present.issubset(visible) and visible.isdisjoint(expected_absent)
    if not passed:
        raise AssertionError("学生任务列表过滤失败: titles=%s" % titles)
    return (
        200,
        "titles=%s total=%s passed=%s" % (titles, payload["total"], passed),
        [],
        "分页 total=%s" % payload["total"],
        [],
    )


def class_ta_search_case(context: FixtureContext) -> tuple[int, str, list[str], str, list[str]]:
    payload = context.api.get_json(
        f"/api/v1/teacher/course-offerings/{context.ids['Offer-A-2025F']}/members?keyword=U-ST2&page=1&pageSize=20",
        token=context.tokens["U-TAC1"],
        expected_status=200,
    )
    usernames = [item["user"]["username"] for item in payload["items"]]
    if int(payload["total"]) != 0 or usernames:
        raise AssertionError("班级助教搜索暴露了兄弟班学生: total=%s usernames=%s" % (payload["total"], usernames))
    return (
        200,
        "search_total=%s usernames=%s" % (payload["total"], usernames),
        [],
        "搜索 total=%s" % payload["total"],
        [],
    )


def forbidden_detail_case(context: FixtureContext, actor: str, path: str) -> tuple[int, str, list[str], str, list[str]]:
    response = api_get(context.api, path, context.tokens[actor], expected_status=[403, 404])
    body = response.text()[:300]
    return (response.status, body, [], "拒绝请求无写入副作用", [])


def multi_identity_case(context: FixtureContext) -> tuple[int, str, list[str], str, list[str]]:
    teacher_resp = api_get(
        context.api,
        f"/api/v1/teacher/submissions/{context.ids['Sub-ST3-B1']}",
        context.tokens["U-M1"],
        expected_status=200,
    )
    denied_resp = api_get(
        context.api,
        f"/api/v1/teacher/submissions/{context.ids['Sub-ST2-A2']}",
        context.tokens["U-M1"],
        expected_status=[403, 404],
    )
    my_assignment = api_get(
        context.api,
        f"/api/v1/me/assignments/{context.ids['Task-A1-Published']}",
        context.tokens["U-M1"],
        expected_status=200,
    )
    if teacher_resp.status != 200 or denied_resp.status not in (403, 404) or my_assignment.status != 200:
        raise AssertionError(
            "多身份隔离失败: teacher=%s denied=%s my_assignment=%s"
            % (teacher_resp.status, denied_resp.status, my_assignment.status)
        )
    return (
        teacher_resp.status,
        "offer_b_teacher=%s offer_a_teacher_denied=%s a1_student=%s"
        % (teacher_resp.status, denied_resp.status, my_assignment.status),
        [],
        "跨开课实体身份隔离",
        [],
    )


def offering_ta_case(context: FixtureContext) -> tuple[int, str, list[str], str, list[str]]:
    a1 = api_get(
        context.api,
        f"/api/v1/teacher/submissions/{context.ids['Sub-ST1-A1']}",
        context.tokens["U-TAO1"],
        expected_status=200,
    )
    a2 = api_get(
        context.api,
        f"/api/v1/teacher/submissions/{context.ids['Sub-ST2-A2']}",
        context.tokens["U-TAO1"],
        expected_status=200,
    )
    if a1.status != 200 or a2.status != 200:
        raise AssertionError("开课级助教未能访问全部班级: A1=%s A2=%s" % (a1.status, a2.status))
    return (
        a1.status,
        "A1=%s A2=%s" % (a1.status, a2.status),
        [],
        "开课级助教可读全部班级",
        [],
    )


def class_ta_export_case(context: FixtureContext) -> tuple[int, str, list[str], str, list[str]]:
    class_export = context.api.request(
        "GET",
        f"/api/v1/teacher/teaching-classes/{context.ids['A1']}/gradebook/export",
        token=context.tokens["U-TAC1"],
        expected_status=200,
        headers={"Accept": "text/csv"},
    )
    offering_export = context.api.request(
        "GET",
        f"/api/v1/teacher/course-offerings/{context.ids['Offer-A-2025F']}/gradebook/export",
        token=context.tokens["U-TAC1"],
        expected_status=[403, 404],
        headers={"Accept": "text/csv"},
    )
    if class_export.status != 200 or offering_export.status not in (403, 404):
        raise AssertionError(
            "班级助教导出边界失败: class=%s offering=%s"
            % (class_export.status, offering_export.status)
        )
    return (
        offering_export.status,
        "class_export=%s offering_export=%s" % (class_export.status, offering_export.status),
        [],
        "本班导出成功，全开课导出拒绝",
        [],
    )


def student_hidden_fields_case(context: FixtureContext) -> tuple[int, str, list[str], str, list[str]]:
    response = api_get(
        context.api,
        f"/api/v1/me/assignments/{context.ids['Task-Programming-A1']}",
        context.tokens["U-ST1"],
        expected_status=200,
    )
    teacher_view = context.api.get_json(
        f"/api/v1/teacher/assignments/{context.ids['Task-Programming-A1']}",
        token=context.tokens["U-TA1"],
        expected_status=200,
    )
    student_payload = response.json()
    student_config = student_payload["paper"]["sections"][0]["questions"][0]["config"]
    teacher_config = teacher_view["paper"]["sections"][0]["questions"][0]["config"]
    leaked_fields: list[str] = []
    if student_config.get("judgeCases"):
        leaked_fields.append("judgeCases")
    if student_config.get("customJudgeScript"):
        leaked_fields.append("customJudgeScript")
    notes = [
        "teacher_judge_case_count=%s" % len(teacher_config.get("judgeCases") or []),
        "student_judge_case_count=%s" % len(student_config.get("judgeCases") or []),
    ]
    if leaked_fields:
        raise AssertionError("学生视图泄露隐藏字段: %s" % leaked_fields)
    return (
        response.status,
        "student_config_keys=%s leaked_fields=%s" % (sorted(student_config.keys()), leaked_fields),
        leaked_fields,
        "教师视图包含 judgeCases/customJudgeScript；学生视图不应包含",
        notes,
    )


def unpublished_grade_case(context: FixtureContext) -> tuple[int, str, list[str], str, list[str]]:
    payload = context.api.get_json(
        f"/api/v1/me/course-offerings/{context.ids['Offer-A-2025F']}/gradebook",
        token=context.tokens["U-ST1"],
        expected_status=200,
    )
    assignment_map = {item["assignment"]["title"]: item["grade"] for item in payload["assignments"]}
    draft_grade = assignment_map["Task-A1-Published"]
    published_grade = assignment_map["Task-Offering-Published"]
    leaked_fields: list[str] = []
    if draft_grade.get("finalScore") is not None:
        leaked_fields.append("finalScore")
    if draft_grade.get("gradePublished") not in (False, None):
        leaked_fields.append("gradePublished")
    notes = [
        "draft_grade=%s" % json.dumps(draft_grade, ensure_ascii=False),
        "published_grade=%s" % json.dumps(published_grade, ensure_ascii=False),
    ]
    if leaked_fields:
        raise AssertionError("未发布成绩泄露字段: %s" % leaked_fields)
    return (
        200,
        "draft_grade=%s" % json.dumps(draft_grade, ensure_ascii=False),
        leaked_fields,
        "已发布成绩可见，未发布成绩不得暴露 finalScore",
        notes,
    )


def self_escalation_case(context: FixtureContext) -> tuple[int, str, list[str], str, list[str]]:
    before = context.api.get_json(
        f"/api/v1/admin/users/{context.ids['U-TA1']}",
        token=context.admin_token,
        expected_status=200,
    )
    response = api_put(
        context.api,
        f"/api/v1/admin/users/{context.ids['U-TA1']}/identities",
        context.tokens["U-TA1"],
        {"identityAssignments": [{"roleCode": "COLLEGE_ADMIN", "scopeOrgUnitId": context.ids["C1"]}]},
        expected_status=[403, 404],
    )
    after = context.api.get_json(
        f"/api/v1/admin/users/{context.ids['U-TA1']}",
        token=context.admin_token,
        expected_status=200,
    )
    side_effect = "before=%s after=%s" % (before["identities"], after["identities"])
    if before["identities"] != after["identities"]:
        raise AssertionError("教师自提权产生副作用: %s" % side_effect)
    return (
        response.status,
        response.text()[:300],
        [],
        side_effect,
        [],
    )


def dropped_student_submit_case(context: FixtureContext, old_token: str) -> tuple[int, str, list[str], str, list[str]]:
    old_me = context.api.request(
        "GET",
        "/api/v1/auth/me",
        token=old_token,
        expected_status=[401, 403, 404],
    )
    before_count = list_assignment_submissions_count(context.api, context.tokens["U-TA1"], context.ids["Task-A1-Published"])
    response = context.api.request(
        "POST",
        f"/api/v1/me/assignments/{context.ids['Task-A1-Published']}/submissions",
        token=context.tokens["U-ST1-dropped"],
        json_body={
            "answers": [
                {
                    "assignmentQuestionId": first_question_id(context.views["Task-A1-Published"]),
                    "answerText": "退课后继续提交",
                }
            ]
        },
        expected_status=[403],
    )
    after_count = list_assignment_submissions_count(context.api, context.tokens["U-TA1"], context.ids["Task-A1-Published"])
    if after_count != before_count:
        raise AssertionError("退课后拒绝提交仍产生副作用: before=%s after=%s" % (before_count, after_count))
    if old_me.status not in (401, 403, 404):
        raise AssertionError("退课后旧 token 未收敛: status=%s" % old_me.status)
    return (
        response.status,
        "old_me=%s submit=%s body=%s" % (old_me.status, response.status, response.text()[:200]),
        [],
        "before_count=%s after_count=%s old_me=%s" % (before_count, after_count, old_me.status),
        [],
    )


def dropped_student_history_case(context: FixtureContext) -> tuple[int, str, list[str], str, list[str]]:
    submission_response = context.api.request(
        "GET",
        f"/api/v1/me/submissions/{context.ids['Sub-ST1-A1']}",
        token=context.tokens["U-ST1-dropped"],
        expected_status=200,
    )
    gradebook_response = context.api.request(
        "GET",
        f"/api/v1/me/course-offerings/{context.ids['Offer-A-2025F']}/gradebook",
        token=context.tokens["U-ST1-dropped"],
        expected_status=200,
    )
    submission_payload = submission_response.json()
    gradebook_payload = gradebook_response.json()
    assignment_titles = [item["assignment"]["title"] for item in gradebook_payload["assignments"]]
    if int(submission_payload["id"]) != context.ids["Sub-ST1-A1"]:
        raise AssertionError("退课学生历史提交读取错误: %s" % submission_payload)
    if "Task-Offering-Published" not in assignment_titles:
        raise AssertionError("退课学生历史成绩未返回已发布成绩条目: %s" % assignment_titles)
    return (
        submission_response.status,
        "submission_id=%s gradebook_titles=%s" % (submission_payload["id"], assignment_titles),
        [],
        "退课后保留本人历史只读",
        [],
    )


def transferred_student_own_history_case(
    context: FixtureContext, old_token: str
) -> tuple[int, str, list[str], str, list[str]]:
    old_me = context.api.request(
        "GET",
        "/api/v1/auth/me",
        token=old_token,
        expected_status=[401, 403, 404],
    )
    submission_response = context.api.request(
        "GET",
        f"/api/v1/me/submissions/{context.ids['Sub-STX1-A1']}",
        token=context.tokens["U-STX1-transferred"],
        expected_status=200,
    )
    assignment_response = context.api.request(
        "GET",
        f"/api/v1/me/assignments/{context.ids['Task-A1-Published']}",
        token=context.tokens["U-STX1-transferred"],
        expected_status=200,
    )
    submit_response = context.api.request(
        "POST",
        f"/api/v1/me/assignments/{context.ids['Task-A1-Published']}/submissions",
        token=context.tokens["U-STX1-transferred"],
        json_body={
            "answers": [
                {
                    "assignmentQuestionId": first_question_id(context.views["Task-A1-Published"]),
                    "answerText": "转班后不应继续提交旧班任务",
                }
            ]
        },
        expected_status=[403],
    )
    if old_me.status not in (401, 403, 404):
        raise AssertionError("转班后旧 token 未收敛: status=%s" % old_me.status)
    return (
        submission_response.status,
        "old_me=%s old_submission=%s assignment=%s submit_old_class=%s"
        % (old_me.status, submission_response.status, assignment_response.status, submit_response.status),
        [],
        "转班后历史只读，旧班正式提交拒绝",
        [],
    )


def transferred_student_peer_data_case(context: FixtureContext) -> tuple[int, str, list[str], str, list[str]]:
    response = context.api.request(
        "GET",
        f"/api/v1/me/submissions/{context.ids['Sub-ST1-A1']}",
        token=context.tokens["U-STX1-transferred"],
        expected_status=[403, 404],
    )
    return (
        response.status,
        response.text()[:300],
        [],
        "转班后不可访问旧班他人提交",
        [],
    )


def upgraded_ta_case(context: FixtureContext, old_token: str) -> tuple[int, str, list[str], str, list[str]]:
    old_response = context.api.request(
        "GET",
        f"/api/v1/teacher/submissions/{context.ids['Sub-ST2-A2']}",
        token=old_token,
        expected_status=[200, 401, 403, 404],
    )
    new_response = context.api.request(
        "GET",
        f"/api/v1/teacher/submissions/{context.ids['Sub-ST2-A2']}",
        token=context.tokens["U-TAC1-new"],
        expected_status=200,
    )
    if old_response.status == 200:
        raise AssertionError("升级后旧 token 仍保留过期权限快照: %s" % old_response.status)
    if new_response.status != 200:
        raise AssertionError("升级后新 token 未获得 A2 读取权限: %s" % new_response.status)
    return (
        new_response.status,
        "old_token=%s new_token=%s" % (old_response.status, new_response.status),
        [],
        "升级后新 token 可立即访问兄弟班级",
        [],
    )


def newly_granted_role_case(context: FixtureContext, old_token: str) -> tuple[int, str, list[str], str, list[str]]:
    old_response = context.api.request(
        "GET",
        f"/api/v1/teacher/submissions/{context.ids['Sub-ST2-A2']}",
        token=old_token,
        expected_status=[200, 401, 403, 404],
    )
    new_response = context.api.request(
        "GET",
        f"/api/v1/teacher/submissions/{context.ids['Sub-ST2-A2']}",
        token=context.tokens["U-M1-new"],
        expected_status=200,
    )
    if old_response.status == 200 and new_response.status == 200:
        raise AssertionError("旧 token 与新 token 同时持有新增角色能力，缺少会话收敛")
    if new_response.status != 200:
        raise AssertionError("新增角色后重新登录仍未获得能力: %s" % new_response.status)
    return (
        new_response.status,
        "old_token=%s new_token=%s" % (old_response.status, new_response.status),
        [],
        "系统采用旧 token 收敛 + 新登录立即生效",
        [],
    )


def downgraded_ta_case(context: FixtureContext, old_token: str) -> tuple[int, str, list[str], str, list[str]]:
    old_response = context.api.request(
        "GET",
        f"/api/v1/teacher/submissions/{context.ids['Sub-ST2-A2']}",
        token=old_token,
        expected_status=[200, 401, 403, 404],
    )
    new_response = context.api.request(
        "GET",
        f"/api/v1/teacher/submissions/{context.ids['Sub-ST2-A2']}",
        token=context.tokens["U-TAO1-new"],
        expected_status=[401, 403, 404],
    )
    a1_response = context.api.request(
        "GET",
        f"/api/v1/teacher/submissions/{context.ids['Sub-ST1-A1']}",
        token=context.tokens["U-TAO1-new"],
        expected_status=[200, 401, 403, 404],
    )
    if old_response.status not in (401, 403, 404) or new_response.status not in (401, 403, 404) or a1_response.status != 200:
        raise AssertionError(
            "助教降级后权限未正确收缩: old=%s new=%s newA1=%s"
            % (old_response.status, new_response.status, a1_response.status)
        )
    return (
        old_response.status,
        "old_token=%s new_token=%s new_token_a1=%s" % (old_response.status, new_response.status, a1_response.status),
        [],
        "降级后旧 token 应失效或拒绝，新 token 仅能访问 A1",
        [],
    )


def removed_role_old_jwt_case(context: FixtureContext, old_token: str) -> tuple[int, str, list[str], str, list[str]]:
    response = context.api.request(
        "GET",
        f"/api/v1/teacher/course-offerings/{context.ids['Offer-A-2025F']}/gradebook/export",
        token=old_token,
        expected_status=[200, 401, 403, 404],
        headers={"Accept": "text/csv"},
    )
    if response.status not in (401, 403, 404):
        raise AssertionError("旧 JWT 仍可导出成绩: status=%s" % response.status)
    return (
        response.status,
        response.text()[:200],
        [],
        "旧 token 不应继续拥有全开课导出能力",
        [],
    )


def build_markdown_report(
    base_url: str,
    fixture_context: FixtureContext,
    results: list[CaseResult],
) -> str:
    passed = sum(1 for item in results if item.passed)
    failed = len(results) - passed
    lines = [
        "# 权限系统真实 API E2E 报告",
        "",
        "## 概览",
        "",
        f"- Base URL: `{base_url}`",
        f"- 用例总数: {len(results)}",
        f"- 通过: {passed}",
        f"- 失败: {failed}",
        "",
        "## 夹具来源",
        "",
        f"- bootstrap: {', '.join(fixture_context.creations['bootstrap'])}",
        f"- 复用现有: {', '.join(fixture_context.creations['reused']) if fixture_context.creations['reused'] else '无'}",
        f"- API 创建: {', '.join(fixture_context.creations['api'])}",
        f"- 基线重置: {', '.join(fixture_context.creations['reset']) if fixture_context.creations['reset'] else '无'}",
        f"- SQL 补齐: {', '.join(fixture_context.creations['sql']) if fixture_context.creations['sql'] else '无'}",
        "",
        "## 用例结果",
        "",
    ]
    for item in results:
        lines.extend(
            [
                f"### {item.case_id} {item.title}",
                "",
                f"- Actor: `{item.actor}`",
                f"- Request: `{item.http_method} {item.api_path}`",
                f"- Expected Status: `{item.expected_status}`",
                f"- Actual Status: `{item.actual_status}`",
                f"- Expected: {item.expected_result}",
                f"- Actual: {item.actual_result}",
                f"- Passed: `{item.passed}`",
                f"- Audit Required: `{item.audit_required}`",
                f"- Audit Passed: `{item.audit_passed}`",
                f"- Audit Detail: {item.audit_detail or 'n/a'}",
                f"- Side Effect Check: {item.side_effect_check or 'n/a'}",
                f"- Filtered Fields: expected={item.expected_filtered_fields}, actual={item.actual_filtered_fields}",
            ]
        )
        if item.notes:
            lines.append(f"- Notes: {' | '.join(item.notes)}")
        lines.append("")
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description="真实 HTTP API 权限系统 E2E 测试")
    parser.add_argument("--base-url", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--admin-username", default="U-SA1")
    parser.add_argument("--admin-password", default=DEFAULT_PASSWORD)
    args = parser.parse_args()

    out_dir = Path(args.out)
    ensure_dir(out_dir)

    api = ApiClient(args.base_url)
    admin_login = login(api, args.admin_username, args.admin_password)
    fixture_context = setup_fixtures(api, admin_login)
    results = run_cases(fixture_context)

    passed = sum(1 for item in results if item.passed)
    failed_items = [item for item in results if not item.passed]
    auth_snapshot = {
        key: {
            "username": value["user"]["username"],
            "tokenType": value["tokenType"],
            "expiresInSeconds": value["expiresInSeconds"],
            "refreshExpiresInSeconds": value["refreshExpiresInSeconds"],
            "sessionId": value["refreshToken"].split(".", 1)[0],
            "authorities": value["user"].get("identities", []),
        }
        for key, value in fixture_context.login_results.items()
        if key
        in {
            "U-SA1",
            "U-CA1",
            "U-TA1",
            "U-TA2",
            "U-TAO1",
            "U-TAO1-new",
            "U-TAC1",
            "U-TAC1-new",
            "U-ST1",
            "U-ST1-dropped",
            "U-STX1",
            "U-STX1-transferred",
            "U-M1",
            "U-M1-new",
        }
    }
    summary = {
        "baseUrl": args.base_url,
        "fixtureCounts": {key: len(value) for key, value in fixture_context.creations.items()},
        "caseCount": len(results),
        "passedCount": passed,
        "failedCount": len(failed_items),
        "status": "passed" if not failed_items else "failed",
        "failures": [item.case_id for item in failed_items],
    }

    json_report = {
        "summary": summary,
        "fixtures": {
            "sources": fixture_context.creations,
            "ids": fixture_context.ids,
        },
        "auth": auth_snapshot,
        "cases": [item.__dict__ for item in results],
    }

    (out_dir / "summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2), encoding="utf-8")
    (out_dir / "report.json").write_text(json.dumps(json_report, ensure_ascii=False, indent=2), encoding="utf-8")
    (out_dir / "report.md").write_text(
        build_markdown_report(args.base_url, fixture_context, results),
        encoding="utf-8",
    )

    print(json.dumps(summary, ensure_ascii=False, indent=2))
    if failed_items:
        sys.exit(1)


if __name__ == "__main__":
    main()
