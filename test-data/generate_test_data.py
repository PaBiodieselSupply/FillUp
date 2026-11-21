"""
FillUp Bio test data generator.

Builds a made-up but realistic history for three vehicles and the centrifuge,
covering Sep 2024 to Nov 2026, and works out every number the app should show.

    python generate_test_data.py [--today YYYY-MM-DD] [--out DIR]

Writes into DIR (default: this folder):
    FillUp Bio Test - Truck.csv     fill-ups, in the app's CSV import format
    FillUp Bio Test - Tractor.csv
    FillUp Bio Test - Van.csv
    fillup_bio_test.db              the same data plus the batches, as the app's
                                    own database (schema version 8), for loading
                                    straight onto a phone
    expected.json                   every expected figure, per vehicle and range
    EXPECTED.md                     the same, readable

The date range buttons are relative to the current month, so the expected
figures depend on the day you test. --today defaults to the real date; rerun
with the day you test on if it is a different month.

The expected figures are computed here independently from the app's code, by
following the rules in FORMULAS.md. Where the app keeps a value in a Java
float, this does too, so the last digit matches.
"""

import argparse, datetime as dt, json, math, os, random, sqlite3, struct
from decimal import Decimal, ROUND_HALF_UP, ROUND_HALF_EVEN

# ---------------------------------------------------------------- helpers

def f32(x):
    """Round a Python float to Java float precision."""
    return struct.unpack('f', struct.pack('f', x))[0]

def jfmt(x, n):
    """Java String.format("%.nf"): HALF_UP on the shortest repr of the double."""
    return str(Decimal(repr(float(x))).quantize(Decimal(1).scaleb(-n), rounding=ROUND_HALF_UP))

def money(x):
    """Android currency formatter, 2 decimals, grouping: $1,234.56 / -$5.00."""
    d = Decimal(float(x)).quantize(Decimal('0.01'), rounding=ROUND_HALF_EVEN)
    s = f"{abs(d):,.2f}"
    return ("-$" if d < 0 else "$") + s

def money3(x):
    """Fractional currency formatter: 2 to 3 decimals, $3.796 / $3.80."""
    d = Decimal(float(x)).quantize(Decimal('0.001'), rounding=ROUND_HALF_EVEN)
    s = f"{abs(d):,.3f}"
    if s.endswith('0'):
        s = s[:-1]
    return ("-$" if d < 0 else "$") + s

def java_round(x):
    """Math.round(float)."""
    return int(math.floor(x + 0.5))

def is_dst(d):
    """US daylight saving time, local wall clock (America/Chicago)."""
    y = d.year
    mar1 = dt.date(y, 3, 1)
    second_sun_mar = mar1 + dt.timedelta(days=(6 - mar1.weekday()) % 7 + 7)
    nov1 = dt.date(y, 11, 1)
    first_sun_nov = nov1 + dt.timedelta(days=(6 - nov1.weekday()) % 7)
    return second_sun_mar <= d.date() < first_sun_nov

def epoch_ms(d):
    """Local America/Chicago wall time -> epoch milliseconds."""
    offset = -5 if is_dst(d) else -6
    utc = d - dt.timedelta(hours=offset)
    return int((utc - dt.datetime(1970, 1, 1)).total_seconds() * 1000)

def month_key(d):
    return (d.year, d.month)

MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]

# ---------------------------------------------------------------- data

rng = random.Random(20261121)

def pump_price(d):
    """A pump diesel price that drifts between about $3.45 and $4.25."""
    t = (d - dt.datetime(2024, 9, 1)).days
    p = 3.82 + 0.28 * math.sin(t / 58.0) + 0.10 * math.sin(t / 17.0) + rng.uniform(-0.04, 0.04)
    return round(p, 3)

def rand_time(day):
    return dt.datetime(day.year, day.month, day.day, rng.randint(7, 18), rng.choice([0, 5, 10, 15, 20, 25, 30, 35, 40, 45, 50, 55]))

