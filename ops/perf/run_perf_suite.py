#!/usr/bin/env python3
import asyncio
import json
import os
import statistics
import subprocess
import time
from collections import Counter
from itertools import count
from pathlib import Path

import aiohttp


def percentile(values, q):
    if not values:
        return 0.0
    values = sorted(values)
    idx = min(len(values) - 1, max(0, int(round((len(values) - 1) * q))))
    return values[idx]


async def login(session, base_url, username, password):
    async with session.post(f"{base_url}/api/v1/auth/login", json={"username": username, "password": password}) as resp:
        data = await resp.json()
        return data["accessToken"]


async def sample_metrics(stop_event, artifact_dir, compose_prefix):
    samples = []
    while not stop_event.is_set():
        docker = subprocess.run(
            ["docker", "stats", "--no-stream", "--format", "{{json .}}"],
            capture_output=True,
            text=True,
            check=True,
        )
        containers = {}
        for line in docker.stdout.splitlines():
            if compose_prefix not in line:
                continue
            payload = json.loads(line)
            containers[payload["Name"]] = {"cpu": payload["CPUPerc"], "mem": payload["MemUsage"]}
        pg = subprocess.run(
            ["docker", "exec", f"{compose_prefix}-postgres-1", "psql", "-U", "aubb", "-d", "aubb", "-Atc", "select count(*) from pg_stat_activity where datname='aubb'"],
            capture_output=True,
            text=True,
            check=True,
        )
        samples.append({"ts": time.time(), "containers": containers, "pgConnections": int((pg.stdout or "0").strip() or 0)})
        await asyncio.sleep(5)
    Path(artifact_dir, "resource_samples.json").write_text(json.dumps(samples, ensure_ascii=False, indent=2), encoding="utf-8")


async def run_scenario(name, session, request_factory, concurrency, duration_s):
    latencies = []
    statuses = Counter()
    start = time.perf_counter()
    stop_at = start + duration_s

    async def worker(worker_id):
        while time.perf_counter() < stop_at:
            req = request_factory(worker_id)
            method = req.pop("method")
            url = req.pop("url")
            t0 = time.perf_counter()
            try:
                async with session.request(method, url, **req) as resp:
                    await resp.read()
                    statuses[str(resp.status)] += 1
                    latencies.append((time.perf_counter() - t0) * 1000)
            except Exception as exc:
                statuses[f"EXC:{type(exc).__name__}"] += 1
                latencies.append((time.perf_counter() - t0) * 1000)

    await asyncio.gather(*(worker(index) for index in range(concurrency)))
    total = sum(statuses.values())
    ok = sum(count for key, count in statuses.items() if key.startswith("2"))
    errors = total - ok
    elapsed = max(time.perf_counter() - start, 0.001)
    result = {
        "scenario": name,
        "concurrency": concurrency,
        "durationSeconds": duration_s,
        "totalRequests": total,
        "rps": round(total / elapsed, 2),
        "avgMs": round(statistics.fmean(latencies), 2) if latencies else 0.0,
        "p50Ms": round(percentile(latencies, 0.50), 2),
        "p95Ms": round(percentile(latencies, 0.95), 2),
        "p99Ms": round(percentile(latencies, 0.99), 2),
        "errorRate": round(errors / total, 4) if total else 0.0,
        "statuses": dict(statuses),
    }
    print(json.dumps(result, ensure_ascii=False))
    return result


