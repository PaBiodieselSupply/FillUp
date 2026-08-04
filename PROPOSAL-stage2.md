Stage 2 proposal
================

From device testing. Nothing here is implemented yet.

---

1. The regression behind three of your findings
------------------------------------------------

**Every options menu in the app is currently unreachable.** Not just Settings —
also Add Batch, Import, Export, Help, and Share Statistics.

This is my fault. Android only supplies the virtual menu key in the navigation
bar for apps targeting **API 10 or lower**. The original FillUp targeted API 10,
so it got that key for free. I raised `targetSdk` to 29 during the Gradle
migration, which removed it — and because the theme is `Theme.Black.NoTitleBar`
there is no action bar to hold an overflow either. So the menus still exist and
still work; there is simply no longer any way to open them.

Orphaned right now:

| Screen | Unreachable actions |
|---|---|
| Home | Add / Edit / Delete Vehicle, Batch Log, Help, Settings |
| Fuel Log | Add Fuel, **Import**, **Export**, Settings |
| Centrifuge Batches | **Add Batch**, Settings |
| Plots | Settings |
| Statistics | Share, Settings |

Only the on-screen buttons and the row context menus work — which is exactly the
pattern you saw.

### The fix, with no theme change

You asked to keep the app's style as-is, so no Material theme and no AppCompat.
Instead, a small reusable bar laid out in XML and included at the top of each
screen:

```
┌─────────────────────────────────────────┐
│  FillUp Bio                          ⋮  │   ← app_bar.xml
├─────────────────────────────────────────┤
│  (screen content, unchanged)            │
```

- Background and text colours taken from what the app already uses, so it looks
  native to the current design rather than imported from another era.
- The ⋮ is an `ImageButton` that opens a **`PopupMenu`** built from the screen's
  existing menu XML, routed straight into the existing
  `onOptionsItemSelected()`. **No menu handling code changes at all** — the same
  handlers, the same menu files, just a new way in.
- `PopupMenu` is framework, available since API 11, so no new dependency and no
  Activity base-class changes. It also anchors a proper dropdown under the ⋮,
  which is the behaviour you described.

**Decision needed:** the bar has to go on all five screens with menus, or Add
Batch, Share and Settings stay unreachable from where they live. That does mean
a visible change to every screen, not just home. Say if you want home only and I
will move those actions instead.

**Also worth deciding:** the bar can be plain dark to match the current look, or
PA Biodiesel green as the original specification called for. One colour value
either way — no other consequences.

---

2. Centrifuge batch log
-----------------------

### Naming

`Batches` → **`Centrifuge Batches`** on the home button. Agreed.

### Why you could not add a batch

Add Batch lives only in that screen's options menu, so it was orphaned by the
issue above. The app bar fixes it. I would also add a plain **Add Batch button**
on that screen, so the primary action on a log screen is never one tap deep.

### New fields

Per your answer — blend % and total batch cost:

| Field | Type | Notes |
|---|---|---|
| Date | existing | |
| Waste Oil Collected | existing | optional |
| Clean Fuel Produced | existing | required |
| **Blend %** | new, optional | the gasoline or diesel mixed in, e.g. 10 |
| **Total Batch Cost** | new, optional | what the batch cost you to produce |
| Notes | existing | |

Derived and displayed, never stored: yield %, and **cost per gallon**
(`total batch cost ÷ clean fuel produced`) — which is what answers "what did my
100 gallon batch actually cost me".

A batch row would read:

```
14/03/2026        55.0 gallons clean        84.62%
  from 65.0 gallons raw · 10% blend
  $34.00 batch · $0.62 per gallon
  Notes: first run on the new filter
```

**Assumption I am making:** blended-in pump fuel stays counted as Black Diesel
in the fuel split, and its cost is part of the batch cost. Splitting a blended
batch back into part-purchased and part-produced would be more precise but adds
real complexity for a 10% correction. Say if you want it apportioned instead.

This needs database version 7 — two `ALTER TABLE Batches ADD COLUMN` statements.
Unlike the v6 migration, **this one will genuinely run on your phone**, on the
data you have already entered. The backup-before-upgrade code added in stage 1
covers it.

---

3. Savings — net only
---------------------

Per your answer, one savings figure with production cost already subtracted:

```
Pump Diesel Price (avg)      $3.856 per gallon   (from 3 fill-ups, all time)
Black Diesel Cost to Produce $0.600 per gallon   (from 2 batches, all time)
Black Diesel Burned          75.000 gallons
Estimated Savings            $244.21
```

