#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
gpusc_web.py — 8090 端口：静态文件（音乐/模组/工人包） + 反向代理 gpusc API 到 8765

为什么要它：
  客户端 mod 的兜底逻辑会把 coordinatorUrl 推导成 "http://<host>:8090"（工人包所在目录），
  所以打到 8090 的 /register /claim /submit /status 需要被转发到真正的协调者 127.0.0.1:8765。

用法：
  python3 gpusc_web.py            # 监听 0.0.0.0:8090
"""
import http.server
import socketserver
import urllib.request
import urllib.error
import os
import sys

ROOT = "/var/www/music"
UPSTREAM = "http://127.0.0.1:8765"
API_PATHS = ("/register", "/claim", "/submit", "/status")
PORT = 8090

HOP_HEADERS = {
    "host", "connection", "content-length", "accept-encoding",
    "transfer-encoding", "keep-alive", "proxy-connection",
}


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=ROOT, **kwargs)

    def log_message(self, fmt, *args):
        sys.stderr.write("%s - - [%s] %s\n" % (self.address_string(), self.log_date_time_string(), fmt % args))

    # ---------- 反向代理 ----------
    def _proxy(self):
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length) if length > 0 else None
        req = urllib.request.Request(UPSTREAM + self.path, data=body, method=self.command)
        for k, v in self.headers.items():
            if k.lower() in HOP_HEADERS:
                continue
            req.add_header(k, v)
        try:
            with urllib.request.urlopen(req, timeout=300) as resp:
                data = resp.read()
                self.send_response(resp.status)
                for k, v in resp.headers.items():
                    if k.lower() in HOP_HEADERS:
                        continue
                    self.send_header(k, v)
                self.send_header("Content-Length", str(len(data)))
                self.end_headers()
                if self.command != "HEAD":
                    self.wfile.write(data)
        except urllib.error.HTTPError as e:
            data = e.read()
            self.send_response(e.code)
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(data)
        except Exception as e:
            msg = ("proxy error: " + str(e)).encode()
            self.send_response(502)
            self.send_header("Content-Type", "text/plain; charset=utf-8")
            self.send_header("Content-Length", str(len(msg)))
            self.end_headers()
            if self.command != "HEAD":
                self.wfile.write(msg)

    def do_GET(self):
        if self.path.split("?")[0] in API_PATHS:
            return self._proxy()
        return super().do_GET()

    def do_POST(self):
        if self.path.split("?")[0] in API_PATHS:
            return self._proxy()
        self.send_error(405, "Method Not Allowed")

    def do_HEAD(self):
        if self.path.split("?")[0] in API_PATHS:
            return self._proxy()
        return super().do_HEAD()


class ThreadingServer(socketserver.ThreadingMixIn, http.server.HTTPServer):
    daemon_threads = True
    allow_reuse_address = True


def main():
    os.chdir(ROOT)
    with ThreadingServer(("0.0.0.0", PORT), Handler) as httpd:
        sys.stderr.write("gpusc_web serving %s on :%d (proxy -> %s)\n" % (ROOT, PORT, UPSTREAM))
        httpd.serve_forever()


if __name__ == "__main__":
    main()
