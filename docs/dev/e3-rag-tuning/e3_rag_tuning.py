# -*- coding: utf-8 -*-
"""1e-e3 RAG 调优回归（反转家教）。

- 忠实移植 mobile-native SourceChunker（先跑 Kotlin 单测同款用例自检）
- 百炼 qwen3.7-text-embedding（compatible-mode，免费额度）对真实资料集回归
- 评估：MinVectorScore 阈值扫描 × chunk 参数三档 × 关键词回退质量
- key 从 F:\\ComfyUI\\video_pipeline\\config.json 读取，不进本文件
"""
import sys
sys.stdout.reconfigure(encoding="utf-8", errors="replace")
import json, math, re, time, urllib.request, urllib.error
from pathlib import Path

HERE = Path(__file__).resolve().parent
MODEL = "qwen3.7-text-embedding"
CORPUS_PATH = HERE / "corpus.json"
RESULTS_PATH = HERE / "e3_results.json"
KEY_CONFIG = Path(r"F:\ComfyUI\video_pipeline\config.json")

# ---------------- SourceChunker 忠实移植 ----------------
SENT_BOUND = re.compile(r"(?<=[。！？；…：!?;])")
PARA_BOUND = re.compile(r"\n{2,}")

def _split_sentences(text):
    return [s.strip("\n \t") for s in SENT_BOUND.split(text) if s.strip("\n \t")]

def chunk_text(text, target=500, overlap=50, min_tail=80):
    paragraphs = [p.strip("\n") for p in PARA_BOUND.split(text)]
    paragraphs = [p for p in paragraphs if p.strip()]
    if not paragraphs:
        out = [text] if text.strip() else []
        return out or [text]
    chunks_all = []
    for para in paragraphs:
        chunks_all.extend(_chunk_paragraph(para, target, overlap, min_tail))
    return chunks_all

def _chunk_paragraph(para, target, overlap, min_tail):
    if len(para) <= target:
        return [para]
    chunks = []
    buf = ""

    def close():
        nonlocal buf
        c = buf.strip()
        buf = ""
        if c:
            chunks.append(c)
            buf = c[max(0, len(c) - overlap):]

    for sentence in _split_sentences(para):
        pieces = [sentence[i:i + target] for i in range(0, len(sentence), target)] if len(sentence) > target else [sentence]
        for piece in pieces:
            if buf and len(buf) + len(piece) > target:
                close()
            buf += piece
    if buf.strip():
        close()
    if len(chunks) >= 2 and len(chunks[-1]) < min_tail:
        tail = chunks.pop()
        chunks[-1] = chunks[-1] + tail
    return chunks

def selfcheck():
    # 与 SourceChunkerTest 同款断言
    assert chunk_text("Alpha\n\nBeta") == ["Alpha", "Beta"]
    assert chunk_text("第一段\n\n\n\n\n第二段") == ["第一段", "第二段"]
    assert chunk_text("   ") == ["   "]
    assert chunk_text("字" * 500) == ["字" * 500]
    s = "这是一个完整的句子，用来填充长度。" * 40
    ch = chunk_text(s)
    assert len(ch) >= 2 and len("".join(ch)) >= len(s) - 40
    ch = chunk_text("短句。" * 200)
    assert len(ch) >= 2 and ch[1].startswith(ch[0][-50:])
    t = "字" * 951
    ch = chunk_text(t)
    assert len(ch) == 2 and len(ch[0]) == 500 and len(ch[1]) == 501 and ch[0] + ch[1][50:] == t
    t = "大" * 500 + "。" + "中" * 400 + "。" + "尾"
    ch = chunk_text(t)
    assert len(ch) == 2 and ch[-1].endswith("尾")
    t = "甲" * 600 + "\n\n" + "乙" * 600
    ch = chunk_text(t)
    assert len(ch) == 4 and all(c == "甲" for c in ch[0]) and all(c == "乙" for c in ch[3])
    t = "前" + "中" * 900 + "后"
    assert len("".join(chunk_text(t))) >= len(t)
    print("[selfcheck] SourceChunker 移植自检 10/10 通过", flush=True)

# ---------------- 检索逻辑移植（与 SourceContextPortAdapter 对齐） ----------------
def cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a)); nb = math.sqrt(sum(x * x for x in b))
    return dot / (na * nb) if na > 0 and nb > 0 else 0.0

def vector_rank(chunk_index, query_vec, min_score):
    """chunk_index: list of (source_id, chunk_text, vector). 返回 [(source_id, best_score, chunk_text)] 按分数降序+id 次序"""
    best = {}
    for sid, ctext, vec in chunk_index:
        if len(vec) != len(query_vec):
            continue
        sc = cosine(query_vec, vec)
        if sc < min_score:
            continue
        if sid not in best or sc > best[sid][0]:
            best[sid] = (sc, ctext)
    return sorted(((sid, v[0], v[1]) for sid, v in best.items()), key=lambda r: (-r[1], r[0]))

def keyword_rank(chunks_by_source, query):
    terms = [t for t in re.split(r"\s+", query.strip().lower()) if t]
    if not terms:
        return []
    ranked = []
    for sid, chunks in chunks_by_source.items():
        best_score, best_chunk = 0.0, None
        for ctext in chunks:
            low = ctext.lower()
            sc = float(sum(1 for t in terms if t in low))
            if sc > best_score:
                best_score, best_chunk = sc, ctext
        if best_score > 0:
            ranked.append((sid, best_score, best_chunk))
    return sorted(ranked, key=lambda r: (-r[1], r[0]))

# ---------------- embedding 调用 ----------------
def load_endpoint():
    cfg = json.loads(KEY_CONFIG.read_text(encoding="utf-8"))
    b = cfg["providers"]["bailian"]
    return b["base_url_openai兼容"].rstrip("/"), b["api_key"]

