# -*- coding: utf-8 -*-
"""I2: clean challenge-02 sources and import into the Reverse Tutor global library.

Usage:
  py scripts/import_challenge02.py            # dry-run: cleaning stats only
  py scripts/import_challenge02.py --apply    # replace same-source_uri docs, then import
"""
import json
import re
import sys
import urllib.error
import urllib.request
from pathlib import Path

API = "http://127.0.0.1:8100"
ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "activities" / "challenge-02-gamer-health" / "sources"
CLEAN = SRC / "cleaned"
NL = chr(10)

DROP_EXACT = {"正文", "您当前的位置：", "您当前的位置:", ">>", ">>>", "上一篇", "下一篇"}
DROP_SUBSTR = ["字体：大中小", "字体:大中小", "点击次数：", "点击次数:",
               "分享本页", "打印本页", "关闭本页", "分享到", "扫一扫在手机打开"]
CAPTION_PUNCT = re.compile("[。，、；：？！,.;:?!「」]")
IMG_LINE = re.compile("^[0-9]+[.](jpg|jpeg|png|gif|webp)$", re.I)


def parse_source(text):
    lines = text.split(NL)
    header = {}
    body_start = 0
    if lines and lines[0].strip() == "---":
        for i in range(1, len(lines)):
            if lines[i].strip() == "---":
                body_start = i + 1
                break
            raw = lines[i].strip()
            for sep in ("：", ":"):
                if sep in raw:
                    k, v = raw.split(sep, 1)
                    header[k.strip()] = v.strip()
                    break
    return header, NL.join(lines[body_start:])


def is_md_link_line(line):
    return line.startswith("[") and line.endswith(")") and "](" in line and line.index("](") > 1


def clean_body(body):
    out = []
    removed = {"md_link": 0, "img": 0, "caption": 0, "boiler": 0}
    prev_img = False
    for raw in body.split(NL):
        line = raw.strip()
        if not line:
            out.append("")
            continue
        if is_md_link_line(line):
            removed["md_link"] += 1
            continue
        if IMG_LINE.match(line):
            removed["img"] += 1
            prev_img = True
            continue
        if prev_img and len(line) <= 20 and not CAPTION_PUNCT.search(line):
            removed["caption"] += 1
            prev_img = False
            continue
        prev_img = False
        if line in DROP_EXACT or any(s in line for s in DROP_SUBSTR):
            removed["boiler"] += 1
            continue
        out.append(line)
    text = NL.join(out)
    while NL + NL + NL in text:
        text = text.replace(NL + NL + NL, NL + NL)
    return text.strip(), removed


def http_json(method, url, payload=None):
    data = None
    headers = {"Content-Type": "application/json"}
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, {"error": e.read().decode("utf-8", "replace")[:500]}


def main():
    apply = "--apply" in sys.argv
    files = sorted(p for p in SRC.glob("src-*.md") if p.is_file())
    print("API=%s apply=%s files=%d" % (API, apply, len(files)))
    planned = []
    for p in files:
        text = p.read_text(encoding="utf-8")
        header, body = parse_source(text)
        cleaned, removed = clean_body(body)
        prov = "来源：%s｜发布日期：%s｜原文：%s" % (
            header.get("来源", "?"), header.get("发布日期", "?"), header.get("原始URL", ""))
        content = prov + NL + NL + cleaned
        title = header.get("标题") or p.stem
        uri = header.get("原始URL", "")
        planned.append({"path": p, "title": title, "uri": uri, "content": content,
                        "removed": removed, "orig_len": len(body), "clean_len": len(cleaned)})
        print("- %s | %s | %d -> %d chars | removed %s" % (
            p.name, title[:28], len(body), len(cleaned), removed))
    if not apply:
        print("DRY-RUN done; rerun with --apply to import")
        return
    CLEAN.mkdir(exist_ok=True)
    status, docs = http_json("GET", API + "/api/documents")
    if status != 200:
        print("GET /api/documents failed:", status, docs)
        sys.exit(1)
    existing_by_uri = {d.get("source_uri"): d for d in docs if d.get("source_uri")}
    results = []
    for item in planned:
        old = existing_by_uri.get(item["uri"])
        if old:
            s, _resp = http_json("DELETE", API + "/api/documents/%s" % old["id"])
            print("delete old doc id=%s -> %s" % (old["id"], s))
        payload = {"title": item["title"], "source_type": "md",
                   "source_uri": item["uri"], "content": item["content"]}
        s, resp = http_json("POST", API + "/api/documents", payload)
        if s not in (200, 201):
            print("POST FAILED %s: %s %s" % (item["path"].name, s, resp))
            continue
        out = CLEAN / (item["path"].stem + ".clean.md")
        out.write_text(item["content"], encoding="utf-8")
        results.append({"doc_id": resp.get("id"), "title": item["title"],
                        "chunk_count": resp.get("chunk_count"), "src": item["path"].name})
        print("imported id=%s chunks=%s %s" % (resp.get("id"), resp.get("chunk_count"), item["title"][:28]))
    total = sum(r["chunk_count"] or 0 for r in results)
    print("OK docs=%d chunks=%d" % (len(results), total))


if __name__ == "__main__":
    main()
