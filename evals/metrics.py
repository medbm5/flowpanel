"""Comparison helpers. Kept tolerant to formatting (case, accents, number formats), strict on values."""

import re
import unicodedata


def fold(s):
    s = unicodedata.normalize("NFD", str(s or "").lower())
    return "".join(c for c in s if unicodedata.category(c) != "Mn").replace("’", "'").strip()


def tokens(s):
    words = re.split(r"[^a-z0-9']+", fold(s))
    out = set()
    for w in words:
        if len(w) < 2 or w in {"de", "du", "des", "d", "le", "la", "les", "l"}:
            continue
        out.add(w[:-1] if len(w) > 3 and w.endswith(("s", "x")) else w)
    return out


def to_number(v):
    if v is None:
        return None
    if isinstance(v, (int, float)):
        return float(v)
    s = str(v).replace(" ", "").replace(" ", "").replace("€", "").strip()
    if "," in s:
        s = s.replace(".", "").replace(",", ".")
    try:
        return float(s)
    except ValueError:
        return None


def same_number(a, b, tol=0.005):
    x, y = to_number(a), to_number(b)
    if x is None or y is None:
        return (a in (None, "")) and (b in (None, ""))
    return abs(x - y) <= tol


def same_text(expected, actual, min_overlap=0.6):
    """Token overlap (relative to the expected value) — 'Soudeur MIG' matches 'Soudeurs MIG'."""
    e, a = tokens(expected), tokens(actual)
    if not e:
        return not a
    return len(e & a) / len(e) >= min_overlap


def same_list(expected, actual):
    norm = lambda s: {re.sub(r"[^a-z0-9]", "", fold(x)) for x in str(s or "").split(",") if x.strip()}
    return norm(expected) == norm(actual)


FIELD_COMPARATORS = {
    "position": same_text,
    "site": same_text,
    "replacedEmployee": same_text,
    "quantity": same_number,
    "weeklyHours": same_number,
    "hourlyRate": same_number,
    "startDate": lambda e, a: fold(e) == fold(a),
    "endDate": lambda e, a: fold(e) == fold(a),
    "legalReason": lambda e, a: fold(e) == fold(a),
    "overtimeAllowed": lambda e, a: fold(e) == fold(a),
    "requiredCertifications": same_list,
}


def ratio(ok, total):
    return round(ok / total, 4) if total else 1.0
