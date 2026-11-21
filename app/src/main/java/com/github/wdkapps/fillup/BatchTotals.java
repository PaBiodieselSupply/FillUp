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
 * Aggregates centrifuge batch records, optionally restricted to a date range.<p>
 *
 * This is the counterpart to {@link FuelTypeTotals} and exists for the same
 * reason: the savings table, the production table and the statistics summary all
 * need to know what producing fuel costs, and routing them through one class is
 * what stops those figures from disagreeing with each other.<p>
 *
 * NOTES:<p>
 * <ol>
 * <li>Only batches with a cost recorded contribute to the cost per unit. A batch
 * logged before cost tracking existed, or one the user did not price, is counted
 * in the yield totals but excluded from the rate - otherwise every unpriced
 * batch would silently drag the production cost towards zero and overstate
 * savings.
 * <li>Batches are global, not per-vehicle. The cost per unit is a rate, so
 * applying it to one vehicle's consumption is sound even though the batches
 * themselves are not vehicle specific.
 * </ol>
 */
public class BatchTotals {

	/// number of batches in range
	private int count = 0;

	/// total fuel produced by those batches, including any blend stock
	private float totalYield = 0f;

	/// total waste oil collected for those batches (where recorded)
	private float totalCollected = 0f;

	/// total cost of the batches that have a cost recorded
	private double costedCost = 0d;

	/// clean fuel produced by those same costed batches
	private float costedYield = 0f;

	/// number of batches that have a cost recorded
	private int costedCount = 0;

	/// total blend percentage, for averaging
	private float blendSum = 0f;

	/// number of batches with a blend percentage recorded
	private int blendCount = 0;

	/**
	 * DESCRIPTION:
	 * Constructs an instance of BatchTotals.
	 * @param batches - the batch records to aggregate (may be null or empty).
	 * @param range - the date range to restrict to, or null for no restriction.
	 */
	public BatchTotals(List<BatchRecord> batches, PlotDateRange range) {

		if (batches == null) return;

		for (BatchRecord batch : batches) {

			if (batch == null) continue;
			if ((range != null) && !range.contains(batch.getDate())) continue;

			// NOTE: totals use the batch's TOTAL fuel, blend included. That is what
			// goes into the tank, so it is the like-for-like comparison with waste
			// oil fuel consumed, and what the batch cost is spread over.
			float output = batch.getTotalGallons();

			count++;
			totalYield += output;
			totalCollected += batch.getCollectedGallons();

			if (batch.hasCost() && (output > 0f)) {
				costedCost += batch.getCost();
				costedYield += output;
				costedCount++;
			}

			if (batch.hasBlendPercent()) {
				blendSum += batch.getBlendPercent();
				blendCount++;
			}
		}
	}

	/**
	 * DESCRIPTION:
	 * @return the number of batches processed.
	 */
	public int getCount() {
		return count;
	}

	/**
	 * DESCRIPTION:
	 * @return the total volume of fuel produced, including any blend stock.
	 */
	public float getTotalYield() {
		return totalYield;
	}

	/**
	 * DESCRIPTION:
	 * @return the total waste oil collected, where recorded.
	 */
	public float getTotalCollected() {
		return totalCollected;
	}

	/**
	 * DESCRIPTION:
	 * @return the number of batches that have a production cost recorded.
	 */
	public int getCostedCount() {
		return costedCount;
	}

	/**
	 * DESCRIPTION:
	 * @return the total production cost of the batches that have one recorded.
	 */
	public double getTotalCost() {
		return costedCost;
	}

	/**
	 * DESCRIPTION:
	 * Calculates what one unit of produced fuel costs, from the batches that
	 * have a cost recorded.
	 * @return the cost per unit, or 0 if no batch has a cost recorded.
	 */
	public double getCostPerUnit() {
		if (!hasCostPerUnit()) return 0d;
		return costedCost / costedYield;
	}

	/**
	 * DESCRIPTION:
	 * Indicates whether enough data exists to calculate a production cost.
	 * @return true if a cost per unit is available.
	 */
	public boolean hasCostPerUnit() {
		return (costedCount > 0) && (costedYield > 0f);
	}

	/**
	 * DESCRIPTION:
	 * Calculates the average blend percentage across the batches that recorded one.
	 * @return the average blend percentage, or 0 if none recorded.
	 */
	public float getAverageBlendPercent() {
		if (blendCount == 0) return 0f;
		return blendSum / blendCount;
	}

	/**
	 * DESCRIPTION:
	 * Indicates whether any batch recorded a blend percentage.
	 * @return true if an average blend percentage is available.
	 */
	public boolean hasBlendPercent() {
		return blendCount > 0;
	}

}
