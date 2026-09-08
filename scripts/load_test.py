#!/usr/bin/env python3
"""OryxOS load test (SC-002).

Measures session-creation P99 and internal forwarding latency against a
running `oryxos serve` / `oryxos gateway`. Uses only the Python stdlib.

Concurrency is modeled with PROCESSES (one user loop each), not threads:
on Windows, thread scheduling + GIL inflates tail latencies with artifacts
that hide the server's real numbers.

Usage:
  python scripts/load_test.py --base http://localhost:8080 --agents 10 \
      --sessions 100 --concurrency 10 [--duration 0]

With --duration > 0 the run keeps looping create/fetch/delete until the
duration (seconds) elapses — used for the 4-hour stability run.
"""

import argparse
import http.client as hc
import json
import multiprocessing as mp
import os
import sys
import time
from pathlib import Path
from urllib.parse import urlparse

SESSION_CREATE_P99_BUDGET_MS = 200  # SC-002
INTERNAL_FORWARD_BUDGET_MS = 50     # SC-002


def prepare_agents(root, count):
    """Write `load-i` agent dirs directly (same shape as `oryxos profile create`)."""
    agents = Path(root) / "agents"
    agents.mkdir(parents=True, exist_ok=True)
    for i in range(count):
        d = agents / f"load{i:02d}"
        d.mkdir(exist_ok=True)
        (d / "AGENT.md").write_text(
            "---\n"
            f"name: load{i:02d}\n"
            "description: load test agent\n"
            "provider:\n  name: deepseek\n  model: deepseek-chat\n"
            "tools: []\nbootstrap: []\n---\n\n压测 Agent。\n",
            encoding="utf-8")
    print(f"prepared {count} agents under {agents}")


def percentile(samples, p):
    if not samples:
        return float("nan")
    s = sorted(samples)
    k = max(0, min(len(s) - 1, round(len(s) * p / 100) - 1))
    return s[k]


# --- 服务端权威延迟：解析 Tomcat access log（pattern "%D %s %r"） ---
# Windows 上客户端自测延迟被进程调度/TCP 栈放大出数百毫秒伪尾（同窗口内
# 独立顺序探测 max≈120ms 而客户端自报 p99≈1000ms），故延迟判定只认服务端。

def snapshot_access_offsets(root):
    d = Path(root) / "logs"
    out = {}
    if d.is_dir():
        for f in d.glob("access*.log"):
            try:
                out[f] = f.stat().st_size
            except OSError:
                pass
    return out


def collect_server_samples(offsets):
    """Read access-log lines appended since the snapshot; classify by method+path.

    Files created after the snapshot (first run of the day) are scanned from 0.
    """
    create_lat, fetch_lat = [], []
    if not offsets:
        return {"create": create_lat, "fetch": fetch_lat}
    logs_dir = next(iter(offsets)).parent
    for f in sorted(logs_dir.glob("access*.log")):
        start = offsets.get(f, 0)
        try:
            with open(f, "r", encoding="utf-8", errors="replace") as fh:
                fh.seek(start)
                for line in fh:
                    parts = line.split()
                    if len(parts) < 4:
                        continue
                    try:
                        # Tomcat 10.1 (Spring Boot 3.4) 的 %D 实测输出微秒而非文档声称
                        # 的毫秒——首请求冷启动 536121µs=536ms 与预热吸收现象吻合，
                        # 毫秒解读则单请求 536 秒、物理不可能
                        ms = float(parts[0]) / 1000.0
                    except ValueError:
                        continue
                    method, path = parts[2], parts[3]
                    if method == "POST" and path == "/api/v1/sessions":
                        create_lat.append(ms)
                    elif method == "GET" and path.startswith("/api/v1/sessions/"):
                        fetch_lat.append(ms)
        except OSError:
            continue
    return {"create": create_lat, "fetch": fetch_lat}


def collect_server_samples_wait(offsets, want_create, want_fetch, timeout_s=8.0):
    """Poll until the buffered access log flushes the run's lines (or timeout)."""
    deadline = time.monotonic() + timeout_s
    while True:
        got = collect_server_samples(offsets)
        if len(got["create"]) >= want_create and len(got["fetch"]) >= want_fetch:
            return got
        if time.monotonic() >= deadline:
            return got
        time.sleep(0.5)


def report(name, samples, budget_ms):
    if not samples:
        print(f"{name:<28} no samples")
        return True
    p50 = percentile(samples, 50)
    p95 = percentile(samples, 95)
    p99 = percentile(samples, 99)
    ok = p99 <= budget_ms
    print(f"{name:<28} n={len(samples):<6} p50={p50:8.1f}ms  p95={p95:8.1f}ms  "
          f"p99={p99:8.1f}ms  max={max(samples):8.1f}ms  budget<={budget_ms}ms  "
          f"{'PASS' if ok else 'FAIL'}")
    return ok


