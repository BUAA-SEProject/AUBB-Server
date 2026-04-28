import http from "k6/http";
import { check, group, sleep } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";
const STUDENT_USERNAME = __ENV.STUDENT_USERNAME;
const STUDENT_PASSWORD = __ENV.STUDENT_PASSWORD;
const TEACHER_USERNAME = __ENV.TEACHER_USERNAME;
const TEACHER_PASSWORD = __ENV.TEACHER_PASSWORD;
const OFFERING_ID = __ENV.OFFERING_ID;
const ASSIGNMENT_ID = __ENV.ASSIGNMENT_ID;
const SUBMISSION_ID = __ENV.SUBMISSION_ID;
const JUDGE_JOB_ID = __ENV.JUDGE_JOB_ID;

export const options = {
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<300", "p(99)<800"],
  },
  scenarios: {
    student_read_peak: {
      executor: "ramping-arrival-rate",
      startRate: 50,
      timeUnit: "1s",
      preAllocatedVUs: 50,
      maxVUs: 800,
      stages: [
        { target: 200, duration: "2m" },
        { target: 600, duration: "5m" },
        { target: 900, duration: "3m" },
      ],
      exec: "studentReadFlow",
    },
    teacher_read_peak: {
      executor: "ramping-arrival-rate",
      startRate: 10,
      timeUnit: "1s",
      preAllocatedVUs: 20,
      maxVUs: 200,
      stages: [
        { target: 50, duration: "2m" },
        { target: 100, duration: "5m" },
      ],
      exec: "teacherReadFlow",
      startTime: "30s",
    },
  },
};

function login(username, password) {
  const response = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username, password }),
    { headers: { "Content-Type": "application/json" } }
  );
  check(response, { "login status is 200": (r) => r.status === 200 });
  return response.json("accessToken");
}

function authHeaders(token) {
  return {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  };
}

export function setup() {
  if (!STUDENT_USERNAME || !STUDENT_PASSWORD || !TEACHER_USERNAME || !TEACHER_PASSWORD) {
    throw new Error("缺少压测账号环境变量");
  }
  if (!OFFERING_ID || !ASSIGNMENT_ID || !SUBMISSION_ID || !JUDGE_JOB_ID) {
    throw new Error("缺少 offeringId / assignmentId / submissionId / judgeJobId 环境变量");
  }
  return {
    studentToken: login(STUDENT_USERNAME, STUDENT_PASSWORD),
    teacherToken: login(TEACHER_USERNAME, TEACHER_PASSWORD),
  };
}

export function studentReadFlow(data) {
  const params = authHeaders(data.studentToken);
  group("student-core-read", () => {
    check(http.get(`${BASE_URL}/api/v1/me/courses`, params), {
      "my courses 200": (r) => r.status === 200,
    });
    check(http.get(`${BASE_URL}/api/v1/me/assignments?offeringId=${OFFERING_ID}`, params), {
      "my assignments 200": (r) => r.status === 200,
    });
    check(
      http.get(`${BASE_URL}/api/v1/me/assignments/${ASSIGNMENT_ID}/submissions?page=1&pageSize=10`, params),
      { "my submissions 200": (r) => r.status === 200 }
    );
    check(http.get(`${BASE_URL}/api/v1/me/submissions/${SUBMISSION_ID}/judge-jobs`, params), {
      "my judge jobs 200": (r) => r.status === 200,
    });
    check(http.get(`${BASE_URL}/api/v1/me/judge-jobs/${JUDGE_JOB_ID}/report`, params), {
      "judge report 200": (r) => r.status === 200 || r.status === 404,
    });
    check(http.get(`${BASE_URL}/api/v1/me/course-offerings/${OFFERING_ID}/gradebook`, params), {
      "student gradebook 200": (r) => r.status === 200,
    });
    check(http.get(`${BASE_URL}/api/v1/me/notifications/unread-count`, params), {
      "unread count 200": (r) => r.status === 200,
    });
  });
  sleep(1);
}

export function teacherReadFlow(data) {
  const params = authHeaders(data.teacherToken);
  group("teacher-core-read", () => {
    check(http.get(`${BASE_URL}/api/v1/teacher/course-offerings/${OFFERING_ID}/assignments?page=1&pageSize=20`, params), {
      "teacher assignments 200": (r) => r.status === 200,
    });
    check(http.get(`${BASE_URL}/api/v1/teacher/assignments/${ASSIGNMENT_ID}/submissions?page=1&pageSize=20`, params), {
      "teacher submissions 200": (r) => r.status === 200,
    });
    check(http.get(`${BASE_URL}/api/v1/teacher/course-offerings/${OFFERING_ID}/gradebook?page=1&pageSize=20`, params), {
      "teacher gradebook 200": (r) => r.status === 200,
    });
  });
  sleep(1);
}