def embed_batch(base, key, texts, batch=10):
    vectors, usage_total, calls = [], 0, 0
    for i in range(0, len(texts), batch):
        part = texts[i:i + batch]
        body = json.dumps({"model": MODEL, "input": part}).encode("utf-8")
        req = urllib.request.Request(base + "/embeddings", data=body, method="POST",
            headers={"Content-Type": "application/json", "Authorization": "Bearer " + key})
        for attempt in range(4):
            try:
                with urllib.request.urlopen(req, timeout=120) as resp:
                    data = json.loads(resp.read().decode("utf-8"))
                break
            except urllib.error.HTTPError as e:
                err = e.read().decode("utf-8", "replace")[:500]
                if e.code in (429, 500, 502, 503) and attempt < 3:
                    time.sleep(2 * (attempt + 1)); continue
                raise RuntimeError(f"HTTP {e.code}: {err}")
            except Exception:
                if attempt < 3:
                    time.sleep(2 * (attempt + 1)); continue
                raise
        calls += 1
        usage_total += data.get("usage", {}).get("total_tokens", 0)
        vectors.extend([d["embedding"] for d in sorted(data["data"], key=lambda d: d["index"])])
        print(f"[embed] batch {i // batch + 1} ok, 累计 tokens={usage_total}", flush=True)
    return vectors, usage_total, calls

# ---------------- 主流程 ----------------
def main():
    selfcheck()
    corpus = json.loads(CORPUS_PATH.read_text(encoding="utf-8"))
    sources, queries = corpus["sources"], corpus["queries"]
    base, key = load_endpoint()

    configs = {"A_500_50_80": (500, 50, 80), "B_300_30_60": (300, 30, 60), "C_800_80_120": (800, 80, 120)}
    thresholds = [0.20, 0.25, 0.30, 0.35, 0.40]
    report = {"model": MODEL, "configs": {}, "usage": {}, "keyword": None}

    total_tokens, total_calls = 0, 0
    q_texts = [q["text"] for q in queries]
    q_vecs, ut, uc = embed_batch(base, key, q_texts)
    total_tokens += ut; total_calls += uc

    for cfg_name, (target, overlap, min_tail) in configs.items():
        chunk_index, chunks_by_source = [], {}
        for s in sources:
            chs = chunk_text(s["text"], target, overlap, min_tail)
            chunks_by_source[s["id"]] = chs
            for c in chs:
                chunk_index.append((s["id"], c))
        c_vecs, ut, uc = embed_batch(base, key, [c for _, c in chunk_index])
        total_tokens += ut; total_calls += uc
        chunk_index = [(sid, c, v) for (sid, c), v in zip(chunk_index, c_vecs)]
        dim = len(c_vecs[0]) if c_vecs else 0

        per_query, sweep = {}, {}
        for th in thresholds:
            hits1 = hits3 = fp = 0
            gold_queries = [q for q in queries if q["gold"]]
            out_queries = [q for q in queries if not q["gold"]]
            for q, qv in zip(queries, q_vecs):
                ranked = vector_rank(chunk_index, qv, th)
                top_ids = [r[0] for r in ranked]
                if th == 0.30:
                    per_query[q["id"]] = {"kind": q["kind"], "gold": q["gold"],
                        "top3": [(r[0], round(r[1], 4)) for r in ranked[:3]]}
                if q["gold"]:
                    if top_ids[:1] and top_ids[0] in q["gold"]: hits1 += 1
                    if any(t in q["gold"] for t in top_ids[:3]): hits3 += 1
                else:
                    if top_ids: fp += 1
            sweep[str(th)] = {"hit@1": f"{hits1}/{len(gold_queries)}", "hit@3": f"{hits3}/{len(gold_queries)}",
                              "out_fp": f"{fp}/{len(out_queries)}"}
        sizes = [len(c) for _, c, _ in chunk_index]
        report["configs"][cfg_name] = {
            "params": {"target": target, "overlap": overlap, "min_tail": min_tail},
            "chunk_count": len(chunk_index), "dim": dim,
            "chunk_size": {"min": min(sizes), "max": max(sizes), "avg": round(sum(sizes) / len(sizes), 1)},
            "sweep": sweep, "per_query@0.30": per_query}
        print(f"[cfg] {cfg_name} 完成 chunks={len(chunk_index)}", flush=True)

    # 关键词回退（与 chunk 参数无关，用 A 档 chunks）
    chunks_by_source = {s["id"]: chunk_text(s["text"]) for s in sources}
    khits1 = khits3 = kfp = 0
    kdetail = {}
    gold_queries = [q for q in queries if q["gold"]]
    out_queries = [q for q in queries if not q["gold"]]
    for q in queries:
        ranked = keyword_rank(chunks_by_source, q["text"])
        top_ids = [r[0] for r in ranked]
        kdetail[q["id"]] = {"kind": q["kind"], "gold": q["gold"], "top3": [(r[0], r[1]) for r in ranked[:3]]}
        if q["gold"]:
            if top_ids[:1] and top_ids[0] in q["gold"]: khits1 += 1
            if any(t in q["gold"] for t in top_ids[:3]): khits3 += 1
        else:
            if top_ids: kfp += 1
    report["keyword"] = {"hit@1": f"{khits1}/{len(gold_queries)}", "hit@3": f"{khits3}/{len(gold_queries)}",
                         "out_fp": f"{kfp}/{len(out_queries)}", "detail": kdetail}
    report["usage"] = {"total_tokens": total_tokens, "api_calls": total_calls}

    RESULTS_PATH.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"[done] tokens={total_tokens} calls={total_calls} -> {RESULTS_PATH}", flush=True)

if __name__ == "__main__":
    main()