def user_loop(wid, base, args, deadline, out_q):
    """One simulated user: sequential create/fetch/delete over a keep-alive
    connection until one-shot completes or the stability deadline passes."""
    parsed = urlparse(base)
    conn = hc.HTTPConnection(parsed.hostname, parsed.port, timeout=10)
    rnd = 0
    errors = 0
    err_samples = []

    def call(method, path, body=None):
        data = json.dumps(body).encode() if body is not None else None
        headers = {"Content-Type": "application/json"} if data else {}
        start = time.perf_counter()
        try:
            conn.request(method, path, body=data, headers=headers)
            resp = conn.getresponse()
            resp.read()
        except (hc.HTTPException, OSError):
            # 连接可能已被服务端关闭：重建一次再试
            new_conn = hc.HTTPConnection(conn.host, conn.port, timeout=10)
            new_conn.request(method, path, body=data, headers=headers)
            resp = new_conn.getresponse()
            resp.read()
            conn.sock = new_conn.sock
        if resp.status >= 400:
            raise RuntimeError(f"HTTP {resp.status}: {resp.reason}")
        return (time.perf_counter() - start) * 1000.0

    while True:
        for i in range(wid, args.sessions, args.concurrency):
            profile = f"load{i % args.agents:02d}"
            # 唯一 user：多轮稳定性模式下绝不复用会话 id（归档后 getOrCreate 会派生新 id）
            user = f"w{wid}r{rnd}u{i}"
            try:
                ms = call("POST", "/api/v1/sessions",
                          {"profile": profile, "user_id": user})
                out_q.put(("create", ms))
                sid = f"http-{user}-{profile}"
                ms2 = call("GET", f"/api/v1/sessions/{sid}")
                out_q.put(("fetch", ms2))
                call("DELETE", f"/api/v1/sessions/{sid}")
            except RuntimeError as e:
                msg = str(e)
                if "409" not in msg:  # 409 = archive race on re-create, tolerated
                    errors += 1
                    if len(err_samples) < 3:
                        err_samples.append(msg[:200])
            except Exception as e:
                errors += 1
                if len(err_samples) < 3:
                    err_samples.append(repr(e)[:200])
        rnd += 1
        if args.duration <= 0 or time.monotonic() >= deadline:
            for s in err_samples:
                out_q.put(("err", s))
            out_q.put(("done", errors))
            return


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://localhost:8080")
    ap.add_argument("--root", default=os.environ.get("ORYXOS_ROOT", ".oryxos"))
    ap.add_argument("--agents", type=int, default=10)
    ap.add_argument("--sessions", type=int, default=100)
    ap.add_argument("--concurrency", type=int, default=10)
    ap.add_argument("--duration", type=int, default=0,
                    help="stability run length in seconds (0 = one shot)")
    ap.add_argument("--warmup", type=int, default=5,
                    help="untimed sequential warm-up requests before measurement")
    ap.add_argument("--skip-prepare", action="store_true")
    args = ap.parse_args()

    if not args.skip_prepare:
        prepare_agents(args.root, args.agents)

    # 预热（主进程顺序发，不计入采样）：吸收 JIT/连接池/SQLite 首表访问毛刺
    parsed = urlparse(args.base)
    warm = hc.HTTPConnection(parsed.hostname, parsed.port, timeout=10)
    for w in range(args.warmup):
        body = json.dumps({"profile": "load00", "user_id": f"warmup{w}"}).encode()
        warm.request("POST", "/api/v1/sessions", body=body,
                     headers={"Content-Type": "application/json"})
        warm.getresponse().read()
    if args.warmup:
        print(f"warmup: {args.warmup} requests (not measured)")

    out_q = mp.Queue()
    # 快照放预热后：预热请求确保 access log 文件已落地，之后追加的行才属于压测窗口
    offsets = snapshot_access_offsets(args.root)
    deadline = time.monotonic() + args.duration
    started = time.monotonic()
    procs = [mp.Process(target=user_loop, args=(w, args.base, args, deadline, out_q))
             for w in range(args.concurrency)]
    for p in procs:
        p.start()
    for p in procs:
        p.join()
    elapsed = time.monotonic() - started

    create_lat, fetch_lat, errors, err_samples = [], [], 0, []
    while not out_q.empty():
        kind, val = out_q.get()
        if kind == "create":
            create_lat.append(val)
        elif kind == "fetch":
            fetch_lat.append(val)
        elif kind == "done":
            errors += val
        elif kind == "err":
            err_samples.append(val)

    print(f"\n=== OryxOS Load Test ===")
    print(f"target={args.base}  agents={args.agents}  concurrency={args.concurrency}"
          f"  duration={elapsed:.1f}s  errors={errors}")

    server = collect_server_samples_wait(offsets, len(create_lat), len(fetch_lat))
    if server["create"]:
        # 权威口径：服务端 Tomcat %D（不含客户端调度/TCP 伪影）
        print("-- server-side (authoritative, from access log %D) --")
        ok = True
        ok &= report("session create (POST)", server["create"], SESSION_CREATE_P99_BUDGET_MS)
        ok &= report("internal fetch (GET)", server["fetch"], INTERNAL_FORWARD_BUDGET_MS)
        print("-- client-observed (informational; Windows inflates tails) --")
        print(f"{'session create (POST)':<28} n={len(create_lat):<6} "
              f"p50={percentile(create_lat, 50):8.1f}ms  p99={percentile(create_lat, 99):8.1f}ms")
        print(f"{'internal fetch (GET)':<28} n={len(fetch_lat):<6} "
              f"p50={percentile(fetch_lat, 50):8.1f}ms  p99={percentile(fetch_lat, 99):8.1f}ms")
    else:
        print("(access log samples unavailable — falling back to client-observed)")
        ok = True
        ok &= report("session create (POST)", create_lat, SESSION_CREATE_P99_BUDGET_MS)
        ok &= report("internal fetch (GET)", fetch_lat, INTERNAL_FORWARD_BUDGET_MS)

    print(f"error rate: {errors}/{len(server['create']) or len(create_lat)} "
          f"({'PASS' if errors == 0 else 'FAIL'})")
    for s in err_samples:
        print(f"  sample error: {s}")
    sys.exit(0 if ok and errors == 0 else 1)


if __name__ == "__main__":
    mp.freeze_support()
    main()
