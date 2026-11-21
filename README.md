FillUp Bio - Dual Fuel Mileage Log
==============================================

An Android application to calculate and track fuel economy, extended for
PA Biodiesel Supply customers who run waste oil diesel systems alongside
conventional pump diesel.

This is a modified fork of **FillUp - Gas Mileage Log** by William D. Kraemer
(https://github.com/wdkapps/FillUp), used under the Apache License 2.0.

Modifications by PA Biodiesel Supply, 2026
------------------------------------------

Added:

+ **Fuel type on every fill-up** - Waste Oil Fuel (produced by the user from waste
  oil) or Pump Diesel (purchased). Waste Oil Fuel has no purchase cost, so the
  price and cost inputs are hidden when it is selected. Fuel type does not
  affect how fuel economy is calculated.
+ **Fuel Split** section on the plots screen - the proportion of fuel consumed
  over the selected date range that was self-produced versus purchased.
+ **Centrifuge Batch Log** - a separate log of each batch of fuel processed:
  date, clean fuel yielded, raw oil processed (optional), notes, and a derived
  yield percentage. Batch records are global, not per-vehicle.
+ **Waste Oil Fuel Savings** in the statistics report - estimated savings, priced
  from the user's own all-time average pump diesel purchase price rather than a
  figure they have to enter and maintain.
+ **Fuel Production vs Use** in the statistics report - centrifuge output
  against Waste Oil Fuel burned, with a signed net.

+ **Fuel Production Log** (previously "Centrifuge Batch Log") with an **Add
  Centrifuge Batch** button on the home screen, both with a custom centrifuge
  icon. Batches record clean fuel produced, waste oil processed and collected,
  blend added and batch cost.
+ **Share Savings** on the Statistics screen: draws a 1080 x 1080 savings card
  for the vehicle and date range (template: `res/layout/savings_card.xml`) and
  opens Facebook's "Share to Groups" screen with it attached, falling back to
  the normal share list. Figures come from `SavingsSummary`, the same
  calculation as the statistics table.

Also changed:

+ Migrated from the Eclipse/ADT + Ant build to Gradle.
+ Database schema version 8: version 6 adds `Records.fuel_type` and the `Batches`
  table; 7 adds batch blend and cost; 8 adds waste oil collected.
  Existing fill-up records are migrated to Pump Diesel.
+ CSV export/import extended with a `fuel_type` field. Older exports without
  that column still import and default to Pump Diesel.
+ Fixed a pre-existing data loss bug in CSV import: `String.split(",")` drops
  trailing empty fields, so a record with empty notes and no mileage was parsed
  as an older-format row and had its cost discarded.

Building
--------

See [BUILD.md](BUILD.md).

Build platform:
+ Gradle 8.7 / Android Gradle Plugin 8.5.2
+ JDK 17
+ compileSdk 34, minSdk 21, targetSdk 29

Dependencies:
+ androidplot-core-0.6.0.jar  (http://www.androidplot.com)
+ androidx.core:core (FileProvider only)


Copyright 2013,2014 William D. Kraemer
Copyright 2026 PA Biodiesel Supply

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this software except in compliance with the License.
You may obtain a copy of the License at:

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
