# -*- coding: utf-8 -*-
"""KG quality check for kb:challenge-02: counts by kind + samples."""
import sqlite3
import sys

sys.stdout.reconfigure(encoding="utf-8", errors="replace")

DB = "F:/xw/reverse-tutor/reverse_tutor.db"
SID = "kb:challenge-02"

con = sqlite3.connect(DB)
cur = con.cursor()
print("== nodes by kind ==")
for kind, n in cur.execute(
    "SELECT kind, COUNT(*) FROM kg_nodes WHERE session_id=? AND status='active' GROUP BY kind", (SID,)):
    print("  %s: %d" % (kind, n))
tn = cur.execute("SELECT COUNT(*) FROM kg_nodes WHERE session_id=? AND status='active'", (SID,)).fetchone()[0]
te = cur.execute("SELECT COUNT(*) FROM kg_edges WHERE session_id=? AND status='active'", (SID,)).fetchone()[0]
print("total nodes=%d edges=%d" % (tn, te))
print("== sample nodes (concept, 8) ==")
for name, props in cur.execute(
    "SELECT name, properties_json FROM kg_nodes WHERE session_id=? AND kind='concept' AND status='active' LIMIT 8", (SID,)):
    import json as j
    summary = j.loads(props or "{}").get("summary", "")
    print("  - %s | %s" % (name, summary[:44]))
print("== sample edges (10) ==")
for s, t, rel, w in cur.execute(
    "SELECT sn.name, tn.name, e.relation, e.weight FROM kg_edges e "
    "JOIN kg_nodes sn ON sn.id=e.source_id JOIN kg_nodes tn ON tn.id=e.target_id "
    "WHERE e.session_id=? AND e.status='active' ORDER BY e.id LIMIT 10", (SID,)):
    print("  %s -[%s]-> %s (w=%.1f)" % (s, rel, t, w))
print("== relation vocab ==")
for rel, n in cur.execute(
    "SELECT relation, COUNT(*) FROM kg_edges WHERE session_id=? AND status='active' GROUP BY relation ORDER BY 2 DESC", (SID,)):
    print("  %s: %d" % (rel, n))
con.close()
