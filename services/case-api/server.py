#!/usr/bin/env python3
"""Legacy Case API of the onboarding back office (simulated). Do not modify – treat it as an external system.

Auth: header X-Api-Key must equal env CASE_API_KEY.
GET  /health
GET  /cases?status=PENDING_REVIEW&offset=0&limit=50     (limit max 50)
GET  /cases/{caseId}
POST /cases/{caseId}/decision   body {"decision":"APPROVE|REJECT|REQUEST_RECAPTURE|ESCALATE","reasonCode":"...","reviewer":"...","comment":"..."}
     optional header Idempotency-Key. Returns 200 with the updated case, 409 if the case is not PENDING_REVIEW,
     422 for an unknown reasonCode/decision.
GET  /reason-codes
GET  /reviewers
Known behaviour of the legacy system: some requests are slow (~1.5 s); POST /decision fails with 503 now and then.
"""
import json, os, random, threading, time, datetime as dt
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

DATA = os.environ.get("DATA_DIR", "/data")
STATE = os.environ.get("STATE_FILE", "/state/decisions.json")
KEY = os.environ.get("CASE_API_KEY", "")
lock = threading.Lock()
cases = json.load(open(f"{DATA}/cases.json"))
reasons = json.load(open(f"{DATA}/reason_codes.json"))
reviewers = json.load(open(f"{DATA}/reviewers.json"))
by_id = {}
for c in cases:
    by_id.setdefault(c["caseId"], c)  # duplicates in the export: first wins
decisions = {}
if os.path.exists(STATE):
    decisions = json.load(open(STATE))
    for cid, d in decisions.get("byCase", {}).items():
        by_id[cid]["status"] = d["status"]; by_id[cid]["decision"] = d
idem = decisions.get("idem", {})

def save():
    os.makedirs(os.path.dirname(STATE), exist_ok=True)
    json.dump({"byCase": {k: v["decision"] for k, v in by_id.items() if "decision" in v}, "idem": idem}, open(STATE, "w"))

STATUS = {"APPROVE": "APPROVED", "REJECT": "REJECTED", "REQUEST_RECAPTURE": "RECAPTURE_REQUESTED", "ESCALATE": "ESCALATED"}

class H(BaseHTTPRequestHandler):
    def _send(self, code, body):
        b = json.dumps(body, ensure_ascii=False).encode()
        self.send_response(code); self.send_header("Content-Type", "application/json"); self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
    def _auth(self):
        if self.path == "/health": return True
        if not KEY or self.headers.get("X-Api-Key") != KEY:
            self._send(401, {"error": "unauthorized"}); return False
        return True
    def _slow(self):
        if random.random() < 0.1: time.sleep(1.5)
    def log_message(self, *a): pass
    def do_GET(self):
        if not self._auth(): return
        self._slow()
        u = urlparse(self.path); q = parse_qs(u.query); parts = u.path.strip("/").split("/")
        if u.path == "/health": return self._send(200, {"status": "UP"})
        if u.path == "/reason-codes": return self._send(200, reasons)
        if u.path == "/reviewers": return self._send(200, reviewers)
        if parts[0] == "cases" and len(parts) == 1:
            st = q.get("status", [None])[0]
            off = int(q.get("offset", ["0"])[0]); lim = min(50, int(q.get("limit", ["50"])[0]))
            items = [c for c in cases if st is None or by_id[c["caseId"]]["status"] == st]
            items = [by_id[c["caseId"]] | {} for c in items]
            return self._send(200, {"items": items[off:off+lim], "offset": off, "limit": lim, "total": len(items)})
        if parts[0] == "cases" and len(parts) == 2:
            c = by_id.get(parts[1])
            return self._send(200, c) if c else self._send(404, {"error": "not found"})
        self._send(404, {"error": "not found"})
    def do_POST(self):
        if not self._auth(): return
        self._slow()
        parts = urlparse(self.path).path.strip("/").split("/")
        if not (len(parts) == 3 and parts[0] == "cases" and parts[2] == "decision"):
            return self._send(404, {"error": "not found"})
        try:
            body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", "0") or 0)) or b"{}")
        except Exception:
            return self._send(400, {"error": "invalid json"})
        key = self.headers.get("Idempotency-Key")
        with lock:
            if key and key in idem: return self._send(200, by_id[idem[key]])
            if random.random() < 0.07: return self._send(503, {"error": "temporarily unavailable, retry"})
            c = by_id.get(parts[1])
            if not c: return self._send(404, {"error": "not found"})
            if body.get("decision") not in STATUS or body.get("reasonCode") not in {r["code"] for r in reasons} or not body.get("reviewer"):
                return self._send(422, {"error": "decision, reasonCode and reviewer are required; see /reason-codes"})
            if c["status"] != "PENDING_REVIEW": return self._send(409, {"error": f"case is {c['status']}"})
            d = {"status": STATUS[body["decision"]], "decision": body["decision"], "reasonCode": body["reasonCode"], "reviewer": body["reviewer"],
                 "comment": str(body.get("comment", ""))[:500], "decidedAt": dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")}
            c["status"] = d["status"]; c["decision"] = d
            if key: idem[key] = c["caseId"]
            save()
            return self._send(200, c)

if __name__ == "__main__":
    ThreadingHTTPServer(("0.0.0.0", int(os.environ.get("PORT", "9000"))), H).serve_forever()
