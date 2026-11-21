/*
 * *****************************************************************************
 * Copyright 2013 William D. Kraemer
 * Copyright 2026 PA Biodiesel Supply
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ****************************************************************************
 */

package com.github.wdkapps.fillup;

import java.util.List;

/**
 * DESCRIPTION:
 * The waste oil fuel savings for one vehicle over a date range: the single
 * source of truth for both the savings table in the statistics report and the
 * shareable savings card. Two copies of this arithmetic would eventually
 * disagree, and a customer comparing the card they posted with the app would
 * notice.<p>
 *
 * The rules (see FORMULAS.md):
 * <ul>
 * <li>Pump price: this vehicle's priced Pump Diesel fill-ups, all time. If it
 * has none - a vehicle that runs only on waste oil fuel - every vehicle's.
 * <li>Production cost: every priced centrifuge batch, all time, spread over the
 * total fuel each batch made including any blend.
 * <li>Volumes: the selected date range.
 * <li>Savings: (pump price - production cost) x waste oil fuel burned - and
 * unknown, not $0, when waste oil fuel was burned but no batch has a cost.
 * </ul>
 */
final class SavingsSummary {

	/// all-time fuel totals that the pump price comes from
	private final FuelTypeTotals pumpHistory;

	/// true when this vehicle had no priced pump fill-ups and every vehicle's were used
	private final boolean pumpPriceFromAllVehicles;

	/// all-time centrifuge batch totals - the source of the production cost
	private final BatchTotals production;

	/// fuel totals for the selected date range
	private final FuelTypeTotals inRange;

	/**
	 * DESCRIPTION:
	 * Constructs an instance of SavingsSummary.
	 * @param records - ALL gas records for the vehicle, unfiltered by date.
	 * @param batches - ALL centrifuge batch records.
	 * @param range - the selected date range.
	 */
	SavingsSummary(List<GasRecord> records, List<BatchRecord> batches, PlotDateRange range) {

		FuelTypeTotals vehicleFuel = new FuelTypeTotals(records,null);
		if (vehicleFuel.hasAveragePumpPrice()) {
			this.pumpHistory = vehicleFuel;
			this.pumpPriceFromAllVehicles = false;
		} else {
			this.pumpHistory = new FuelTypeTotals(GasLog.getInstance().readAllRecordsAllVehicles(),null);
			this.pumpPriceFromAllVehicles = true;
		}

		this.production = new BatchTotals(batches,null);
		this.inRange = new FuelTypeTotals(records,range);
	}

	/** @return true if any vehicle has a priced Pump Diesel fill-up. */
	boolean hasPumpPrice() {
		return pumpHistory.hasAveragePumpPrice();
	}

	/** @return the average Pump Diesel price per unit. */
	double getPumpPrice() {
		return pumpHistory.getAveragePumpPrice();
	}

	/** @return how many priced fill-ups the pump price came from. */
	int getPumpPriceCount() {
		return pumpHistory.getPumpDieselCostedCount();
	}

	/** @return true if the pump price came from every vehicle, not just this one. */
	boolean isPumpPriceFromAllVehicles() {
		return pumpPriceFromAllVehicles;
	}

	/** @return the all-time centrifuge batch totals. */
	BatchTotals getProduction() {
		return production;
	}

	/** @return the fuel totals for the selected date range. */
	FuelTypeTotals getInRange() {
		return inRange;
	}

	/**
	 * DESCRIPTION:
	 * Indicates whether the cost of the waste oil fuel burned in the range is
	 * known: either none was burned, or at least one batch has a cost.
	 * @return true if the waste oil fuel cost is known.
	 */
	boolean isWasteOilPriced() {
		return (inRange.getWasteOilGallons() <= 0f) || production.hasCostPerUnit();
	}

	/**
	 * DESCRIPTION:
	 * @return true if a net savings figure can be stated.
	 */
	boolean hasSavings() {
		return hasPumpPrice() && isWasteOilPriced();
	}

	/**
	 * DESCRIPTION:
	 * Calculates the estimated net savings: what the waste oil fuel burned would
	 * have cost at the pump, less what it cost to produce. Only meaningful when
	 * {@link #hasSavings()} is true.
	 * @return the net savings.
	 */
	double getSavings() {
		return (getPumpPrice() - production.getCostPerUnit()) * inRange.getWasteOilGallons();
	}

	/**
	 * DESCRIPTION:
	 * Indicates whether a blended price - what every unit burned actually cost,
	 * purchased or produced - can be stated for the range.
	 * @return true if a blended price is available.
	 */
	boolean hasBlendedPrice() {
		return isWasteOilPriced()
				&& ((inRange.getPumpDieselCostedGallons() + inRange.getWasteOilGallons()) > 0f);
	}

	/**
	 * DESCRIPTION:
	 * Calculates the blended price over priced fuel only, matching the summary
	 * table: (pump spend + waste oil production cost) / (priced pump volume +
	 * waste oil volume). Only meaningful when {@link #hasBlendedPrice()} is true.
	 * @return the blended price per unit.
	 */
	double getBlendedPrice() {
		double cost = inRange.getPumpDieselCost()
				+ (production.getCostPerUnit() * inRange.getWasteOilGallons());
		return cost / (inRange.getPumpDieselCostedGallons() + inRange.getWasteOilGallons());
	}

}
