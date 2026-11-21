"""Drive the app through every vehicle x range on Statistics and Plots, compare with expected.json."""
import json, sys, time
sys.path.insert(0, __import__("os").path.dirname(__file__))
import ui, cdp

EXP = json.load(open(__import__("os").path.join(__import__("os").path.dirname(__import__("os").path.abspath(__file__)), "..", "expected.json"), encoding="utf-8"))
BUTTON = {"1m": "1m", "6m": "6m", "12m": "12m", "ytd": "2026", "all": "All"}
only = sys.argv[1:]  # optional vehicle names
results = {"pass": 0, "fail": []}

def check(where, label, got, want):
    if got == want:
        results["pass"] += 1
    else:
        results["fail"].append(f"{where} | {label}\n      app: {got}\n expected: {want}")

def go_home():
    for _ in range(8):
        t = ui.texts()
        if "Add Centrifuge Batch" in t:
            return t
        ui.adb("shell", "input", "keyevent", "4"); time.sleep(1)
    return ui.texts()

def wait_for(text, tries=10):
    for _ in range(tries):
        t = ui.texts()
        if text in t:
            return t
        time.sleep(1)
    raise RuntimeError(f"screen never showed {text!r}")

def select_vehicle(name):
    for _ in range(3):
        t = go_home()
        if t[t.index("Vehicle") + 1] == name:
            return
        ui.tap(t[t.index("Vehicle") + 1])   # open the spinner
        time.sleep(1)
        wait_for(name)
        ui.tap(name)
        time.sleep(1.5)
    raise RuntimeError(f"could not select {name}")

def open_screen(button, marker):
    wait_for(button)
    ui.tap(button)
    wait_for(marker)
    time.sleep(1.5)

def tables(range_label):
    for _ in range(10):
        try:
            v = cdp.evaluate(cdp.TABLES_JS)
            T = json.loads(v) if v else []
            if T and T[0][0][0].lower().endswith(range_label.lower()):
                return T
        except Exception:
            pass
        time.sleep(1)
    return []

def compare_stats(vname, key, E, T):
    where = f"{vname} {key}"
    byhead = {}
    months = []
    for t in T:
        head = t[0][0]
        rows = {r[0]: r[1] for r in t[1:] if len(r) > 1}
        notes = [r[0] for r in t[1:] if len(r) == 1]
        if head.startswith("WASTE OIL FUEL SAVINGS"):
            byhead["Waste Oil Fuel Savings"] = (head, rows, notes)
        elif head.startswith("FUEL PRODUCTION"):
            byhead["Fuel Production vs Use"] = (head, rows, notes)
        elif head.startswith("SUMMARY"):
            byhead["Summary"] = (head, rows, notes)
        else:
            months.append((head, rows))
    for name in ("Waste Oil Fuel Savings", "Fuel Production vs Use", "Summary"):
        want = E[name]
        if want is None:
            check(where, f"{name} table shown?", name in byhead, False)
            continue
        if name not in byhead:
            check(where, f"{name} table shown?", False, True)
            continue
        head, rows, notes = byhead[name]
        check(where, f"{name} heading", head.split(" — ")[-1].lower(), E["range"].lower())
        for label, val in want.items():
            if label == "footnote":
                check(where, f"{name} scope note", bool(notes), val)
                continue
            if label == "message":
                check(where, f"{name} message", bool(notes), True)
                continue
            check(where, f"{name}: {label.strip()}", rows.get(label.strip()), val)
        extra = set(rows) - {l.strip() for l in want}
        if extra:
            check(where, f"{name} unexpected rows", sorted(extra), [])
    check(where, "month tables", [m[0] for m in months], [m["month"].upper() for m in E["Months"]])
    for (head, rows), m in zip(months, E["Months"]):
        for label, val in m.items():
            if label == "month":
                continue
            check(where, f"{m['month']}: {label}", rows.get(label), val)

for vname, V in EXP["vehicles"].items():
    if only and vname not in only:
        continue
    select_vehicle(vname)
    open_screen("Statistics", "Share Report")
    for key, E in V["ranges"].items():
        ui.tap(BUTTON[key]); time.sleep(2)
        compare_stats(vname, key, E, tables(E["range"]))
    # plots: fuel split summary text, per range
    go_home()
    open_screen("Plots", "1m")
    for key, E in V["ranges"].items():
        ui.tap(BUTTON[key]); time.sleep(5)
        got = None
        for _ in range(8):
            got = next((x for x in ui.texts() if "Waste Oil Fuel (" in x or "No fuel recorded" in x), None)
            if got == E["Fuel Split"]:
                break
            time.sleep(1)
        check(f"{vname} {key}", "Plots: Fuel Split", got, E["Fuel Split"])
    go_home()

print(f"PASS {results['pass']}  FAIL {len(results['fail'])}")
sys.stdout.reconfigure(encoding="utf-8")
for f in results["fail"]:
    print(" -", f)
