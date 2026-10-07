# -*- coding: utf-8 -*-
"""I2 KG extraction: LLM extracts concept/method/source_topic nodes + relation edges
from challenge-02 library documents into kg_nodes/kg_edges (sid=kb:challenge-02).

Usage:
  py scripts/kg_extract_challenge02.py --only 1     # single doc smoke test
  py scripts/kg_extract_challenge02.py              # full run, resumable via progress file
"""
import json
import os
import re
import sys
import time
import urllib.request
import urllib.error
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
os.chdir(ROOT)
os.environ.setdefault("DB_URL", "sqlite:///F:/xw/reverse-tutor/reverse_tutor.db")

from dotenv import load_dotenv
load_dotenv(ROOT / ".env")

sys.path.insert(0, str(ROOT))
import db as dbmod

SID = "kb:challenge-02"
PROGRESS = ROOT / ".aily_tmp_kg_progress.jsonl"
DONE = ROOT / ".aily_tmp_kg_done.txt"
ALLOWED_KINDS = {"concept", "method", "source_topic"}

SYS = "你是一个知识图谱抽取器，从健康科普文档中抽取结构化知识，只输出 JSON。"

PROMPT = (
    "文档标题：{title}"
    + chr(10) + "文档内容：" + chr(10) + "{content}" + chr(10)
    + "请从上面这篇面向游戏人群的健康科普文档中抽取知识图谱。"
    + "输出一个 JSON 对象，包含 nodes 和 edges 两个数组。" + chr(10)
    + "要求：" + chr(10)
    + "1. nodes 每项是对象，字段为 name(名称)、kind(类型)、summary(一句话说明,30字内)。"
    + "kind 只能是 concept(概念/术语/状态)、method(可执行的方法/动作/习惯)、source_topic(本文主题, 仅1个)。"
    + chr(10)
    + "2. 节点名 2-12 个汉字，用规范术语；每篇 nodes 不超过 12 个，其中 source_topic 恰好 1 个。"
    + chr(10)
    + "3. edges 每项是对象，字段为 source(起点节点名)、target(终点节点名)、relation(关系)，"
    + "relation 用 2-6 字中文短语（如 导致、缓解、属于、建议、纠正、包含、加重、改善）；"
    + "concept/method 节点应尽量通过属于关系连到 source_topic；edges 不超过 15 条。" + chr(10)
    + "4. 不要抽取人名、机构名、网址；只输出 JSON，不要解释。"
)


def llm_extract(title, content, key, base, model):
    body = {
        "model": model,
        "messages": [
            {"role": "system", "content": SYS},
            {"role": "user", "content": PROMPT.replace("{title}", title).replace("{content}", content[:6000])},
        ],
        "response_format": {"type": "json_object"},
        "temperature": 0.2,
        "max_tokens": 1500,
    }
    req = urllib.request.Request(
        base.rstrip("/") + "/chat/completions",
        data=json.dumps(body, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json", "Authorization": "Bearer " + key},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=60) as r:
        data = json.loads(r.read().decode("utf-8"))
    txt = data["choices"][0]["message"]["content"]
    return json.loads(txt)


def norm_name(s):
    return re.sub("[\s　]+", "", (s or "").strip())


def main():
    only = None
    if "--only" in sys.argv:
        only = int(sys.argv[sys.argv.index("--only") + 1])
    key = os.getenv("LLM_API_KEY", "").strip()
    base = os.getenv("LLM_BASE_URL", "https://api.deepseek.com/v1")
    model = os.getenv("LLM_MODEL", "deepseek-chat")
    if not key:
        print("LLM_API_KEY missing")
        sys.exit(1)
    done_docs = set()
    if PROGRESS.exists():
        for line in PROGRESS.read_text(encoding="utf-8").splitlines():
            try:
                done_docs.add(json.loads(line)["doc_id"])
            except Exception:
                pass
    with dbmod.SessionLocal() as d:
        docs = [doc for doc in dbmod.list_documents(d, session_id=None)]
        docs.sort(key=lambda x: x.id)
        stats = {"nodes": 0, "edges": 0, "docs": 0, "skipped": 0, "errors": 0}
        for doc in docs:
            if only is not None and doc.id != only:
                continue
            if doc.id in done_docs and only is None:
                stats["skipped"] += 1
                continue
            try:
                res = llm_extract(doc.title, doc.content_text, key, base, model)
            except Exception as e:
                stats["errors"] += 1
                print("doc %s LLM ERR %s: %s" % (doc.id, type(e).__name__, e))
                continue
            id_by_name = {}
            n_nodes = 0
            for node in (res.get("nodes") or [])[:12]:
                kind = (node.get("kind") or "").strip()
                name = norm_name(node.get("name"))
                if kind not in ALLOWED_KINDS or not name or len(name) > 14:
                    continue
                props = {"summary": (node.get("summary") or "")[:80],
                         "source_doc_id": doc.id, "source_title": doc.title}
                kn = dbmod.upsert_kg_node(d, SID, kind, name, properties=props)
                if kn is not None:
                    id_by_name[name] = kn.id
                    n_nodes += 1
            n_edges = 0
            for edge in (res.get("edges") or [])[:15]:
                s = id_by_name.get(norm_name(edge.get("source")))
                t = id_by_name.get(norm_name(edge.get("target")))
                rel = (edge.get("relation") or "").strip()[:12]
                if not s or not t or not rel or s == t:
                    continue
                ke = dbmod.upsert_kg_edge(d, SID, s, t, rel,
                                          properties={"source_doc_id": doc.id})
                if ke is not None:
                    n_edges += 1
            d.commit()
            stats["nodes"] += n_nodes
            stats["edges"] += n_edges
            stats["docs"] += 1
            with PROGRESS.open("a", encoding="utf-8") as f:
                f.write(json.dumps({"doc_id": doc.id, "nodes": n_nodes,
                                    "edges": n_edges, "title": doc.title[:30]},
                                   ensure_ascii=False) + chr(10))
            print("doc %s -> nodes=%d edges=%d %s" % (doc.id, n_nodes, n_edges, doc.title[:30]))
        if only is None:
            DONE.write_text(json.dumps(stats, ensure_ascii=False), encoding="utf-8")
        print("STATS " + json.dumps(stats, ensure_ascii=False))


if __name__ == "__main__":
    main()
