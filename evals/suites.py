"""The four eval suites. Each returns (metrics, failures)."""

import datetime
import json
import os
import re
import urllib.request
from pathlib import Path

from .metrics import FIELD_COMPARATORS, fold, ratio, same_number, same_text

DATASETS = Path(__file__).parent / "datasets"
YEAR = datetime.date.today().year


def load(name):
    with open(DATASETS / name, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]


def refs(text):
    """Dataset placeholders like {ref:142} → ORD-<year>-0142 (refs carry the current year)."""
    return re.sub(r"\{ref:(\d+)\}", lambda m: f"ORD-{YEAR}-{int(m.group(1)):04d}", text)


def run_intake(sessions, cases=None):
    cases = cases or load("intake.jsonl")
    ok = total = 0
    failures = []
    for case in cases:
        view = sessions["claire"].post("/intake/preview", {"emailText": case["email"]})
        fields = {f["name"]: f["value"] for f in view["fields"]}
        for name, expected in case["expected"].items():
            total += 1
            if FIELD_COMPARATORS[name](expected, fields.get(name)):
                ok += 1
            else:
                failures.append(f"{case['id']} {name}: expected {expected!r}, got {fields.get(name)!r}")
    return {"intake.field_accuracy": ratio(ok, total)}, failures


def run_invoice(sessions, cases=None):
    cases = cases or load("invoice.jsonl")
    lines_ok = lines_total = totals_ok = 0
    failures = []
    for case in cases:
        got = sessions["claire"].post("/invoices/extract", {"text": case["text"]})
        expected = case["expected"]
        for exp in expected["lines"]:
            lines_total += 1
            match = next((g for g in got["lines"] if same_text(exp["workerName"], g["workerName"], 1.0)), None)
            good = match is not None and all(same_number(exp[k], match[k], 0.011) for k in ("hours", "hourlyRate", "amount"))
            lines_ok += good
            if not good:
                failures.append(f"{case['id']} line {exp['workerName']}: expected {exp}, got {match}")
        if same_number(expected["totalExclTax"], got["totalExclTax"], 0.011):
            totals_ok += 1
        else:
            failures.append(f"{case['id']} total: expected {expected['totalExclTax']}, got {got['totalExclTax']}")
    return {"invoice.line_accuracy": ratio(lines_ok, lines_total), "invoice.total_accuracy": ratio(totals_ok, len(cases))}, failures


def run_rag(sessions, cases=None, judge=None):
    cases = cases or load("copilot_rag.jsonl")
    hit = answered = valid = cited = facts_ok = 0
    faithful = judged = 0
    failures = []
    for case in cases:
        answer = sessions[case["persona"]].post("/copilot/ask", {"question": case["question"]})
        sources = answer["sources"]
        citations = answer["citations"]
        if case["expect_not_found"]:
            answered += 1
            if answer["notFound"]:
                facts_ok += 1
                hit += 1
            else:
                failures.append(f"{case['id']}: expected 'not found', got {answer['answer']!r}")
            continue
        answered += 1
        titles = [s["documentTitle"] for s in sources]
        if any(fold(case["expected_document"]) == fold(t) for t in titles):
            hit += 1
        else:
            failures.append(f"{case['id']}: expected document {case['expected_document']!r} not retrieved ({titles})")
        numbers = {s["n"] for s in sources}
        for c in citations:
            cited += 1
            valid += c["n"] in numbers and fold(c["documentTitle"]) in {fold(t) for t in titles}
        text = fold(answer["answer"])
        if not answer["notFound"] and all(fold(fact) in text for fact in case["key_facts"]):
            facts_ok += 1
        else:
            failures.append(f"{case['id']}: missing facts {case['key_facts']} in {answer['answer']!r}")
        if judge:
            judged += 1
            faithful += judge(case["question"], answer["answer"], [s["excerpt"] for s in sources])
    metrics = {
        "rag.hit_rate": ratio(hit, answered),
        "rag.citation_validity": ratio(valid, cited),
        "rag.answer_accuracy": ratio(facts_ok, answered),
    }
    if judged:
        metrics["rag.faithfulness"] = ratio(faithful, judged)
    return metrics, failures


def run_tools(sessions, cases=None):
    cases = cases or load("copilot_tools.jsonl")
    tool_ok = value_ok = 0
    failures = []
    for case in cases:
        answer = sessions[case["persona"]].post("/copilot/ask", {"question": refs(case["question"])})
        names = [t["name"] for t in answer["toolCalls"]]
        if case["expected_tool"] in names:
            tool_ok += 1
        else:
            failures.append(f"{case['id']}: expected tool {case['expected_tool']}, got {names}")
        if fold(refs(case["expected_value"])) in fold(answer["answer"]):
            value_ok += 1
        else:
            failures.append(f"{case['id']}: expected {refs(case['expected_value'])!r} in {answer['answer']!r}")
    return {"tools.choice_accuracy": ratio(tool_ok, len(cases)), "tools.answer_accuracy": ratio(value_ok, len(cases))}, failures


def openai_judge():
    """Optional LLM-as-judge for faithfulness, only when OPENAI_API_KEY is set (1 = answer supported by the passages)."""
    key = os.environ.get("OPENAI_API_KEY")
    if not key:
        return None
    model = os.environ.get("EVAL_JUDGE_MODEL", "gpt-4o-mini")

    def judge(question, answer, passages):
        prompt = ("Question: " + question + "\nAnswer: " + answer + "\nPassages:\n" + "\n---\n".join(passages)
                  + "\nIs every factual claim of the answer supported by the passages (or does the answer correctly say it "
                    "was not found)? Reply with exactly YES or NO.")
        body = {"model": model, "max_tokens": 3, "temperature": 0, "messages": [{"role": "user", "content": prompt}]}
        req = urllib.request.Request("https://api.openai.com/v1/chat/completions", data=json.dumps(body).encode(),
                                     headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=60) as resp:
            text = json.loads(resp.read())["choices"][0]["message"]["content"]
        return 1 if text.strip().upper().startswith("YES") else 0

    return judge


SUITES = {
    "intake": run_intake,
    "invoice": run_invoice,
    "rag": run_rag,
    "tools": run_tools,
}
