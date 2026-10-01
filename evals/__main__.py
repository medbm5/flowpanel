"""CLI: python -m evals run --suite all --base-url http://localhost:8080

Exit code 0 when every metric meets its threshold, 1 otherwise, 2 on a runtime error.
"""

import argparse
import datetime
import json
import os
import subprocess
import sys
from pathlib import Path

from .client import ApiError, Sessions
from .suites import SUITES, openai_judge

HERE = Path(__file__).parent


def load_thresholds(path):
    text = Path(path).read_text(encoding="utf-8")
    try:
        import yaml  # optional dependency

        return {k: float(v) for k, v in (yaml.safe_load(text) or {}).items()}
    except ImportError:
        result = {}
        for line in text.splitlines():
            line = line.split("#", 1)[0].strip()
            if ":" in line:
                key, value = line.split(":", 1)
                result[key.strip().strip('"')] = float(value)
        return result


def git_sha():
    if os.environ.get("GITHUB_SHA"):
        return os.environ["GITHUB_SHA"]
    try:
        return subprocess.check_output(["git", "rev-parse", "HEAD"], text=True, stderr=subprocess.DEVNULL).strip()
    except (OSError, subprocess.CalledProcessError):
        return "local"


def run(args):
    sessions = Sessions(args.base_url)
    admin = sessions["admin"]
    if not args.no_reset:
        admin.post("/admin/demo/reset")
    names = list(SUITES) if args.suite == "all" else [s.strip() for s in args.suite.split(",")]
    judge = openai_judge() if args.judge else None
    metrics, failures = {}, {}
    for name in names:
        fn = SUITES[name]
        result, fails = fn(sessions, judge=judge) if name == "rag" else fn(sessions)
        metrics.update(result)
        failures[name] = fails
        print(f"[{name}] " + ", ".join(f"{k}={v:.3f}" for k, v in result.items()))
        for f in fails[: args.show_failures]:
            print(f"    - {f}")

    thresholds = load_thresholds(args.thresholds)
    checks = []
    for key, minimum in thresholds.items():
        if key in metrics:
            checks.append({"metric": key, "value": metrics[key], "threshold": minimum, "passed": metrics[key] >= minimum})
    passed = all(c["passed"] for c in checks)
    profile = admin.get("/admin/metrics/overview")["summary"]["profile"]
    report = {
        "createdAt": datetime.datetime.now(datetime.timezone.utc).isoformat(),
        "gitSha": git_sha(),
        "profile": profile,
        "baseUrl": args.base_url,
        "passed": passed,
        "metrics": metrics,
        "thresholds": thresholds,
        "checks": checks,
        "failures": failures,
    }
    Path(args.report).write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(f"\nReport written to {args.report}")
    for c in checks:
        mark = "PASS" if c["passed"] else "FAIL"
        print(f"  {mark}  {c['metric']:<26} {c['value']:.3f} (min {c['threshold']:.2f})")
    if not args.no_post:
        admin.post("/admin/evals/runs", {"gitSha": report["gitSha"], "profile": profile, "passed": passed,
                                         "metrics": metrics, "thresholds": thresholds})
        print("Results posted to /admin/evals/runs")
    print("\nEVALS PASSED" if passed else "\nEVALS FAILED: at least one metric is below its threshold")
    return 0 if passed else 1


def main(argv=None):
    parser = argparse.ArgumentParser(prog="python -m evals")
    sub = parser.add_subparsers(dest="command", required=True)
    r = sub.add_parser("run", help="run eval suites against a backend")
    r.add_argument("--suite", default="all", help="all, or a comma list of: " + ", ".join(SUITES))
    r.add_argument("--base-url", default=os.environ.get("EVAL_BASE_URL", "http://localhost:8080"))
    r.add_argument("--thresholds", default=str(HERE / "thresholds.yaml"))
    r.add_argument("--report", default=str(HERE / "report.json"))
    r.add_argument("--no-post", action="store_true", help="do not post the results to the admin dashboard")
    r.add_argument("--no-reset", action="store_true", help="do not reset the demo data first")
    r.add_argument("--judge", action="store_true", help="LLM-as-judge faithfulness (needs OPENAI_API_KEY)")
    r.add_argument("--show-failures", type=int, default=5)
    args = parser.parse_args(argv)
    try:
        return run(args)
    except (ApiError, OSError) as e:
        print(f"Eval run error: {e}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