async def main():
    manifest_path = os.environ.get("PERF_MANIFEST", "/tmp/aubb-perf-20260418/manifest.json")
    artifact_dir = os.environ.get("ARTIFACT_DIR", str(Path(manifest_path).parent))
    compose_prefix = os.environ.get("COMPOSE_PREFIX", "aubb-perf-20260418")
    scenario_filter = {item.strip() for item in os.environ.get("SCENARIOS", "").split(",") if item.strip()}
    results_name = os.environ.get("PERF_RESULTS_NAME", "perf_results.json")
    manifest = json.loads(Path(manifest_path).read_text(encoding="utf-8"))
    base_url = manifest["baseUrl"]
    student_password = manifest["students"][0]["password"]

    timeout = aiohttp.ClientTimeout(total=15)
    connector = aiohttp.TCPConnector(limit=4000, limit_per_host=4000)
    async with aiohttp.ClientSession(timeout=timeout, connector=connector) as session:
        student_tokens = []
        for student in manifest["students"]:
            token = await login(session, base_url, student["username"], student_password)
            student_tokens.append((student["username"], token))
        read_tokens = student_tokens[:20]
        teacher_token = await login(session, base_url, manifest["teacher"]["username"], manifest["teacher"]["password"])
        stop_event = asyncio.Event()
        sampler = asyncio.create_task(sample_metrics(stop_event, artifact_dir, compose_prefix))

        def auth_headers(token):
            return {"headers": {"Authorization": f"Bearer {token}"}}

        write_counter = count()
        scenarios = [
            ("login_smoke", lambda i: {"method": "POST", "url": f"{base_url}/api/v1/auth/login", "json": {"username": student_tokens[i % len(student_tokens)][0], "password": student_password}}, [(20, 15), (100, 20), (300, 20), (500, 20)]),
            ("auth_me", lambda i: {"method": "GET", "url": f"{base_url}/api/v1/auth/me", **auth_headers(read_tokens[i % len(read_tokens)][1])}, [(50, 20), (200, 20), (500, 20), (1000, 20)]),
            ("my_courses", lambda i: {"method": "GET", "url": f"{base_url}/api/v1/me/courses", **auth_headers(read_tokens[i % len(read_tokens)][1])}, [(50, 20), (200, 20), (500, 20), (1000, 20)]),
            ("my_assignments", lambda i: {"method": "GET", "url": f"{base_url}/api/v1/me/assignments?offeringId={manifest['offeringId']}&page=1&pageSize=10", **auth_headers(read_tokens[i % len(read_tokens)][1])}, [(50, 20), (200, 20), (500, 20), (1000, 20)]),
            ("my_submissions", lambda i: {"method": "GET", "url": f"{base_url}/api/v1/me/assignments/{manifest['objectiveAssignmentId']}/submissions?page=1&pageSize=10", **auth_headers(read_tokens[i % len(read_tokens)][1])}, [(50, 20), (200, 20), (500, 20)]),
            ("judge_jobs", lambda i: {"method": "GET", "url": f"{base_url}/api/v1/me/submissions/{manifest['judgeSubmissionId']}/judge-jobs", **auth_headers(read_tokens[0][1])}, [(50, 20), (200, 20), (500, 20)]),
            ("judge_report", lambda i: {"method": "GET", "url": f"{base_url}/api/v1/me/judge-jobs/{manifest['judgeJobId']}/report", **auth_headers(read_tokens[0][1])}, [(50, 20), (200, 20), (500, 20)]),
            ("student_gradebook", lambda i: {"method": "GET", "url": f"{base_url}/api/v1/me/course-offerings/{manifest['offeringId']}/gradebook", **auth_headers(read_tokens[i % len(read_tokens)][1])}, [(50, 20), (200, 20), (500, 20)]),
            ("teacher_gradebook", lambda i: {"method": "GET", "url": f"{base_url}/api/v1/teacher/course-offerings/{manifest['offeringId']}/gradebook?page=1&pageSize=20", **auth_headers(teacher_token)}, [(20, 20), (100, 20), (300, 20)]),
            ("write_submission", lambda i: {"method": "POST", "url": f"{base_url}/api/v1/me/assignments/{manifest['writeAssignmentId']}/submissions", **auth_headers(student_tokens[next(write_counter) % len(student_tokens)][1]), "json": {"answers": [{"assignmentQuestionId": manifest["writeQuestionId"], "selectedOptionKeys": ["A"]}]}}, [(10, 10), (30, 10), (50, 10), (100, 10)]),
        ]

        results = []
        for name, factory, stages in scenarios:
            if scenario_filter and name not in scenario_filter:
                continue
            for concurrency, duration_s in stages:
                result = await run_scenario(name, session, factory, concurrency, duration_s)
                results.append(result)
                if result["errorRate"] > 0.05 or result["p95Ms"] > 3000:
                    break

        stop_event.set()
        await sampler
    Path(artifact_dir).mkdir(parents=True, exist_ok=True)
    Path(artifact_dir, results_name).write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    asyncio.run(main())