class Fill:
    def __init__(self, when, odo, gal, full, hidden, cost, notes, fuel, csv_cost=None):
        self.when, self.odo, self.gal, self.full, self.hidden = when, odo, gal, full, hidden
        self.cost, self.notes, self.fuel = cost, notes, fuel
        self.csv_cost = cost if csv_cost is None else csv_cost  # what the CSV row says

def build_fills(start, odo, until, gap_days, miles, mpg, wvo_share, events, skip_months=(), pump_only=False, wvo_only=False, start_note="starting fill", pattern=None):
    """Routine fill-ups between start and until, with special events pinned by date.

    events: list of (date, kind, extra) where kind is one of
      partial  - this fill is a partial tank (Tank Full off)
      hidden   - this fill's MPG is hidden (towing)
      cash     - pump fill with no cost recorded
      topup    - add a short top-up 3 days after this fill
      fuel=X   - force this fill's fuel type
      note=TXT - add a note
    An event attaches to the first routine fill on or after its date.
    """
    fills = []
    d = start
    pending = sorted(events, key=lambda e: e[0])
    first = True
    while d <= until:
        if month_key(d) in skip_months:
            d = dt.datetime(d.year + (d.month // 12), d.month % 12 + 1, 3)
            continue
        when = rand_time(d)
        tags = []
        while pending and pending[0][0] <= d:
            tags.append(pending.pop(0)[1:])
        kinds = {t[0]: t[1] if len(t) > 1 else None for t in tags}

        if wvo_only:
            fuel = "wvo"
        elif pump_only:
            fuel = "diesel"
        elif pattern:
            fuel = "wvo" if pattern[len(fills) % len(pattern)] == "W" else "diesel"
        else:
            fuel = "wvo" if rng.random() < wvo_share else "diesel"
        if "fuel" in kinds:
            fuel = kinds["fuel"]
        if "cash" in kinds:
            fuel = "diesel"

        if first:
            gal = round(rng.uniform(0.75, 0.95) * 30, 3)
            full, hidden, notes = True, False, start_note
            first = False
        else:
            m = rng.randint(*miles)
            odo += m
            e = rng.uniform(*mpg) - (0.4 if fuel == "wvo" else 0.0)
            hidden = "hidden" in kinds
            if hidden:
                e *= 0.72  # towing
            full = "partial" not in kinds
            gal = round(m / e, 3)
            notes = kinds.get("note") or ""
            if hidden and not notes:
                notes = "towing trailer - MPG hidden"
            if not full and not notes:
                notes = "partial top off"

        price = pump_price(when) if fuel == "diesel" else None
        cost = round(gal * price, 2) if price and "cash" not in kinds else 0.0
        if "cash" in kinds and not notes:
            notes = "paid cash - no receipt"
        fills.append(Fill(when, odo, gal, full, hidden, cost, notes, fuel))

        if "topup" in kinds:
            when2 = when + dt.timedelta(days=3, hours=1)
            m2 = rng.randint(50, 65)
            odo += m2
            g2 = round(m2 / 29.5, 3)  # a short top-up reads as a very high MPG
            p2 = pump_price(when2)
            fills.append(Fill(when2, odo, g2, True, False, round(g2 * p2, 2), "short top-up", "diesel"))
            d = when2.replace(hour=0, minute=0) + dt.timedelta(days=rng.randint(*gap_days))
        else:
            d = d + dt.timedelta(days=rng.randint(*gap_days))
    return fills

def D(s):
    return dt.datetime.strptime(s, "%Y-%m-%d")

def build_data(until):
    truck = build_fills(
        start=D("2024-09-05"), odo=61250, until=until, gap_days=(13, 19),
        miles=(330, 430), mpg=(16.8, 18.2), wvo_share=0.6, pattern="WWPWP",
        skip_months={(2025, 2)},  # truck in the shop: a month with no data
        events=[
            (D("2024-10-01"), "fuel", "wvo"),
            (D("2025-04-08"), "partial"), (D("2025-04-09"), "fuel", "wvo"),
            (D("2025-07-14"), "hidden"),
            (D("2025-09-01"), "cash"),
            (D("2026-01-10"), "topup"), (D("2026-01-10"), "fuel", "diesel"),
            (D("2026-05-12"), "partial"), (D("2026-05-12"), "fuel", "diesel"),
            (D("2026-06-20"), "cash"),
            (D("2026-08-10"), "hidden"),
            (D("2026-11-01"), "partial"), (D("2026-11-01"), "fuel", "wvo"),
            (D("2026-11-12"), "fuel", "wvo"),
        ])
    # end the truck on a full tank just before "today", so the current month
    # (the 1m button) has a fuel economy figure: it closes the partial before it
    last = truck[-1]
    final = until.replace(hour=9, minute=40)
    if final.date() > last.when.date():
        m = 385
        gal = round(m / 16.9, 3)
        truck.append(Fill(final, last.odo + m, gal, True, False, 0.0, "", "wvo"))
    tractor = build_fills(
        start=D("2026-02-20"), odo=2140, until=until, gap_days=(24, 34),
        miles=(180, 260), mpg=(9.5, 11.0), wvo_share=1.0, wvo_only=True,
        events=[(D("2026-06-01"), "note", "hay season")],
        start_note="starting fill - farm tank")
    # one tractor row in the CSV carries a cost on a waste oil row: import must zero it
    for f in tractor:
        if f.when.month == 7:
            f.csv_cost = 22.50
            f.notes = "cost typed by mistake - import sets it to 0"
            break
    van = build_fills(
        start=D("2025-01-08"), odo=88400, until=until, gap_days=(18, 27),
        miles=(380, 470), mpg=(21.0, 23.0), wvo_share=0.0, pump_only=True,
        events=[(D("2026-03-05"), "cash")],
        start_note="starting fill")

    # centrifuge batches: date, clean fuel (before blend), processed, collected, blend %, cost, notes
    batches = [
        ("2024-10-20", 52.0, 62.0, 70.0, 0, 31.00, "first batch - before the All range"),
        ("2024-12-14", 55.0, 65.0, 75.0, 10, 34.00, ""),
        ("2025-01-25", 48.5, 58.0, 58.0, 0, 27.50, ""),
        ("2025-03-15", 57.0, 68.0, 80.0, 10, 36.25, ""),
        ("2025-04-26", 50.0, 60.0, 65.0, 5, 30.00, ""),
        ("2025-06-07", 46.0, 55.0, 0.0, 0, 0.00, "forgot receipts - no cost"),
        ("2025-07-19", 58.0, 70.0, 90.0, 15, 40.10, "hot weather - thin oil"),
        ("2025-08-30", 53.0, 63.0, 70.0, 10, 33.80, ""),
        ("2025-10-11", 47.5, 0.0, 60.0, 0, 28.00, "processed not measured"),
        ("2025-11-22", 56.0, 66.0, 72.0, 20, 41.00, "winter blend"),
        ("2026-01-10", 54.0, 64.0, 70.0, 20, 40.50, "winter blend"),
        ("2026-02-21", 60.0, 71.0, 85.0, 20, 44.25, "winter blend"),
        ("2026-03-28", 55.0, 65.0, 0.0, 10, 34.00, "collected not recorded"),
        ("2026-05-02", 58.0, 68.0, 75.0, 5, 33.75, ""),
        ("2026-06-06", 61.0, 72.0, 80.0, 0, 32.40, ""),
        ("2026-07-11", 59.0, 69.0, 78.0, 0, 31.90, ""),
        ("2026-08-15", 62.0, 73.0, 90.0, 5, 36.20, "new bowl gasket"),
        ("2026-09-19", 57.5, 68.0, 75.0, 10, 35.60, ""),
        ("2026-10-24", 60.0, 70.0, 80.0, 15, 40.75, ""),
        ("2026-11-14", 58.0, 68.5, 76.0, 10, 37.10, "latest batch - in the 1m range"),
    ]
    batches = [dict(when=D(b[0]).replace(hour=15, minute=30), clean=b[1], processed=b[2],
                    collected=b[3], blend=b[4], cost=b[5], notes=b[6]) for b in batches]

    vehicles = [
        dict(name="Test Truck", tank=34.0, fills=truck),
        dict(name="Test Tractor", tank=20.0, fills=tractor),
        dict(name="Test Van", tank=26.0, fills=van),
    ]
    return vehicles, batches

# ---------------------------------------------------------------- outputs

def write_csv(path, fills, calcs):
    lines = []
    for i, x in enumerate(fills):
        m = jfmt(calcs[i][2], 2) if i in calcs else ""
        lines.append(",".join([
            x.when.strftime("%m/%d/%Y %H:%M"), str(x.odo), f"{x.gal:.3f}",
            str(x.full).lower(), str(x.hidden).lower(), f"{x.csv_cost:.2f}",
            x.notes.replace(",", " "), m, x.fuel]))
    with open(path, "w", newline="\n", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")

SCHEMA = [
    "CREATE TABLE android_metadata (locale TEXT)",
    "CREATE TABLE Vehicles ( _id integer primary key autoincrement, name text not null unique, tanksize real not null)",
    "CREATE TABLE Records ( _id integer primary key autoincrement, _vid integer not null, time integer not null, odometer integer not null, gallons real not null, fulltank integer not null, hidden integer not null default 0, cost real not null default 0, notes text, fuel_type text not null default 'diesel', unique (_vid,odometer), foreign key (_vid) references Vehicles (_id))",
    "CREATE TABLE Batches ( _id integer primary key autoincrement, time integer not null, yield_gallons real not null, raw_gallons real not null default 0, notes text, blend_percent real not null default 0, cost real not null default 0, collected_gallons real not null default 0)",
]

def write_db(path, vehicles, batches):
    if os.path.exists(path):
        os.remove(path)
    c = sqlite3.connect(path)
    for s in SCHEMA:
        c.execute(s)
    c.execute("INSERT INTO android_metadata VALUES ('en_US')")
    for v in vehicles:
        vid = c.execute("INSERT INTO Vehicles (name,tanksize) VALUES (?,?)", (v["name"], v["tank"])).lastrowid
        for x in v["fills"]:
            c.execute("INSERT INTO Records (_vid,time,odometer,gallons,fulltank,hidden,cost,notes,fuel_type) VALUES (?,?,?,?,?,?,?,?,?)",
                      (vid, epoch_ms(x.when), x.odo, x.gal, int(x.full), int(x.hidden), x.cost, x.notes, x.fuel))
    for b in batches:
        c.execute("INSERT INTO Batches (time,yield_gallons,raw_gallons,notes,blend_percent,cost,collected_gallons) VALUES (?,?,?,?,?,?,?)",
                  (epoch_ms(b["when"]), b["clean"], b["processed"], b["notes"], b["blend"], b["cost"], b["collected"]))
    c.execute("PRAGMA user_version = 8")
    c.commit()
    c.close()

# ---------------------------------------------------------------- the app's rules

def mileage(fills):
    """GasRecordList.calculateMileage: {index: (distance, gallons, mpg)} for full tanks."""
    out = {}
    start = None
    used = f32(0.0)
    for i, x in enumerate(fills):
        if start is None:
            if x.full:
                start, used = i, f32(0.0)
            continue
        used = f32(used + f32(x.gal))
        if x.full:
            dist = x.odo - fills[start].odo
            out[i] = (dist, used, f32(dist / used))
            start, used = i, f32(0.0)
    return out

def ranges(today):
    first = dt.datetime(today.year, today.month, 1)
    def back(n):
        y, m = first.year, first.month - n
        while m <= 0:
            m += 12
            y -= 1
        return dt.datetime(y, m, 1)
    end = back(-1)
    return {
        "1m": (first, end, "Past month"),
        "6m": (back(5), end, "Past 6 months"),
        "12m": (back(11), end, "Past 12 months"),
        "ytd": (dt.datetime(today.year, 1, 1), end, "Year to date"),
        "all": (back(23), end, "All data"),
    }

def months_between(start, end):
    out = []
    y, m = start.year, start.month
    while dt.datetime(y, m, 1) < end:
        out.append((y, m))
        m += 1
        if m > 12:
            m, y = 1, y + 1
    return out

def fuel_totals(fills, rng_=None):
    t = dict(wvo=f32(0), pump=f32(0), pcost=0.0, pcg=f32(0), pcn=0)
    for x in fills:
        if rng_ and not (rng_[0] <= x.when <= rng_[1]):
            continue
        g = f32(x.gal)
        if x.fuel == "wvo":
            t["wvo"] = f32(t["wvo"] + g)
        else:
            t["pump"] = f32(t["pump"] + g)
            if x.cost > 0 and g > 0:
                t["pcost"] += x.cost
                t["pcg"] = f32(t["pcg"] + g)
                t["pcn"] += 1
    return t

def wvo_pct(t):
    total = f32(t["wvo"] + t["pump"])
    if total <= 0:
        return 0, 0
    w = java_round(f32(f32(t["wvo"] / total) * 100))
    return w, 100 - w

def batch_totals(batches, rng_=None):
    t = dict(n=0, yld=f32(0), coll=f32(0), cost=0.0, cyld=f32(0), cn=0)
    for b in batches:
        if rng_ and not (rng_[0] <= b["when"] <= rng_[1]):
            continue
        out = f32(f32(b["clean"]) * f32(1 + f32(b["blend"]) / 100))
        t["n"] += 1
        t["yld"] = f32(t["yld"] + out)
        t["coll"] = f32(t["coll"] + f32(b["collected"]))
        if b["cost"] > 0 and out > 0:
            t["cost"] += b["cost"]
            t["cyld"] = f32(t["cyld"] + out)
            t["cn"] += 1
    t["rate"] = t["cost"] / t["cyld"] if t["cn"] else None
    return t

def plural(n, one, many):
    return one if n == 1 else many

def expected_for(vehicle, vehicles, batches, key, rng_):
    start, end, label = rng_
    fills = vehicle["fills"]
    calcs = mileage(fills)
    E = {"range": label}

    # ---- Waste Oil Fuel Savings
    own = fuel_totals(fills)
    if own["pcn"]:
        hist, scope = own, "all time"
    else:
        allf = [x for v in vehicles for x in v["fills"]]
        hist, scope = fuel_totals(allf), "all vehicles"
    prod_all = batch_totals(batches)
    inr = fuel_totals(fills, (start, end))
    S = {}
    if hist["pcn"]:
        price = hist["pcost"] / hist["pcg"]
        S["Pump Diesel Price (avg)"] = f"{money3(price)} per gallon (from {hist['pcn']} {plural(hist['pcn'], 'fill-up', 'fill-ups')}, {scope})"
        if prod_all["rate"] is not None:
            S["Waste Oil Fuel Cost to Produce"] = f"{money3(prod_all['rate'])} per gallon (from {prod_all['cn']} {plural(prod_all['cn'], 'batch', 'batches')}, all time)"
        else:
            S["Waste Oil Fuel Cost to Produce"] = "Not recorded"
        S["Waste Oil Fuel Burned"] = f"{jfmt(inr['wvo'], 3)} gallons"
        if inr["wvo"] <= 0 or prod_all["rate"] is not None:
            S["Estimated Savings"] = money((price - (prod_all["rate"] or 0)) * inr["wvo"])
        else:
            S["Estimated Savings"] = "- (Add batch costs to see this.)"
        if len(vehicles) > 1:
            # sum of every vehicle's own Estimated Savings for this range
            allf = [x for v in vehicles for x in v["fills"]]
            total, unpriced = 0.0, False
            for v in vehicles:
                own_v = fuel_totals(v["fills"])
                h = own_v if own_v["pcn"] else fuel_totals(allf)
                w = fuel_totals(v["fills"], (start, end))["wvo"]
                if w > 0 and prod_all["rate"] is None:
                    unpriced = True
                if h["pcn"]:
                    total += (h["pcost"] / h["pcg"] - (prod_all["rate"] or 0)) * w
            S["Estimated Savings, all vehicles"] = "- (Add batch costs to see this.)" if unpriced else money(total)
    else:
        S["message"] = "No cost has been recorded for any Pump Diesel fill-up..."
    E["Waste Oil Fuel Savings"] = S

    # ---- Fuel Production vs Use
    pr = batch_totals(batches, (start, end))
    P = {"Centrifuge Batches": str(pr["n"])}
    if pr["coll"] > 0:
        P["Waste Oil Collected"] = f"{jfmt(pr['coll'], 3)} gallons"
    P["Fuel Produced"] = f"{jfmt(pr['yld'], 3)} gallons"
    P["Waste Oil Fuel Consumed"] = f"{jfmt(inr['wvo'], 3)} gallons"
    net = f32(pr["yld"] - inr["wvo"])
    sign = "+" if Decimal(repr(float(net))).quantize(Decimal("0.1"), rounding=ROUND_HALF_UP) >= 0 else ""
    P["Net"] = f"{sign}{jfmt(net, 1)} gallons"
    P["footnote"] = len(vehicles) > 1
    E["Fuel Production vs Use"] = P

    # ---- months in the range (MonthlyTrips)
    mstart = start
    if key == "all":
        earliest = min(x.when for x in fills) if fills else end
        if mstart < earliest:
            mstart = earliest
    months = months_between(dt.datetime(mstart.year, mstart.month, 1), end)

    # trips: each record's distance from the previous record, month of the record
    trips = {}
    for i, x in enumerate(fills):
        k = month_key(x.when)
        dist = 0 if i == 0 else x.odo - fills[i - 1].odo
        t = trips.setdefault(k, dict(dist=0, gal=f32(0), cost=0.0, idx=[]))
        t["dist"] += dist
        t["gal"] = f32(t["gal"] + f32(x.gal))
        t["cost"] += x.cost
        t["idx"].append(i)

    def mpg_rows(idxs):
        rows = [calcs[i] for i in idxs if i in calcs and not fills[i].hidden]
        if not rows:
            return "-", "-", "-"
        d = sum(r[0] for r in rows)
        g = f32(0)
        for r in rows:
            g = f32(g + r[1])
        return (f"{jfmt(f32(d / g), 2)} MPG", f"{jfmt(min(r[2] for r in rows), 2)} MPG",
                f"{jfmt(max(r[2] for r in rows), 2)} MPG")

    # ---- Summary (only when the range covers more than one month)
    if len(months) > 1:
        idxs = [i for m in months for i in trips.get(m, {"idx": []})["idx"]]
        tdist = sum(trips[m]["dist"] for m in months if m in trips)
        tgal = f32(0)
        for m in months:
            if m in trips:
                tgal = f32(tgal + trips[m]["gal"])
        tcost = sum(trips[m]["cost"] for m in months if m in trips)
        mwd = sum(1 for m in months if m in trips)
        sub = [fills[i] for i in idxs]
        ft = fuel_totals(sub)
        wp, pp = wvo_pct(ft)
        avg, mn, mx = mpg_rows(idxs)
        Y = {"Fuel Economy (avg)": avg, "Fuel Economy (min)": mn, "Fuel Economy (max)": mx}
        Y["Distance Driven"] = f"{tdist} miles ({jfmt(f32(tdist / mwd) if mwd else 0, 0)} per month)"
        Y["Fuel Used"] = f"{jfmt(tgal, 3)} gallons ({jfmt(f32(tgal / mwd) if mwd else 0, 3)} per month)"
        if f32(ft["wvo"] + ft["pump"]) > 0:
            Y["    Waste Oil Fuel"] = f"{jfmt(ft['wvo'], 3)} gallons ({wp}%)"
            Y["    Pump Diesel"] = f"{jfmt(ft['pump'], 3)} gallons ({pp}%)"
        Y["Pump Fuel Cost"] = f"{money(tcost)} ({money(tcost / mwd if mwd else 0)} per month)"
        priced = ft["wvo"] <= 0 or prod_all["rate"] is not None
        wcost = (prod_all["rate"] or 0) * ft["wvo"]
        Y["Waste Oil Fuel Cost"] = money(wcost) if priced else "Not recorded. Add batch costs to see this."
        if not priced:
            Y["Total Fuel Cost"] = "-"
        else:
            tot = ft["pcost"] + wcost
            unpriced = max(f32(0), f32(ft["pump"] - ft["pcg"]))
            if unpriced > 0:
                Y["Total Fuel Cost"] = f"{money(tot)} (excludes {jfmt(unpriced, 3)} gallons with no cost recorded)"
            else:
                Y["Total Fuel Cost"] = f"{money(tot)} ({money3(tot / tdist if tdist else 0)} per mile)"
        g = f32(ft["pcg"] + ft["wvo"])
        if priced and g > 0:
            Y["Blended Fuel Price"] = f"{money3((ft['pcost'] + wcost) / g)} per gallon"
        else:
            Y["Blended Fuel Price"] = "-"
        E["Summary"] = Y
    else:
        E["Summary"] = None

    # ---- one table per month, newest first
    M = []
    for m in reversed(months):
        t = trips.get(m, dict(dist=0, gal=f32(0), cost=0.0, idx=[]))
        avg, mn, mx = mpg_rows(t["idx"])
        ft = fuel_totals([fills[i] for i in t["idx"]])
        M.append({
            "month": f"{MONTHS[m[1] - 1]} {m[0]}",
            "Fuel Economy (avg)": avg, "Fuel Economy (min)": mn, "Fuel Economy (max)": mx,
            "Distance Driven": f"{t['dist']} miles",
            "Fuel Used": f"{jfmt(t['gal'], 3)} gallons",
            "Pump Fuel Cost": f"{money(t['cost'])} ({money3(t['cost'] / t['dist'] if t['dist'] else 0)} per mile)",
            "Pump Diesel Price (avg)": f"{money3(ft['pcost'] / ft['pcg'])} per gallon" if ft["pcn"] else "-",
        })
    E["Months"] = M

    # ---- Plots: Fuel Split
    if f32(inr["wvo"] + inr["pump"]) > 0:
        wp, pp = wvo_pct(inr)
        E["Fuel Split"] = f"{wp}% Waste Oil Fuel ({jfmt(inr['wvo'], 1)} gallons) · {pp}% Pump Diesel ({jfmt(inr['pump'], 1)} gallons)"
    else:
        E["Fuel Split"] = "No fuel recorded for this period."
    return E

def batch_rows(batches):
    rows = []
    for b in sorted(batches, key=lambda b: b["when"], reverse=True):
        total = f32(f32(b["clean"]) * f32(1 + f32(b["blend"]) / 100))
        r = {"date": b["when"].strftime("%b %d, %Y"),
             "line1": f"{jfmt(b['clean'], 1)} gallons clean"
                      + (f" · {jfmt(b['clean'] / b['processed'] * 100, 2)}%" if b["processed"] > 0 else "")}
        parts = []
        if b["processed"] > 0:
            parts.append(f"from {jfmt(b['processed'], 1)} gallons processed")
        if b["collected"] > 0:
            parts.append(f"{jfmt(b['collected'], 1)} gallons collected")
        if b["blend"] > 0:
            parts.append(f"+{b['blend']:g}% blend = {jfmt(total, 1)} gallons total")
        r["line2"] = " · ".join(parts)
        r["line3"] = f"{money(b['cost'])} batch · {money3(b['cost'] / total)} per gallon" if b["cost"] > 0 else ""
        r["notes"] = b["notes"]
        rows.append(r)
    return rows

# ---------------------------------------------------------------- main

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--today", default=dt.date.today().isoformat())
    ap.add_argument("--out", default=os.path.dirname(os.path.abspath(__file__)))
    a = ap.parse_args()
    today = D(a.today)
    vehicles, batches = build_data(today - dt.timedelta(days=1))

    for v in vehicles:
        calcs = mileage(v["fills"])
        short = v["name"].split()[-1]
        write_csv(os.path.join(a.out, f"FillUp Bio Test - {short}.csv"), v["fills"], calcs)
    write_db(os.path.join(a.out, "fillup_bio_test.db"), vehicles, batches)

    R = ranges(today)
    out = {"generated_for": a.today, "vehicles": {}, "batches": batch_rows(batches)}
    for v in vehicles:
        calcs = mileage(v["fills"])
        out["vehicles"][v["name"]] = {
            "fill_ups": len(v["fills"]),
            "first": v["fills"][0].when.strftime("%Y-%m-%d"),
            "last": v["fills"][-1].when.strftime("%Y-%m-%d"),
            "ranges": {k: expected_for(v, vehicles, batches, k, r) for k, r in R.items()},
        }
    with open(os.path.join(a.out, "expected.json"), "w", encoding="utf-8") as fh:
        json.dump(out, fh, indent=1, ensure_ascii=False)

    # readable copy
    L = [f"Expected figures — generated for {a.today}", "=" * 44, "",
         "Generated by generate_test_data.py. Do not edit by hand.", ""]
    for name, v in out["vehicles"].items():
        L += [f"## {name}", "", f"{v['fill_ups']} fill-ups, {v['first']} to {v['last']}.", ""]
        for k, E in v["ranges"].items():
            L += [f"### {name} — {E['range']} ({k})", ""]
            for table in ("Waste Oil Fuel Savings", "Fuel Production vs Use", "Summary"):
                T = E[table]
                if T is None:
                    L += [f"**{table}:** not shown (range covers only one month)", ""]
                    continue
                L += [f"**{table}**", "", "| Row | Expected |", "|---|---|"]
                for r, val in T.items():
                    if r == "footnote":
                        val = "batch scope note shown" if val else "no note"
                    L.append(f"| {r.strip() if not r.startswith('    ') else '&nbsp;&nbsp;' + r.strip()} | {val} |")
                L.append("")
            L += [f"**Fuel Split plot:** {E['Fuel Split']}", ""]
            if k in ("6m", "1m"):
                L += ["**Month tables**", "", "| Month | MPG avg / min / max | Distance | Fuel Used | Pump Fuel Cost | Pump Price |", "|---|---|---|---|---|---|"]
                for m in E["Months"]:
                    L.append(f"| {m['month']} | {m['Fuel Economy (avg)']} / {m['Fuel Economy (min)']} / {m['Fuel Economy (max)']} | {m['Distance Driven']} | {m['Fuel Used']} | {m['Pump Fuel Cost']} | {m['Pump Diesel Price (avg)']} |")
                L.append("")
    L += ["## Fuel Production Log (newest first)", ""]
    for b in out["batches"]:
        L += [f"- **{b['date']}** — {b['line1']}", f"  - {b['line2']}" if b["line2"] else "  - (no processed / collected / blend)",
              f"  - {b['line3']}" if b["line3"] else "  - (no cost line)"] + ([f"  - notes: {b['notes']}"] if b["notes"] else [])
    with open(os.path.join(a.out, "EXPECTED.md"), "w", encoding="utf-8") as fh:
        fh.write("\n".join(L) + "\n")
    print(f"ok: {sum(len(v['fills']) for v in vehicles)} fill-ups, {len(batches)} batches, today={a.today}")

if __name__ == "__main__":
    main()
