"""Tiny adb UI driver: python ui.py dump | tap TEXT [n] | texts | back | shot FILE | key CODE | swipeup"""
import subprocess, sys, re, time, os
ADB = os.environ.get("ADB", r"C:\Users\Micha\AndroidBuild\sdk\platform-tools\adb.exe")
HERE = os.path.dirname(os.path.abspath(__file__))

def adb(*a, binary=False):
    r = subprocess.run([ADB, *a], capture_output=True)
    return r.stdout if binary else r.stdout.decode("utf-8", "replace")

def dump():
    for _ in range(3):
        out = adb("exec-out", "uiautomator", "dump", "/dev/tty")
        if "<hierarchy" in out:
            return out[out.index("<hierarchy"):out.rindex("</hierarchy>") + 12]
        time.sleep(1)
    return ""

def nodes(xml):
    res = []
    for m in re.finditer(r'<node ([^>]*)>?', xml):
        a = dict(re.findall(r'([\w-]+)="([^"]*)"', m.group(1)))
        b = re.findall(r'\d+', a.get("bounds", ""))
        if len(b) == 4:
            a["xy"] = ((int(b[0]) + int(b[2])) // 2, (int(b[1]) + int(b[3])) // 2)
        res.append(a)
    return res

def unescape(s):
    return s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", '"').replace("&#10;", "\n")

def texts():
    out = []
    for n in nodes(dump()):
        t = unescape(n.get("text", "") or n.get("content-desc", ""))
        if t.strip():
            out.append(t)
    return out

def tap(label, idx=0):
    found = [n for n in nodes(dump()) if unescape(n.get("text", "")) == label or unescape(n.get("content-desc", "")) == label]
    if len(found) <= idx:
        print("NOT FOUND:", label); return False
    x, y = found[idx]["xy"]
    adb("shell", "input", "tap", str(x), str(y))
    time.sleep(1.2)
    return True

if __name__ == "__main__":
    cmd = sys.argv[1]
    if cmd == "texts":
        print("\n".join(texts()))
    elif cmd == "dump":
        print(dump())
    elif cmd == "tap":
        tap(sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 0)
    elif cmd == "back":
        adb("shell", "input", "keyevent", "4"); time.sleep(1)
    elif cmd == "key":
        adb("shell", "input", "keyevent", sys.argv[2]); time.sleep(1)
    elif cmd == "shot":
        open(sys.argv[2], "wb").write(adb("exec-out", "screencap", "-p", binary=True))
    elif cmd == "swipeup":
        adb("shell", "input", "swipe", "540", "1900", "540", sys.argv[2] if len(sys.argv) > 2 else "500", "400"); time.sleep(1)
    elif cmd == "swipedown":
        adb("shell", "input", "swipe", "540", "500", "540", "1900", "400"); time.sleep(1)
