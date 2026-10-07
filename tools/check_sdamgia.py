# -*- coding: utf-8 -*-
"""Проверка исправленного разбора: блок ищется с учётом вложенных тегов."""
import re
import sys
import time
import requests

UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
      "(KHTML, like Gecko) Chrome/122.0 Safari/537.36")
H = {"User-Agent": UA, "Accept": "text/html,application/xhtml+xml",
     "Accept-Language": "ru-RU,ru;q=0.9"}
ZERO = dict.fromkeys(map(ord, "\u00ad\u200b\u200c\u200d\ufeff"), None)


def get(url, referer=None):
    h = dict(H)
    if referer:
        h["Referer"] = referer
    try:
        r = requests.get(url, headers=h, timeout=25)
        return r.text if r.status_code == 200 else "__HTTP_%d__" % r.status_code
    except Exception as e:
        return "__ERR_%s__" % type(e).__name__


def strip_tags(html):
    """Убирает теги, корректно пропуская '>' внутри значений атрибутов."""
    out = []
    i = 0
    n = len(html)
    while i < n:
        c = html[i]
        if c != "<":
            out.append(c)
            i += 1
            continue
        j = i + 1
        quote = None
        while j < n:
            ch = html[j]
            if quote:
                if ch == quote:
                    quote = None
            elif ch in "\"'":
                quote = ch
            elif ch == ">":
                break
            j += 1
        if j >= n:                       # незакрытый тег — дальше не текст
            break
        out.append(" ")
        i = j + 1
    return "".join(out)


ENT = {"&nbsp;": " ", "&quot;": '"', "&#039;": "'", "&apos;": "'", "&laquo;": "«",
       "&raquo;": "»", "&mdash;": "—", "&ndash;": "–", "&minus;": "−",
       "&deg;": "°", "&amp;": "&", "&lt;": "<", "&gt;": ">", "&shy;": ""}


def text(html):
    s = re.sub(r"<(script|style)[^>]*>.*?</\1>", " ", html, flags=re.S | re.I)
    s = strip_tags(s)
    for a, b in ENT.items():
        s = s.replace(a, b)
    s = re.sub(r"&#(\d+);", lambda m: chr(int(m.group(1))), s)
    s = re.sub(r"&#x([0-9a-fA-F]+);", lambda m: chr(int(m.group(1), 16)), s)
    s = s.translate(ZERO).replace("\u00a0", " ")
    return re.sub(r"\s+", " ", s).strip()


def block(html, marker):
    """Содержимое элемента с данным class, с учётом вложенности одноимённых тегов."""
    at = html.find(marker)
    if at < 0:
        return None
    open_at = html.rfind("<", 0, at)
    if open_at < 0:
        return None
    tag_end = html.find(">", at)
    if tag_end < 0:
        return None
    m = re.match(r"<\s*([a-zA-Z0-9]+)", html[open_at:])
    tag = (m.group(1).lower() if m else "div")
    if html[open_at:tag_end + 1].rstrip().endswith("/>"):
        return ""
    open_re = re.compile(r"<\s*" + tag + r"[\s>/]", re.I)
    close_re = re.compile(r"</\s*" + tag + r"\s*>", re.I)
    i = tag_end + 1
    depth = 1
    while i < len(html):
        no = open_re.search(html, i)
        nc = close_re.search(html, i)
        if nc is None:
            return html[tag_end + 1:]
        if no is not None and no.start() < nc.start():
            gt = html.find(">", no.start())
            self_closed = gt > 0 and html[no.start():gt + 1].rstrip().endswith("/>")
            if not self_closed:
                depth += 1
            i = (gt + 1) if gt > 0 else no.start() + 1
        else:
            depth -= 1
            if depth == 0:
                return html[tag_end + 1:nc.start()]
            i = nc.end()
    return html[tag_end + 1:]