```
Net savings = (3.856 − 0.600) × 75.000 = $244.21
```

For comparison, the current gross figure on the same data is $289.21 — so
tracking production cost reduces the claimed saving by $45.00, which is exactly
what it cost to make that fuel. That is the number that survives a customer
checking it.

The two rate rows stay because they are the inputs to the calculation, not a
second savings figure. If no batch costs are recorded, production cost is 0 and
the result equals today's number.

**Edge case worth knowing:** if production ever costs more per gallon than pump
diesel, this goes negative. I propose showing it rather than clamping to zero —
a negative number is real information.

---

4. Keeping pump and produced data isolated
-------------------------------------------

This was your last point, and it is the one that most improves the app.

**The root problem today:** Black Diesel fill-ups carry a cost of £0/$0, and
those zeros are averaged in with real purchases. So `Fuel Price (avg)` reads
$1.55/gal — a number that describes nothing. It is neither what you pay at the
pump nor what your fuel actually costs, because production cost was never
recorded anywhere.

**Once batch costs exist, that stops being a distortion and becomes a real
number.** Every gallon then has a true cost — purchased or produced — so the
blend is meaningful rather than an artifact of missing data.

### Proposed statistics summary table

Every row labelled by exactly what it covers:

```
Fuel Economy (avg / min / max)      unchanged
Distance Driven                     unchanged

Fuel Used                    150.500 gallons  (50.2 per month)
   Black Diesel               75.000 gallons  (50%)
   Pump Diesel                75.500 gallons  (50%)

Pump Fuel Cost               $233.30
Black Diesel Cost             $45.00      (75.0 gal × $0.600)
Total Fuel Cost              $278.30      ($0.31 per mile)

Blended Fuel Price            $1.849 per gallon
```

```
Blended = (233.30 + 45.00) ÷ 150.500 = $1.849 per gallon
```

Compare that against the $3.856 pump price and you have the strongest line in
the whole app: **your fuel costs less than half what the pump charges.** Today's
$1.55 could not be defended if a customer asked how it was worked out. This can.

### Plots screen — same isolation problem

`Fuel Price` and `Fuel Cost` have the same defect and it is arguably worse
there: a month in which you burned only Black Diesel plots as **$0.00 per
gallon**, a fabricated data point that drags the whole line down.

Proposed:

| Chart | Now | Proposed |
|---|---|---|
| Fuel Purchased | includes both, mislabelled | **Fuel Used**, stacked by type |
| Fuel Cost | pump spend only, unlabelled as such | **Pump Fuel Cost** |
| Fuel Price | pump only, with fake $0 months | **Pump Diesel Price**, months with no pump purchase omitted rather than plotted as zero |

Omitting the empty months is the actual accuracy fix — the rename alone would
still leave false zeros in the chart.

---

5. Import and export on the home screen
----------------------------------------

They currently live in the Fuel Log menu. They are per-vehicle operations and
the home screen is where the vehicle is selected, so moving them there is the
right call as well as what you asked for.

Proposed: **Import** and **Export** as the last two items in the home ⋮ menu,
after the vehicle actions, operating on the vehicle in the spinner. Removed from
the Fuel Log menu so there is one place to find them rather than two.

The shared code (permission gate, overwrite confirmation, storage chooser,
share prompt) moves to a small helper both screens can call, rather than being
duplicated.

---

6. Carried over from the display proposal
------------------------------------------

Unchanged, still awaiting your go-ahead:

- Log rows: drop the green font so Black Diesel matches Pump Diesel.
- Fuel Split card: `#444444` fill, `#969696` 1px square border to match the
  androidplot cards.
- Table headings: `BLACK DIESEL SAVINGS — Past Month`, fixing the missing space
  across all tables including the original `SUMMARY:` one.
- Fuel Used chart: stacked bars (androidplot 0.6.0 supports it).
- Open question still outstanding: is `PRODUCTION vs CONSUMPTION` the wording
  you want on a phone screen?

---

Decisions I need
----------------

| # | Decision | My recommendation |
|---|---|---|
| 1 | App bar on all five screens, or home only | **All five** — otherwise Add Batch, Share and Settings stay unreachable |
| 2 | Bar colour: plain dark, or PA Biodiesel green | Green — it is the branding the original spec asked for, and costs nothing |
| 3 | Blended batches: count fully as Black Diesel, or apportion by blend % | **Count fully** — apportioning is real complexity for a 10% correction |
| 4 | `PRODUCTION vs CONSUMPTION` wording | Your customers' vocabulary, not mine |

Everything else above I will build as described.

Nothing in here has been compiled or run.
