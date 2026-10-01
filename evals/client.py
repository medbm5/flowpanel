"""Tiny HTTP client for the backend (stdlib only), one cookie jar per persona."""

import http.cookiejar
import json
import urllib.error
import urllib.request


class ApiError(Exception):
    def __init__(self, status, body):
        super().__init__(f"HTTP {status}: {body}")
        self.status = status
        self.body = body


class Session:
    def __init__(self, base_url, timeout=60):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def request(self, method, path, body=None):
        data = None if body is None else json.dumps(body).encode("utf-8")
        req = urllib.request.Request(self.base_url + path, data=data, method=method,
                                     headers={"Content-Type": "application/json", "Accept": "application/json"})
        try:
            with self.opener.open(req, timeout=self.timeout) as resp:
                raw = resp.read().decode("utf-8")
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as e:
            raise ApiError(e.code, e.read().decode("utf-8", "replace")) from None

    def get(self, path):
        return self.request("GET", path)

    def post(self, path, body=None):
        return self.request("POST", path, body if body is not None else {})

    def login(self, persona):
        self.post("/auth/demo-login", {"persona": persona})
        return self


class Sessions:
    """Lazily logged-in sessions keyed by persona."""

    def __init__(self, base_url):
        self.base_url = base_url
        self._by_persona = {}

    def __getitem__(self, persona):
        if persona not in self._by_persona:
            self._by_persona[persona] = Session(self.base_url).login(persona)
        return self._by_persona[persona]
