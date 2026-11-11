"""SysArch Studio's remote solver service (P3 T4): the HTTP side of RemoteHttpSolver.

POST / with the JSON python_solver.py reads (expression, inputs, targets, options) and get its JSON back.
Standard library only; it runs python_solver.py (kept verbatim, spec P3 §3.4) in its own process for every request.

Defence in depth: the backend already scans what users wrote and sends only code it generated, but this service may be
reachable by others, so it checks again: the expression may hold only names, numbers, the operators + - * / ** ( ) =,
spaces and newlines, and no names with double underscores or Python keywords. The Helm chart runs it in a pod without
network egress and with a read-only root file system (the container the spec asks for).

    SOLVER_HOST=0.0.0.0 SOLVER_PORT=8091 python -I http_server.py
"""
import json
import os
import re
import subprocess
import sys
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "python_solver.py")
TIMEOUT_S = float(os.environ.get("SOLVER_TIMEOUT_S", "25"))
MAX_BODY = 256 * 1024
ALLOWED = re.compile(r"^[A-Za-z0-9_.\s+\-*/()=]*$")
NAME = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")
FORBIDDEN = {"import", "open", "eval", "exec", "compile", "lambda", "globals", "locals", "getattr", "setattr", "delattr", "vars", "dir",
             "input", "print", "breakpoint", "help", "exit", "quit", "os", "sys", "subprocess", "builtins", "class", "def", "for", "while",
             "with", "yield", "return", "from", "as", "global", "nonlocal", "del", "pass", "raise", "try", "except", "finally", "assert",
             "if", "else", "elif", "and", "or", "not", "is", "in", "None", "True", "False", "async", "await"}


def refused(expression):
    if not isinstance(expression, str) or not ALLOWED.match(expression):
        return "expression may only hold names, numbers and + - * / ** ( ) ="
    for name in NAME.findall(expression):
        if "__" in name or name in FORBIDDEN:
            return "forbidden name: " + name
    return None


class Handler(BaseHTTPRequestHandler):
    def _send(self, status, body):
        data = json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self._send(200, {"status": "UP"}) if self.path == "/health" else self._send(404, {"error": "not found"})

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0 or length > MAX_BODY:
            return self._send(413, {"error": "body missing or too large"})
        try:
            request = json.loads(self.rfile.read(length))
        except ValueError:
            return self._send(400, {"error": "not JSON"})
        reason = refused(request.get("expression"))
        if reason:
            return self._send(422, {"error": reason})
        try:
            done = subprocess.run([sys.executable, "-I", SCRIPT], input=json.dumps(request).encode("utf-8"), capture_output=True,
                                  timeout=TIMEOUT_S, env={k: v for k, v in os.environ.items() if k in ("SystemRoot", "PATH")})
        except subprocess.TimeoutExpired:
            return self._send(504, {"error": "solver timed out"})
        if done.returncode != 0:
            return self._send(422, {"error": done.stderr.decode("utf-8", "replace").strip().splitlines()[-1:] or ["solver failed"]})
        try:
            return self._send(200, json.loads(done.stdout))
        except ValueError:
            return self._send(502, {"error": "solver answered no JSON"})

    def log_message(self, format, *args):
        sys.stderr.write("solver %s\n" % (format % args))


if __name__ == "__main__":
    host = os.environ.get("SOLVER_HOST", "127.0.0.1")
    port = int(os.environ.get("SOLVER_PORT", "8091"))
    ThreadingHTTPServer((host, port), Handler).serve_forever()