def catalog(domain):
    html = get("https://%s.sdamgia.ru/prob_catalog" % domain, "https://%s.sdamgia.ru/" % domain)
    if html.startswith("__"):
        return [], html
    out, seen = [], set()
    for m in re.finditer(r'href="[^"]*category_id=(\d+)[^"]*"[^>]*>(.*?)</a>', html, re.S):
        cid, name = m.group(1), text(m.group(2))
        if cid in seen or not name or len(name) > 90:
            continue
        seen.add(cid)
        out.append((name, cid))
    return out, "ok"


def problem_ids(domain, cid, limit):
    html = get("https://%s.sdamgia.ru/test?filter=all&category_id=%s" % (domain, cid),
               "https://%s.sdamgia.ru/prob_catalog" % domain)
    if html.startswith("__"):
        return []
    ids, seen = [], set()
    for m in re.finditer(r"problem\?id=(\d+)", html):
        pid = m.group(1)
        if pid in seen:
            continue
        seen.add(pid)
        if len(ids) < limit:
            ids.append(pid)
    return ids


def norm_answer(raw):
    return re.sub(r"^Ответ\s*:?\s*", "", text(raw)).strip()


def is_cell_friendly(a):
    if any(c in a for c in "<> ="):
        return False
    n = "".join(c for c in a.lower() if c.isalnum() or c in "-.")
    return bool(n) and len(n) <= 12


def acceptable(cond, ans):
    if not ans or len(ans) > 12:
        return False, "answer=%r" % ans[:30]
    if any(c in ans for c in "{}[]<>|"):
        return False, "answer chars"
    if not is_cell_friendly(ans):
        return False, "not cell friendly"
    if len(cond) < 15 or len(cond) > 900:
        return False, "cond=%d" % len(cond)
    if (ans.count(",") + ans.count(".")) > 0 and len(ans) > 6:
        return False, "segmented"
    return True, "ok"


def problem(domain, pid):
    url = "https://%s.sdamgia.ru/problem?id=%s" % (domain, pid)
    html = get(url, "https://%s.sdamgia.ru/prob_catalog" % domain)
    if html.startswith("__"):
        return None, html
    start = html.find("prob_maindiv")
    if start < 0:
        return None, "no prob_maindiv"
    scope = html[start:]
    cond_raw = block(scope, 'class="pbody"')
    if cond_raw is None:
        return None, "no pbody"
    ans_raw = block(scope, 'class="answer"')
    if ans_raw is None:
        return None, "no answer"
    sol_raw = block(scope, 'class="solution"')
    return {
        "id": pid, "url": url,
        "condition": text(cond_raw),
        "answer": norm_answer(ans_raw),
        "solution": text(sol_raw)[:80] if sol_raw else "",
    }, "ok"


def probe(domain, want=8):
    cats, note = catalog(domain)
    print("== %s: каталог %d тем (%s)" % (domain, len(cats), note))
    print("   первые темы: " + " | ".join(n for n, _ in cats[:4]))
    if not cats:
        return
    good, tried, shown = 0, 0, 0
    for name, cid in cats[:6]:
        ids = problem_ids(domain, cid, 4)
        for pid in ids:
            tried += 1
            task, why = problem(domain, pid)
            if task is None:
                if shown < 3:
                    print("   id=%s ошибка: %s" % (pid, why))
                    shown += 1
                continue
            ok, reason = acceptable(task["condition"], task["answer"])
            if ok:
                good += 1
            if shown < 8 and (ok or tried < 12):
                print("   %s id=%s ans=%r cond=%d | %s" %
                      ("OK " if ok else "нет", pid, task["answer"],
                       len(task["condition"]), reason))
                shown += 1
            if tried >= want * 2:
                break
        if tried >= want * 2:
            break
        time.sleep(0.3)
    print("   итог: подходит %d из %d проверенных" % (good, tried))


if __name__ == "__main__":
    for d in sys.argv[1:]:
        probe(d)
