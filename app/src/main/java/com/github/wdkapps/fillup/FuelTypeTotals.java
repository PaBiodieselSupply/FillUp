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
 * Aggregates gasoline record volumes and costs by fuel type, optionally
 * restricted to a date range.<p>
 *
 * NOTES:<p>
 * <ol>
 * <li>Every feature that reports on fuel type - the fuel split bar on the plot
 * screen, the savings table and the production/consumption table in the
 * statistics report - goes through this one class. That is deliberate: it is
 * what guarantees the "Black Diesel Burned" figure in one table can never
 * disagree with the "Black Diesel Consumed" figure in the table beneath it.
 * <li>A cost greater than zero is the only available proxy for "a cost was
 * recorded". The database column is <code>real not null default 0</code>, so a
 * genuinely free pump purchase is indistinguishable from an unrecorded one and
 * is excluded from the price average. {@link GasLogListAdapter} already treats
 * a zero cost as "absent" for display purposes, so this is consistent.
 * <li>Volumes are summed exactly as stored. The application never converts
 * between gallons and liters - {@link Units} swaps labels only - so a user who
 * changes the units preference part way through their history will get mixed
 * units summed together. That is a pre-existing characteristic of the whole
 * application, not something introduced here.
 * </ol>
 */
public class FuelTypeTotals {

	/// total volume of user-produced fuel (Black Diesel) consumed
	private float blackDieselGallons = 0f;

	/// total volume of purchased fuel (Pump Diesel) consumed
	private float pumpDieselGallons = 0f;

	/// total cost of the Pump Diesel purchases that have a cost recorded
	private double pumpDieselCost = 0d;

	/// total volume of the Pump Diesel purchases that have a cost recorded
	private float pumpDieselCostedGallons = 0f;

	/// number of Pump Diesel purchases that have a cost recorded
	private int pumpDieselCostedCount = 0;

	/**
	 * DESCRIPTION:
	 * Constructs an instance of FuelTypeTotals.
	 * @param records - the gas records to aggregate (may be null or empty).
	 * @param range - the date range to restrict to, or null for no restriction.
	 */
	public FuelTypeTotals(List<GasRecord> records, PlotDateRange range) {

		if (records == null) return;

		for (GasRecord record : records) {

			if (record == null) continue;
			if ((range != null) && !range.contains(record.getDate())) continue;

			float gallons = record.getGallons();

			if (record.getFuelType().isBlackDiesel()) {
				blackDieselGallons += gallons;
			} else {
				pumpDieselGallons += gallons;

				// only purchases with a recorded cost contribute to the price average
				double cost = record.getCost();
				if ((cost > 0d) && (gallons > 0f)) {
					pumpDieselCost += cost;
					pumpDieselCostedGallons += gallons;
					pumpDieselCostedCount++;
				}
			}
		}
	}

	/**
	 * DESCRIPTION:
	 * @return the total volume of Black Diesel consumed.
	 */
	public float getBlackDieselGallons() {
		return blackDieselGallons;
	}

	/**
	 * DESCRIPTION:
	 * @return the total volume of Pump Diesel consumed.
	 */
	public float getPumpDieselGallons() {
		return pumpDieselGallons;
	}

	/**
	 * DESCRIPTION:
	 * @return the total volume of fuel consumed, of both types.
	 */
	public float getTotalGallons() {
		return blackDieselGallons + pumpDieselGallons;
	}

	/**
	 * DESCRIPTION:
	 * @return the total cost of the Pump Diesel purchases that have a cost recorded.
	 */
	public double getPumpDieselCost() {
		return pumpDieselCost;
	}

	/**
	 * DESCRIPTION:
	 * @return the total volume of the Pump Diesel purchases that have a cost recorded.
	 */
	public float getPumpDieselCostedGallons() {
		return pumpDieselCostedGallons;
	}

	/**
	 * DESCRIPTION:
	 * @return the number of Pump Diesel purchases that have a cost recorded.
	 */
	public int getPumpDieselCostedCount() {
		return pumpDieselCostedCount;
	}

	/**
	 * DESCRIPTION:
	 * Calculates the user's average price per unit of Pump Diesel, from their own
	 * purchase history. Purchases without a recorded cost are excluded.
	 * @return the average price per unit, or 0 if no costed purchases exist.
	 */
	public double getAveragePumpPrice() {
		if (!hasAveragePumpPrice()) return 0d;
		return pumpDieselCost / pumpDieselCostedGallons;
	}

	/**
	 * DESCRIPTION:
	 * Indicates whether enough data exists to calculate an average Pump Diesel price.
	 * @return true if an average price is available.
	 */
	public boolean hasAveragePumpPrice() {
		return (pumpDieselCostedCount > 0) && (pumpDieselCostedGallons > 0f);
	}

	/**
	 * DESCRIPTION:
	 * @return the fraction (0.0 - 1.0) of total fuel that was Black Diesel.
	 */
	public float getBlackDieselFraction() {
		float total = getTotalGallons();
		if (total <= 0f) return 0f;
		return blackDieselGallons / total;
	}

	/**
	 * DESCRIPTION:
	 * @return the fraction (0.0 - 1.0) of total fuel that was Pump Diesel.
	 */
	public float getPumpDieselFraction() {
		float total = getTotalGallons();
		if (total <= 0f) return 0f;
		return pumpDieselGallons / total;
	}

	/**
	 * DESCRIPTION:
	 * @return the percentage (0 - 100) of total fuel that was Black Diesel.
	 */
	public int getBlackDieselPercent() {
		if (isEmpty()) return 0;
		return Math.round(getBlackDieselFraction() * 100f);
	}

	/**
	 * DESCRIPTION:
	 * Returns the percentage of total fuel that was Pump Diesel.<p>
	 *
	 * NOTE: this is derived from the Black Diesel percentage rather than rounded
	 * independently. Rounding both separately produces totals of 99% or 101%.
	 *
	 * @return the percentage (0 - 100) of total fuel that was Pump Diesel.
	 */
	public int getPumpDieselPercent() {
		if (isEmpty()) return 0;
		return 100 - getBlackDieselPercent();
	}

	/**
	 * DESCRIPTION:
	 * Indicates whether any fuel at all was recorded.
	 * @return true if no fuel was recorded.
	 */
	public boolean isEmpty() {
		return getTotalGallons() <= 0f;
	}

}
