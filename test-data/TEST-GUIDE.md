FillUp Bio test data
====================

A made-up but realistic history covering **September 2024 to November 2026**:
3 vehicles, 92 fill-ups and 20 centrifuge batches. It is built to exercise
every input and every calculation in the app. Every figure the app should show,
for every vehicle and every date range button, is in **[EXPECTED.md](EXPECTED.md)**.

| File | What it is |
|---|---|
| `generate_test_data.py` | builds everything below; rerun it to regenerate |
| `fillup_bio_test.db` | the whole data set (fill-ups **and** batches) as the app's own database |
| `FillUp Bio Test - Truck.csv` / `Tractor.csv` / `Van.csv` | the fill-ups, one CSV per vehicle, for the Import menu |
| `EXPECTED.md`, `expected.json` | every expected figure, per vehicle and per range |
| `device-checks/` | scripts that read the app's screens over USB and compare them with `expected.json` |

---

What's in it
------------

**Test Truck**: 52 fill-ups, Sep 2024 to Nov 20 2026, about 60% Waste Oil Fuel.

| Case | Where | Tests |
|---|---|---|
| fill-ups before Dec 2024 | Sep to Nov 2024 | the **All** button covers 24 months only; these must be left out |
| no fill-ups at all | Feb 2025 (truck in the shop) | per-month figures divide by months that *have* data |
| partial fills (Tank Full off) | Apr 2025, May 2026, Nov 11 2026 | no MPG on the partial row; the next full tank carries its gallons |
| hidden MPG (towing) | Jul 2025 (pump), Aug 2026 (waste oil) | MPG shows `---` and is left out of avg/min/max; gallons still count |
| pump fill with no cost | Sep 2025, Jul 2026 | left out of the pump price average and Blended Fuel Price; Total Fuel Cost says "excludes … gallons" |
| short top-up | Jan 15 2026, 1.8 gal, 29.5 MPG | Fuel Economy (avg) is miles ÷ gallons, not the mean of each fill |
| priced partial pump fill | May 23 2026 | counts toward pump price even without an MPG |

**Test Tractor**: 10 Waste Oil Fuel fill-ups, Feb to Nov 2026, never buys pump
diesel. Its pump price falls back to **all vehicles** ("from 48 fill-ups, all
vehicles"). Its CSV has a $22.50 cost typed on a waste oil row (Jul 2026), which
**import must set to $0**.

**Test Van**: 30 Pump Diesel fill-ups, Jan 2025 to Nov 2026, no waste oil. Savings
must read $0.00, and Waste Oil Fuel Cost must read $0.00, not "Not recorded".
Includes one unpriced (cash) fill-up, Mar 2026.

**20 centrifuge batches**, Oct 2024 to Nov 14 2026:

| Case | Batch | Tests |
|---|---|---|
| before the All range | Oct 20 2024 | left out of batch counts and Fuel Produced, **included** in the all-time production cost |
| no cost | Jun 7 2025 | counted in Fuel Produced, left out of the production cost ("from 19 batches") |
| no oil processed | Oct 11 2025 | no yield % on the row |
| no oil collected | Mar 28 2026 | no "collected" on the row |
| blends 0, 5, 10, 15, 20% | throughout | total = clean × (1 + blend), cost spread over the total |
| current month | Nov 14 2026 | the **1m** button shows exactly 1 batch |

---

Loading it on a phone
---------------------

### Option A: everything at once (USB, about a minute)

This **replaces all FillUp Bio data on the phone**. Back it up first if it
matters. With the phone connected and USB debugging on, run these from the
`test-data` folder:

```powershell
$adb = "C:\Users\Micha\AndroidBuild\sdk\platform-tools\adb.exe"
```

```powershell
& $adb shell am force-stop com.pabiodiesel.fillup
```

```powershell
& $adb push fillup_bio_test.db /data/local/tmp/fillup_bio_test.db
```

```powershell
& $adb shell "run-as com.pabiodiesel.fillup sh -c 'cat /data/local/tmp/fillup_bio_test.db > databases/gaslog.db; rm -f databases/gaslog.db-journal'"
```

This works with the debug build only (`run-as`).

### Option B: by hand (tests the import and the forms)

1. Add vehicles **Test Truck** (tank 34), **Test Tractor** (20) and **Test Van** (26).
2. For each one, select it, then **⋮ → Import → Cloud storage** and pick its CSV
   from Downloads. If a newly copied file doesn't show up, open the picker's
   menu (☰), tap the phone's name, and browse into **Download**.
3. Type the 20 batches from the **Fuel Production Log** section of
   [EXPECTED.md](EXPECTED.md) with **Add Centrifuge Batch**.

---

Checking it
-----------

Open **Statistics** and **Plots** for each vehicle and press each range button,
then compare with [EXPECTED.md](EXPECTED.md). Numbers can differ by one in the
last decimal place where a value lands exactly on a half (for example
$255.87 ÷ 6 = $42.645 shows as $42.64). Anything bigger is a bug.

The range buttons count back from **the current month**. EXPECTED.md was
generated for **21 Nov 2026**. If you test in a later month, regenerate it
for the day you test on. The data stays the same; only the expected figures
for each range change:

```powershell
python generate_test_data.py --today 2026-12-05
```

### Automatic check (USB)

`device-checks/run_checks.py` drives the phone through every vehicle and range
on Statistics and Plots and compares every row with `expected.json`.
`device-checks/logcheck.py` does the same for every row of every fill-up Log.
They read the statistics page through the WebView debugger, so they need the
**debug** build. Leave the phone on the home screen and run:

```powershell
python device-checks\run_checks.py
```

```powershell
python device-checks\logcheck.py
```

Results on 21 Nov 2026: **1,385 of 1,386 statistics checks** and **all 92 log
rows** matched. The one difference is the $42.645 half-cent tie above.
