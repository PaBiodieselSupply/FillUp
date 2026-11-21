"""Evaluate JS in the app's WebView over the DevTools protocol (stdlib only).
usage: python cdp.py "<js expression>"   (prints the JSON result value)
Forwards tcp:9222 to the app's current webview_devtools_remote socket first."""
import base64, json, os, re, socket, struct, subprocess, sys, urllib.request

ADB = os.environ.get("ADB", r"C:\Users\Micha\AndroidBuild\sdk\platform-tools\adb.exe")

def forward():
    pid = subprocess.run([ADB, "shell", "pidof", "com.pabiodiesel.fillup"], capture_output=True, text=True).stdout.strip()
    subprocess.run([ADB, "forward", "tcp:9222", f"localabstract:webview_devtools_remote_{pid}"], capture_output=True)

def ws_connect(url):
    m = re.match(r"ws://([^:/]+):(\d+)(/.*)", url)
    host, port, path = m.group(1), int(m.group(2)), m.group(3)
    s = socket.create_connection((host, port), timeout=15)
    key = base64.b64encode(os.urandom(16)).decode()
    s.sendall((f"GET {path} HTTP/1.1\r\nHost: {host}:{port}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
               f"Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n").encode())
    buf = b""
    while b"\r\n\r\n" not in buf:
        buf += s.recv(4096)
    return s, buf.split(b"\r\n\r\n", 1)[1]

def ws_send(s, text):
    data = text.encode()
    hdr = bytearray([0x81])
    n = len(data)
    if n < 126:
        hdr.append(0x80 | n)
    elif n < 65536:
        hdr.append(0x80 | 126); hdr += struct.pack(">H", n)
    else:
        hdr.append(0x80 | 127); hdr += struct.pack(">Q", n)
    mask = os.urandom(4)
    hdr += mask
    s.sendall(bytes(hdr) + bytes(b ^ mask[i % 4] for i, b in enumerate(data)))

class Reader:
    def __init__(self, s, rest):
        self.s, self.buf = s, rest
    def need(self, n):
        while len(self.buf) < n:
            chunk = self.s.recv(65536)
            if not chunk:
                raise EOFError
            self.buf += chunk
        out, self.buf = self.buf[:n], self.buf[n:]
        return out
    def frame(self):
        msg = b""
        while True:
            b0, b1 = self.need(2)
            n = b1 & 0x7F
            if n == 126:
                n = struct.unpack(">H", self.need(2))[0]
            elif n == 127:
                n = struct.unpack(">Q", self.need(8))[0]
            msg += self.need(n)
            if b0 & 0x80:
                return msg.decode("utf-8", "replace")

def evaluate(expr):
    forward()
    pages = json.load(urllib.request.urlopen("http://127.0.0.1:9222/json"))
    page = [p for p in pages if p.get("type") == "page"][0]
    s, rest = ws_connect(page["webSocketDebuggerUrl"])
    ws_send(s, json.dumps({"id": 1, "method": "Runtime.evaluate",
                           "params": {"expression": expr, "returnByValue": True}}))
    r = Reader(s, rest)
    while True:
        msg = json.loads(r.frame())
        if msg.get("id") == 1:
            s.close()
            return msg["result"]["result"].get("value")

# all report tables as [[cell, cell], ...] with the header row first
TABLES_JS = """JSON.stringify([...document.querySelectorAll('table')].map(t =>
  [...t.querySelectorAll('tr')].map(r => [...r.querySelectorAll('th,td')].map(c => c.innerText.replace(/\\s+/g,' ').trim()))))"""

if __name__ == "__main__":
    expr = sys.argv[1] if len(sys.argv) > 1 else TABLES_JS
    v = evaluate(expr)
    sys.stdout.reconfigure(encoding="utf-8")
    print(v if isinstance(v, str) else json.dumps(v, indent=1))
