How the numbers are calculated
==============================

Verification of every formula added for PA Biodiesel Supply, against the
specification. Each section gives the spec text, where it is implemented, and a
worked example.

All arithmetic below was checked by hand. **None of it has been checked by
running the app** — there is no JDK or Android SDK on the machine this was
written on, and this project has no automated tests.

---

Worked example data
-------------------

One vehicle. Complete history:

| # | Fuel type    | Gallons | Cost   |
|---|--------------|---------|--------|
| 1 | Pump Diesel  | 20.000  | $75.00 |
| 2 | Pump Diesel  | 18.500  | $70.30 |
| 3 | Pump Diesel  | 22.000  | $88.00 |
| 4 | Pump Diesel  | 15.000  | *none recorded* |
| 5 | Waste Oil Fuel | 40.000  | —      |
| 6 | Waste Oil Fuel | 35.000  | —      |

Two centrifuge batches: 55.0 gal clean from 65.0 gal raw, and 30.0 gal clean
from 40.0 gal raw.

Assume every record falls inside the selected date range.

---

1. Fuel economy (MPG) — unchanged
---------------------------------

> `(current odometer − previous odometer) / gallons`

**Untouched.** Fuel type has no effect on how fuel economy is calculated, as
specified. `GasRecordList.calculateMileage()` and `MileageCalculation` were not
modified and never read the fuel type. Mixed-fuel tanks blend into one figure.

---

2. Fuel Split — `FuelTypeTotals`, displayed by `FuelSplitPlot`
--------------------------------------------------------------

> `(gallons of one fuel type / total gallons) × 100`

```
Waste Oil Fuel   = 40.000 + 35.000                   = 75.000 gal
Pump Diesel    = 20.000 + 18.500 + 22.000 + 15.000 = 75.500 gal
Total                                              = 150.500 gal

Black %  = round(75.000 / 150.500 × 100) = round(49.8339) = 50
Pump  %  = 100 − 50                                       = 50
```

Displays: `50% Waste Oil Fuel (75.0 gal) · 50% Pump Diesel (75.5 gal)`

**Two deliberate behaviours worth knowing before someone reports them as bugs:**

- **Only one percentage is rounded.** The other is derived as `100 − it`. Round
  both independently and you get totals of 99% or 101%.
  ([FuelTypeTotals.java](FillUp-master/app/src/main/java/com/github/wdkapps/fillup/FuelTypeTotals.java) — `getPumpDieselPercent()`)
- **Equal percentages with unequal gallons is correct**, as above: 75.0 and 75.5
  gal genuinely both round to 50%. The gallons are always shown alongside, so
  the exact figures are never hidden.
- The **bar itself uses the unrounded fractions**, so its proportions are exact
  even when the labels round. Bar and label can disagree by under 1%.

Uncosted Pump Diesel (row 4) **is** counted here. The split is about volume
burned, not money spent.

---

3. Waste Oil Fuel Savings — `BlackDieselSavingsTable`
---------------------------------------------------

> `Average Diesel Price = total cost of all Pump Diesel fill-ups ÷ total gallons
> of all Pump Diesel fill-ups`
> "Only Pump Diesel fill-up records that have a cost recorded are included.
> Records without a cost entered are excluded."

Row 4 has no cost, so it is excluded from **both** the numerator and the
denominator. Rows 5 and 6 are Waste Oil Fuel and never enter the price at all.

```
Costed Pump Diesel cost    = 75.00 + 70.30 + 88.00 = $233.30
Costed Pump Diesel gallons = 20.000 + 18.500 + 22.000 = 60.500 gal
Costed fill-up count       = 3

Average price = 233.30 / 60.500 = $3.856198… per gallon
```

Displays: `Pump Diesel Price (avg) — $3.856 per gallon (from 3 fill-ups, all time)`

