# -*- coding: utf-8 -*-
"""Verify challenge-02 library import: row counts + FTS sample queries (mirrors db._search_doc_chunks_fts)."""
import re
import sqlite3
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")
import sys

DB = "F:/xw/reverse-tutor/reverse_tutor.db"
QUERIES = ["久坐 伤害", "超量恢复", "褪黑素", "碎片化 微运动", "膳食 八准则", "熬夜 炎症", "电竞 劳损", "DRIs", "膳食营养素参考摄入量"]


def prep(query):
    tokens = re.findall("[A-Za-z0-9_]+|[一-鿿]", query or "")
    return " ".join(tokens)


def main():
    con = sqlite3.connect(DB)
    cur = con.cursor()
    for table in ("documents", "doc_chunks", "doc_chunks_fts"):
        try:
            n = cur.execute("SELECT COUNT(*) FROM " + table).fetchone()[0]
            print("count %s = %d" % (table, n))
        except Exception as e:
            print("count %s ERR %s" % (table, e))
    for q in QUERIES:
        fts = prep(q)
        if not fts:
            continue
        try:
            rows = cur.execute(
                "SELECT c.id, d.title, substr(c.content,1,60), -bm25(doc_chunks_fts) "
                "FROM doc_chunks_fts "
                "JOIN doc_chunks c ON c.id = doc_chunks_fts.chunk_id "
                "JOIN documents d ON d.id = c.doc_id "
                "WHERE doc_chunks_fts MATCH ? "
                "AND (doc_chunks_fts.session_id = '' ) "
                "ORDER BY bm25(doc_chunks_fts) ASC LIMIT 3", (fts,)).fetchall()
        except Exception as e:
            print("Q[%s] ERR %s" % (q, e))
            continue
        print("Q[%s] hits=%d" % (q, len(rows)))
        for r in rows:
            title = r[1].encode("utf-8", "replace").decode("utf-8")
            snip = r[2].replace(chr(10), " ")
            print("   #%s score=%.3f %s | %s..." % (r[0], r[3], title[:22], snip[:48]))
    con.close()


if __name__ == "__main__":
    main()
