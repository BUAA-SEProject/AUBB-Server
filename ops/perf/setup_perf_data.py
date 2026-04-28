#!/usr/bin/env python3
import csv
import io
import json
import os
import time
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

import requests


TZ = timezone(timedelta(hours=8))


@dataclass
class Client:
    base_url: str
    session: requests.Session
    token: str | None = None

    def request(self, method: str, path: str, **kwargs):
        headers = kwargs.pop("headers", {})
        if self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        response = self.session.request(method, self.base_url + path, headers=headers, timeout=30, **kwargs)
        if response.status_code >= 400:
            raise RuntimeError(f"{method} {path} -> {response.status_code}: {response.text[:500]}")
        return response

    def get(self, path: str, **kwargs):
        return self.request("GET", path, **kwargs)

    def post(self, path: str, **kwargs):
        return self.request("POST", path, **kwargs)


def iso(dt: datetime) -> str:
    return dt.astimezone(TZ).isoformat(timespec="seconds")


def read_id(payload: dict) -> int:
    return int(payload["id"])


def login(session: requests.Session, base_url: str, username: str, password: str) -> str:
    response = session.post(
        base_url + "/api/v1/auth/login",
        json={"username": username, "password": password},
        timeout=30,
    )
    response.raise_for_status()
    return response.json()["accessToken"]


def find_school_root(nodes) -> int:
    stack = list(nodes if isinstance(nodes, list) else [nodes])
    while stack:
        node = stack.pop()
        if node.get("type") == "SCHOOL":
            return int(node["id"])
        stack.extend(node.get("children") or [])
    raise RuntimeError("未找到学校根节点")


def batch(seq, size):
    for index in range(0, len(seq), size):
        yield seq[index:index + size]


