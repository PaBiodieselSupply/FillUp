Proposal: presenting fuel type data
===================================

Response to device review. Nothing here is implemented yet.

---

Three findings first — one of them changes the ask
--------------------------------------------------

### 1. "Fuel Purchased" already includes the fuel you produced

This is the important one. The chart and the statistics row are **not** missing
your Black Diesel — they already count it. The label is simply wrong.

`MonthlyTrips` builds a `TripRecord` for every consecutive pair of fill-ups and
sums `end.getGallons()`, with **no fuel type filter anywhere**. Both the
"Fuel Purchased" plot and the "Fuel Purchased" statistics row read from that
total.

So the number already answers your question — *how many total gallons did this
vehicle use* — it is just labelled as though it doesn't. What is genuinely
missing is the **breakdown**, not the total.

That makes this mostly a naming and presentation job rather than a
recalculation job, which is good news for risk.

### 2. The missing space is a pre-existing Android quirk, not our bug

`BLACK DIESEL SAVINGS:Past Month` — the resource string is
`"BLACK DIESEL SAVINGS: "` **with** a trailing space, but Android's resource
compiler strips leading and trailing whitespace from string resources unless
they are quoted.

The original app has the same defect: its summary table reads
`SUMMARY:Past Month`. Worth fixing everywhere at once rather than patching one
string.

### 3. I now know exactly what the plot cards are styled with

Read out of the bundled androidplot source:

| | Value |
|---|---|
| Card fill | `Color.DKGRAY` = **#444444** |
| Card border | **#969696**, 1px stroke |
| Corners | **square** (`BorderStyle.SQUARE` is the default, not rounded) |

My Fuel Split box is solid white with no border — completely off, exactly as you
saw.

Also confirmed: androidplot 0.6.0 **does** support `BarRenderStyle.STACKED`,
which makes the best option below actually buildable.

---

A. Log — remove the green
--------------------------

Agreed, and straightforward. Drop the per-fuel-type colour so both rows inherit
`@color/log_fg` like the Cost and Notes lines do. The `Fuel Type: Black Diesel`
/ `Fuel Type: Pump Diesel` text still distinguishes them.

Removes two colour resources and `FuelType.getColorResId()` entirely.

**Recommendation: do it. No decision needed.**

---

B. Plots — make Fuel Split look like the other cards
----------------------------------------------------

Give the Fuel Split container a shape drawable matching the values in finding 3:
`#444444` fill, 1dp `#969696` stroke, square corners, and switch the summary
label from black to white so it reads on dark grey. Margins already match the
plots at 10dp.

**Recommendation: do it. No decision needed.**

---

C. Plots — "Fuel Purchased" → the breakdown
--------------------------------------------

Three ways to go. I recommend option 3.

### Option 1 — rename only

`Fuel Purchased` → `Fuel Used`. One string. The total is already correct, so
this alone makes the card honest.

*No per-month breakdown. Cheapest, and a valid stopping point.*

### Option 2 — a second card

Keep `Fuel Used` as-is, add a separate `Fuel Produced` card below it.

*Answers the question, but the plots screen is already six cards deep and each
one is sized to 85% of the screen. This makes it seven, and forces you to
compare two charts by scrolling between them — which is the thing charts are
supposed to save you from.*

### Option 3 — one stacked card ✅ recommended

Rename to **`Fuel Used`** and make each month's bar two stacked segments:
Black Diesel on the bottom, Pump Diesel on top.

```
 gal
 60 |          ▓▓▓▓
 45 |  ▓▓▓▓    ▓▓▓▓    ▓▓▓▓      ▓▓ Pump Diesel
 30 |  ████    ████    ▓▓▓▓      ██ Black Diesel
 15 |  ████    ████    ████
    +--------------------------
       JAN     FEB     MAR
```

Total bar height is still total fuel used, so nothing is lost — and the split is
readable per month, which is what actually shows whether the centrifuge is
displacing pump purchases over time. That is the story your customers care
about, and it is the one chart that tells it.

**Cost:** a small new class mapping each month to its Black Diesel and Pump
Diesel volumes (the current `TripRecord` collapses them into one number), plus
switching `GallonsPlot` to two series with `BarRenderStyle.STACKED`.

**Risk:** stacked bars in androidplot 0.6.0 are a code path this app has never
used, and I cannot test rendering here. If it misbehaves, option 1 is the
fallback and costs nothing already spent.

---

D. Statistics — table headings
-------------------------------

Fix the whitespace properly rather than per-string: introduce one title format
resource and build every range-scoped heading through it, so this class of bug
cannot come back.

Current vs proposed:

| Now | Proposed |
|---|---|
| `BLACK DIESEL SAVINGS:Past Month` | `BLACK DIESEL SAVINGS — Past Month` |
| `PRODUCTION vs CONSUMPTION:Past Month` | `PRODUCTION vs CONSUMPTION — Past Month` |
| `SUMMARY:Past Month` | `SUMMARY — Past Month` |

Month tables (`January 2026`) are unaffected.

**Open question for you:** `PRODUCTION vs CONSUMPTION` is long and reads a bit
technical on a phone. Alternatives: `CENTRIFUGE OUTPUT`, `PRODUCED vs BURNED`,
or leave it. Your call — it is your customers' vocabulary, not mine.

---

E. Statistics — Fuel Used breakdown
------------------------------------

Rename the row and add two indented sub-rows beneath it, inside the existing
summary table rather than as a new block:

```
Fuel Used            200.000 gallons
                     (66.7 per month)
  Black Diesel       150.000 gallons (75%)
  Pump Diesel         50.000 gallons (25%)
```

Keeping it inside the summary table avoids a fourth block competing with the
Savings and Production tables above it, and puts the breakdown immediately next
to the total it decomposes.

---

F. One thing you did not raise, that this change exposes
---------------------------------------------------------

`Fuel Price (avg)` in the summary table is `total cost ÷ total gallons` — and
"total gallons" includes your free Black Diesel. Using the worked example from
FORMULAS.md:

```
$233.30 ÷ 150.500 gal = $1.550 per gallon
```

That is **not** what you pay at the pump ($3.856), and right now the label gives
no hint of that. Once we correctly relabel gallons as "Fuel Used", the row gets
more misleading, not less — so it needs a decision now.

I think the honest reading is that this number is genuinely useful, just badly
named. **Your blended fuel cost is $1.55/gal against a pump price of $3.86** is
a considerably better sales line than anything in the savings table.

**Recommended split of duties:**

| Table | Row | Meaning |
|---|---|---|
| Black Diesel Savings | `Pump Diesel Price (avg)` — $3.856 | what you pay when you buy |
| Summary | `Blended Fuel Price` — $1.550 | what every gallon you burned actually cost |

Same two numbers as today, each labelled for what it is, no duplication.

*(Cost per mile in the summary already includes the free fuel, which is correct
— that is your true operating cost. No change proposed.)*

---

Decisions I need
----------------

| # | Decision | My recommendation |
|---|---|---|
| 1 | Fuel Used chart: rename only / second card / stacked | **Stacked**, with rename-only as fallback if rendering misbehaves |
| 2 | `PRODUCTION vs CONSUMPTION` heading wording | Open — your customers' vocabulary |
| 3 | `Fuel Price (avg)` → `Blended Fuel Price` | **Yes**, it is a better number than its current name suggests |

Items A, B, D and E need no decision — say go and they are done.

Nothing in here has been compiled or run.