(Three decimal places: the app's fractional currency formatter adds one digit
beyond the locale's normal two, so tenths of a cent are visible.)

### Production cost, and why savings are net

Centrifuge batches record what they cost to produce, so produced fuel is not
treated as free. Using two batches — 55.0 gal for $34.00 and 30.0 gal for
$17.00:

```
Production cost = (34.00 + 17.00) ÷ (55.0 + 30.0) = $0.600 per gallon
```

Only batches with a cost recorded contribute to that rate. An unpriced batch is
counted in the yield totals but excluded here — otherwise every unpriced batch
would quietly drag production cost toward zero and overstate savings.

> `Estimated Savings = (pump price − production cost) × gallons of Waste Oil Fuel consumed`

```
Savings = (3.856198… − 0.600) × 75.000 = $244.2148…  →  $244.21
```

The gross figure on the same data would be $289.21. The $45.00 difference is
exactly what that fuel cost to make. **Only the net figure is shown** — a single
savings number that survives a customer checking it.

If no batch has a cost recorded, production cost is zero and this equals the
gross figure. If producing ever costs more per gallon than buying, the result
goes negative and is displayed as such rather than clamped to zero.

**The two halves use different time windows, on purpose.** The price uses the
complete history; the Waste Oil Fuel volume and therefore the savings follow the
date range buttons. That is what the spec asks for, and it is why the price row
carries the explicit "all time" note — without it, a user switching from All to
Past Month would watch every figure change except one and reasonably assume it
was broken.

If no Pump Diesel fill-up anywhere in the history has a cost, the table shows
the "add cost data" message instead of a number, as specified.

---

4. Centrifuge yield percentage — `BatchRecord.getYieldPercent()`
----------------------------------------------------------------

> `(clean_fuel_yielded / raw_oil_processed) × 100` — shown only when raw oil is entered

Displayed to **two decimal places**, rounded, consistent with how the rest of
the app formats derived numbers:

```
Batch 1: 55.0 / 65.0 × 100 = 84.615384…%  →  "84.62%"
Batch 2: 30.0 / 40.0 × 100 = 75.0%        →  "75.00%"
```

### Note on the spec's example

Section 2.4 of the specification gives **`"84%"`** for exactly this
55.0-from-65.0 case. That is neither rounding nor two decimals — 55 ÷ 65 =
84.615384…%, which rounds to 85% at zero decimals and to **84.62%** at two. The
spec's formula and its worked example disagree with each other.

Two decimal places with normal rounding was chosen deliberately, so that a
customer checking the arithmetic themselves gets the same answer the app shows.
55 ÷ 65 displays as **84.62%**, not 84.61% — 84.61% would require discarding the
remaining digits rather than rounding them.

Batch 2 is unaffected: 75% is exact and displays as 75.00% either way.

The same percentage appears live on the Add Batch form as you type. Both that
preview and the batch log list call `BatchRecord.formatYieldPercent()`, so the
two can never display the same batch differently.

---

5. Production vs Consumption — `ProductionConsumptionTable`
-----------------------------------------------------------

> `Net = total yield − Waste Oil Fuel consumed`. Positive means producing more
> than burning.

```
Centrifuge batches    = 2
Total yield           = 55.0 + 30.0 = 85.000 gal
Waste Oil Fuel consumed = 75.000 gal
Net                   = 85.000 − 75.000 = +10.000 gal
```

Displays `+10.0 gallons` — the sign is always printed, so surplus and deficit
are distinguishable at a glance.

**"Waste Oil Fuel consumed" here is the same number as "Waste Oil Fuel Burned" in
the savings table above it, guaranteed.** Both come from the same
`FuelTypeTotals` calculation rather than two separate loops, which is the whole
reason that class exists.

---

Things that affect the numbers, that you should know about
-----------------------------------------------------------

**1. "All" is not all time — it is the last 24 months.**
`PlotDateRange` hard-caps the All range at −23 months, with the original
author's comment *"force maximum range to 2 years or plot gets ugly"*. This is
pre-existing and affects every ranged figure: Waste Oil Fuel burned, estimated
savings, batch count, total yield, consumed and net. **There is no way in the UI
to see a true lifetime savings figure.** This is why the row is labelled
"Estimated Savings" rather than the spec's "Estimated Lifetime Savings", and why
the table header always carries the range name. Raising or removing the cap is a
one-line change in `PlotDateRange` if you want a genuine lifetime option — but
it would also widen all five plots.

The average pump price is **not** affected: it bypasses `PlotDateRange`
entirely and genuinely uses the whole history.

**2. The price average covers the active vehicle only.**
The statistics report is scoped to the selected vehicle throughout, so "all
time" means all time for that vehicle. In practice the pump price a person pays
is not vehicle-dependent, so a multi-vehicle user's averages will be very close
either way — but they will not be identical.

**3. Batch totals are global; consumption is per-vehicle.**
Batches are not tied to a vehicle (a batch exists before it goes into anything).
So with two or more vehicles, the Net row compares global production against one
vehicle's consumption and overstates the surplus. The table prints a footnote
saying so, but only when more than one vehicle is defined. Single-vehicle users
— the large majority here — are unaffected, since the two scopes are the same.

**4. A zero cost is indistinguishable from no cost recorded.**
The database column is `cost real not null default 0`, so a genuinely free pump
purchase is treated as "no cost recorded" and excluded from the price average.
This matches how the existing fuel log already decides whether to show a cost on
a row. Not fixable without another schema change.

**5. Volumes are never converted between gallons and litres.**
The units preference swaps labels only — that is true throughout the original
app, not something introduced here. A user who switches units partway through
their history will get mixed units summed into a single figure, in the fuel
split, the savings and the yield totals alike.

**6. Purchased and produced fuel are now kept separate everywhere.**
Previously, waste oil fill-ups carried a cost of 0 and those zeros were averaged
in with real purchases, producing a "Fuel Price (avg)" that described nothing.
Now that batches carry a production cost, every gallon has a true cost and each
figure states its own scope:

| Where | Row | Covers |
|---|---|---|
| Savings table | `Pump Diesel Price (avg)` | what you pay at the pump, all time |
| Savings table | `Waste Oil Fuel Cost to Produce` | what your own fuel costs, all time |
| Summary table | `Pump Fuel Cost` | pump spend only |
| Summary table | `Waste Oil Fuel Cost` | production cost of what you burned |
| Summary table | `Total Fuel Cost` | both, plus cost per mile |
| Summary table | `Blended Fuel Price` | total cost ÷ every gallon burned |
| Plots | `Pump Diesel Price` | pump purchases only — months with none are not averaged in |
| Plots | `Fuel Used` | every gallon, both types |

`Fuel Used` was previously labelled `Fuel Purchased`. The underlying total always
included both fuel types — only the label was wrong.