def main():
    base_url = os.environ.get("BASE_URL", "http://localhost:18084")
    artifact_dir = os.environ.get("ARTIFACT_DIR", "/tmp/aubb-perf-20260418")
    run_id = os.environ.get("RUN_ID", datetime.now(TZ).strftime("pf%m%d%H%M"))
    admin_user = os.environ.get("ADMIN_USERNAME", "perf-admin")
    admin_pass = os.environ.get("ADMIN_PASSWORD", "PerfAdmin!123")
    teacher_user = os.environ.get("TEACHER_USERNAME", f"{run_id}-teacher")
    teacher_pass = os.environ.get("TEACHER_PASSWORD", "Password123")
    student_pass = os.environ.get("STUDENT_PASSWORD", "Password123")
    student_count = int(os.environ.get("STUDENT_COUNT", "60"))
    active_submitters = min(int(os.environ.get("ACTIVE_SUBMITTERS", "20")), student_count)
    session = requests.Session()
    os.makedirs(artifact_dir, exist_ok=True)

    admin = Client(base_url, session, login(session, base_url, admin_user, admin_pass))
    root_id = find_school_root(admin.get("/api/v1/admin/org-units/tree").json())

    college_code = f"{run_id}-COL".upper()
    college = admin.post(
        "/api/v1/admin/org-units",
        json={"name": f"{run_id} 工程学院", "code": college_code, "type": "COLLEGE", "sortOrder": 1, "parentId": root_id},
    ).json()
    college_id = read_id(college)

    teacher = admin.post(
        "/api/v1/admin/users",
        json={
            "username": teacher_user,
            "displayName": "性能教师",
            "email": f"{teacher_user}@example.com",
            "password": teacher_pass,
            "primaryOrgUnitId": college_id,
            "identityAssignments": [],
            "accountStatus": "ACTIVE",
        },
    ).json()
    teacher_id = read_id(teacher)

    csv_buf = io.StringIO()
    writer = csv.writer(csv_buf)
    writer.writerow(["username", "displayName", "email", "password", "primaryOrgCode", "identities", "status"])
    students = []
    for idx in range(1, student_count + 1):
        username = f"{run_id}-stu-{idx:03d}"
        writer.writerow([username, f"性能学生{idx:03d}", f"{username}@example.com", student_pass, college_code, "", "ACTIVE"])
        students.append({"username": username, "password": student_pass})
    admin.post(
        "/api/v1/admin/users/import",
        params={"importType": "csv"},
        files={"file": ("users.csv", csv_buf.getvalue().encode("utf-8"), "text/csv")},
    )

    user_items = []
    page = 1
    while True:
        payload = admin.get(f"/api/v1/admin/users?page={page}&pageSize=200").json()
        items = payload.get("items", [])
        user_items.extend(items)
        if len(items) < 200:
            break
        page += 1
    student_map = {item["username"]: int(item["id"]) for item in user_items if item["username"].startswith(f"{run_id}-stu-")}

    now = datetime.now(TZ)
    term = admin.post(
        "/api/v1/admin/academic-terms",
        json={
            "termCode": f"{run_id.upper()}-TERM",
            "termName": f"{run_id} 压测学期",
            "schoolYear": "2025-2026",
            "semester": "SPRING",
            "startDate": (now - timedelta(days=30)).date().isoformat(),
            "endDate": (now + timedelta(days=90)).date().isoformat(),
        },
    ).json()
    catalog = admin.post(
        "/api/v1/admin/course-catalogs",
        json={
            "courseCode": f"{run_id.upper()}-CS101",
            "courseName": f"{run_id} 性能课程",
            "courseType": "REQUIRED",
            "credit": 3.0,
            "totalHours": 48,
            "departmentUnitId": college_id,
            "description": "压测专用课程",
        },
    ).json()
    offering = admin.post(
        "/api/v1/admin/course-offerings",
        json={
            "catalogId": read_id(catalog),
            "termId": read_id(term),
            "offeringCode": f"{run_id.upper()}-OFF",
            "offeringName": "压测开课",
            "primaryCollegeUnitId": college_id,
            "secondaryCollegeUnitIds": [],
            "deliveryMode": "HYBRID",
            "language": "ZH",
            "capacity": student_count + 20,
            "instructorUserIds": [teacher_id],
            "startAt": iso(now - timedelta(days=7)),
            "endAt": iso(now + timedelta(days=90)),
        },
    ).json()
    offering_id = read_id(offering)

    teacher_client = Client(base_url, session, login(session, base_url, teacher_user, teacher_pass))
    class_a = teacher_client.post(
        f"/api/v1/teacher/course-offerings/{offering_id}/classes",
        json={"classCode": f"{run_id.upper()}-A", "className": "压测 A 班", "entryYear": 2026, "capacity": student_count, "scheduleSummary": "周二 1-2 节"},
    ).json()
    class_b = teacher_client.post(
        f"/api/v1/teacher/course-offerings/{offering_id}/classes",
        json={"classCode": f"{run_id.upper()}-B", "className": "压测 B 班", "entryYear": 2026, "capacity": student_count, "scheduleSummary": "周四 3-4 节"},
    ).json()
    class_ids = [read_id(class_a), read_id(class_b)]

    member_rows = []
    for index, student in enumerate(students):
        member_rows.append({
            "userId": student_map[student["username"]],
            "memberRole": "STUDENT",
            "teachingClassId": class_ids[index % 2],
            "remark": "perf-seed",
        })
    for rows in batch(member_rows, 100):
        teacher_client.post(f"/api/v1/teacher/course-offerings/{offering_id}/members/batch", json={"members": rows})

    objective = teacher_client.post(
        f"/api/v1/teacher/course-offerings/{offering_id}/assignments",
        json={
            "title": "压测客观题",
            "description": "高频查询与提交压测",
            "teachingClassId": None,
            "openAt": iso(now - timedelta(days=1)),
            "dueAt": iso(now + timedelta(days=15)),
            "maxSubmissions": 50,
            "gradeWeight": 20,
            "paper": {"sections": [{"title": "客观题", "questions": [{"title": "复杂度单选", "prompt": "并查集复杂度", "questionType": "SINGLE_CHOICE", "score": 10, "options": [{"optionKey": "A", "content": "近似常数", "correct": True}, {"optionKey": "B", "content": "O(log n)", "correct": False}, {"optionKey": "C", "content": "O(n)", "correct": False}]}]}]},
        },
    ).json()
    write_objective = teacher_client.post(
        f"/api/v1/teacher/course-offerings/{offering_id}/assignments",
        json={
            "title": "压测写入客观题",
            "description": "仅用于提交接口压测",
            "teachingClassId": None,
            "openAt": iso(now - timedelta(days=1)),
            "dueAt": iso(now + timedelta(days=15)),
            "maxSubmissions": 500,
            "gradeWeight": 5,
            "paper": {"sections": [{"title": "客观题", "questions": [{"title": "复杂度单选", "prompt": "并查集复杂度", "questionType": "SINGLE_CHOICE", "score": 10, "options": [{"optionKey": "A", "content": "近似常数", "correct": True}, {"optionKey": "B", "content": "O(log n)", "correct": False}, {"optionKey": "C", "content": "O(n)", "correct": False}]}]}]},
        },
    ).json()
    judge_assignment = teacher_client.post(
        f"/api/v1/teacher/course-offerings/{offering_id}/assignments",
        json={
            "title": "压测编程题",
            "description": "评测轮询压测",
            "teachingClassId": read_id(class_a),
            "openAt": iso(now - timedelta(days=1)),
            "dueAt": iso(now + timedelta(days=15)),
            "maxSubmissions": 10,
            "judgeConfig": {"language": "PYTHON3", "timeLimitMs": 1000, "memoryLimitMb": 128, "outputLimitKb": 64, "testCases": [{"stdinText": "abc\\n", "expectedStdout": "cba\\n", "score": 60}, {"stdinText": "hello\\n", "expectedStdout": "olleh\\n", "score": 40}]},
        },
    ).json()
    objective_id = read_id(objective)
    write_assignment_id = read_id(write_objective)
    judge_assignment_id = read_id(judge_assignment)
    teacher_client.post(f"/api/v1/teacher/assignments/{objective_id}/publish")
    teacher_client.post(f"/api/v1/teacher/assignments/{write_assignment_id}/publish")
    teacher_client.post(f"/api/v1/teacher/assignments/{judge_assignment_id}/publish")

    first_student = Client(base_url, session, login(session, base_url, students[0]["username"], student_pass))
    question_id = int(first_student.get(f"/api/v1/me/assignments/{objective_id}").json()["paper"]["sections"][0]["questions"][0]["id"])
    write_question_id = int(first_student.get(f"/api/v1/me/assignments/{write_assignment_id}").json()["paper"]["sections"][0]["questions"][0]["id"])
    for student in students[:active_submitters]:
        token = login(session, base_url, student["username"], student_pass)
        Client(base_url, session, token).post(
            f"/api/v1/me/assignments/{objective_id}/submissions",
            json={"answers": [{"assignmentQuestionId": question_id, "selectedOptionKeys": ["A"]}]},
        )
    teacher_client.post(f"/api/v1/teacher/assignments/{objective_id}/grades/publish")

    student_gradebook = first_student.get(f"/api/v1/me/course-offerings/{offering_id}/gradebook").json()
    judge_submission = first_student.post(
        f"/api/v1/me/assignments/{judge_assignment_id}/submissions",
        json={"contentText": "print(input()[::-1])", "artifactIds": []},
    ).json()
    judge_submission_id = read_id(judge_submission)
    judge_job_id = None
    for _ in range(40):
        jobs = first_student.get(f"/api/v1/me/submissions/{judge_submission_id}/judge-jobs").json()
        if jobs and jobs[0]["status"] in {"SUCCEEDED", "FAILED", "CANCELLED"}:
            judge_job_id = int(jobs[0]["id"])
            break
        time.sleep(1)
    if judge_job_id is None:
        raise RuntimeError("等待评测任务完成超时")

    manifest = {
        "baseUrl": base_url,
        "runId": run_id,
        "artifactDir": artifact_dir,
        "admin": {"username": admin_user, "password": admin_pass},
        "teacher": {"username": teacher_user, "password": teacher_pass, "userId": teacher_id},
        "students": students,
        "offeringId": offering_id,
        "classIds": class_ids,
        "objectiveAssignmentId": objective_id,
        "objectiveQuestionId": question_id,
        "writeAssignmentId": write_assignment_id,
        "writeQuestionId": write_question_id,
        "judgeAssignmentId": judge_assignment_id,
        "judgeSubmissionId": judge_submission_id,
        "judgeJobId": judge_job_id,
        "studentGradebookSummary": student_gradebook.get("summary"),
    }
    with open(os.path.join(artifact_dir, "manifest.json"), "w", encoding="utf-8") as handle:
        json.dump(manifest, handle, ensure_ascii=False, indent=2)
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
