/*
 * *****************************************************************************
 * Copyright 2013 William D. Kraemer
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * DESCRIPTION:
 * A table for display of statistics derived from multiple months of trip data.
 */
public class StatisticsSummaryTable implements HtmlData {

	/// the trip data
	private List<TripRecord> data;
	
	/// accumulation of all trip data
	private TripRecord total;
	
	/// a title for the table
	private final String title;

	/// css class value
	private final String cssClass = "summary";
	
	/// the html data for the table
	private StringBuilder html;
	
	/// current units of measurement for display of labels
	private Units units;
	
	/// gas records data was derived from
	private ArrayList<GasRecord> records = new ArrayList<GasRecord>();

	/// fuel volumes and pump costs split by fuel type, for the displayed months
	private FuelTypeTotals fuel;

	/// centrifuge batch economics, all time - the source of the production cost rate
	private BatchTotals production;
	
	/// a row index used during creation
	private int row;
	
	/// end of line string
	private static final String newline = System.getProperty("line.separator");
	
	/**
	 * DESCRIPTION:
	 * Constructs an instance of StatisticsReportTable.
	 * @param data - the trip data used to generate the table (current month at index 0)
	 * @param title - the table title.
	 */
	public StatisticsSummaryTable(List<TripRecord> data, List<BatchRecord> batches, String title) {
		this.data = data;
		this.title = String.format(App.getLocale(),
				getString(R.string.stats_table_title),
				getString(R.string.stats_heading_summary),
				title);
		this.total = new TripRecord(new Date());
		for (TripRecord trip : data) {
			this.total.append(trip);
		}
		this.records.addAll(total.getGasRecords());
		Collections.sort(records,new OdometerComparator());

		// The records above are already limited to the displayed months, so these
		// totals need no further date filtering. The production cost RATE, however,
		// deliberately comes from the complete batch history - same reasoning as
		// the savings table, so the two tables agree.
		this.fuel = new FuelTypeTotals(records,null);
		this.production = new BatchTotals(batches,null);

		createTable();
	}

	/**
	 * DESCRIPTION:
	 * Returns the table as an HTML String.
	 * @see com.github.wdkapps.fillup.HtmlData#getHtml()
	 */
	@Override
	public String getHtml() {
		return html.toString();
	}
	
	/**
	 * DESCRIPTION:
	 * Generates an HTML table from the data.
	 */
	private void createTable() {
		units = new Units(Settings.KEY_UNITS);
		html = new StringBuilder();
		html.append("<table"+property("class",cssClass)+">").append(newline);
		appendTableHeaderRow(title,"2");
		row = 0;
		appendMileageData();
		appendDistanceData();
		appendGallonsData();
		appendFuelUsedBreakdown();
		appendCostData();
		appendWasteOilCostData();
		appendTotalCostData();
		appendPriceData();
		html.append("</table>").append(newline);
	}	

	/**
	 * DESCRIPTION:
	 * Generates a generic HTML table header row consisting of a single
	 * cell that spans a specified number of table columns.
	 * @param cell - the header cell content as a String.
	 * @param colspan - the number of table columns that the header cell spans
	 */
	private void appendTableHeaderRow(String cell, String colspan) {
		html.append("<tr"+property("class",cssClass)+">").append(newline);
		html.append("  <th"+property("class",cssClass)+property("colspan",colspan)+">").append(cell).append("</th>").append(newline);
		html.append("</tr>").append(newline);
	}
	
	/**
	 * DESCRIPTION:
	 * Generates a generic HTML table row.<p>
	 * NOTE: marks odd numbered rows to support alternating colors for older browsers.
	 * @param cells - the cell content for the row (each cell is one table column).
	 */
	private void appendTableRow(String[] cells) {
		if ((row & 1) == 1) {
			html.append("<tr"+property("class",cssClass+" odd")+">").append(newline);
		} else {			
			html.append("<tr"+property("class",cssClass)+">").append(newline);
		}
		for (String cell : cells) {
			html.append("  <td"+property("class",cssClass)+">").append(cell).append("</td>").append(newline);
		}		
		html.append("</tr>").append(newline);
		row++;
	}
	
	/**
	 * DESCRIPTION:
	 * Appends distance statistical data to the table.
	 */
	private void appendDistanceData() {

		// calculate monthly average
		int months = getMonthsWithData();
		float average = 0;
		if (months > 0) {
			// float division: Integer/int would truncate, so 17312 miles over 23
			// months showed 752 instead of 753 (752.70 rounded)
			average = (float) total.getDistance() / months;
		}
		
		// create table row
		String label = getString(R.string.stats_label_distance);
		String value = String.format(App.getLocale(),getString(R.string.stats_calc_distance),
    				total.getDistance().intValue(),
    				units.getDistanceLabelLowerCase(),
    				average);
		value = value.replace("(","<br/>(");
		appendTableRow(new String[]{label,value});
	}
	
	/**
	 * DESCRIPTION:
	 * Appends cost statistical data to the table.
	 */
	private void appendCostData() {

		// calculate cost per month
		int months = getMonthsWithData();
		double per_month = 0;
		if (months > 0) {
			per_month = total.getCost()/months;
		}
		
		// calculate cost per mile/kilometer
		double per_mile = 0;
		if (total.getDistance() > 0) {
			per_mile = total.getCost()/total.getDistance();
		}
		
		// NOTE: total.getCost() sums the cost recorded against fill-ups. Waste Oil
		// Fuel fill-ups have no purchase cost, so this is pump spend only - which is
		// what the label now says. What the produced fuel cost to make is a separate
		// row below, so the two are never conflated.
		String label = getString(R.string.stats_label_cost);
		String value = String.format(App.getLocale(),getString(R.string.stats_calc_cost_permonth),
    				CurrencyManager.getInstance().getSymbolicFormatter().format(total.getCost()),
    				CurrencyManager.getInstance().getSymbolicFormatter().format(per_month));
		value = value.replace("(","<br/>(");
		appendTableRow(new String[]{label,value});
	}

	/**
	 * DESCRIPTION:
	 * Appends the fuel type breakdown beneath the Fuel Used row.
	 */
	private void appendFuelUsedBreakdown() {

		if (fuel.isEmpty()) return;

		String format = getString(R.string.stats_calc_gallons_pct);
		String volume = units.getLiquidVolumeLabelLowerCase();

		appendTableRow(new String[]{
				indent(getString(R.string.stats_label_fuel_used_wvo)),
				String.format(App.getLocale(),format,
						fuel.getWasteOilGallons(),volume,fuel.getWasteOilPercent())});

		appendTableRow(new String[]{
				indent(getString(R.string.stats_label_fuel_used_pump)),
				String.format(App.getLocale(),format,
						fuel.getPumpDieselGallons(),volume,fuel.getPumpDieselPercent())});
	}

	/**
	 * DESCRIPTION:
	 * Appends what the Waste Oil Fuel burned cost to produce.<p>
	 *
	 * NOTE: the rate comes from the complete batch history while the volume is
	 * the volume burned in the displayed months, matching how the savings table
	 * works. If waste oil fuel was burned but no batch has a cost recorded, the
	 * cost is unknown - it is reported as not recorded rather than as $0.00,
	 * because treating the fuel as free understates every total below it.
	 */
	private void appendWasteOilCostData() {
		String label = getString(R.string.stats_label_wvo_fuel_cost);
		String value;
		if (isWasteOilPriced()) {
			value = CurrencyManager.getInstance().getSymbolicFormatter().format(getWasteOilCost());
		} else {
			value = getString(R.string.stats_calc_wvo_cost_none);
		}
		appendTableRow(new String[]{label,value});
	}

	/**
	 * DESCRIPTION:
	 * Appends the total recorded cost of the fuel burned, purchased or produced,
	 * and what that works out to per unit of distance.<p>
	 *
	 * NOTE: only costs that were actually recorded are totalled - nothing is
	 * estimated. Consequently:
	 * <ul>
	 * <li>If waste oil fuel was burned but its production cost is unknown, the
	 * total is incomplete in a way that cannot be quantified, so "-" is shown.
	 * <li>If some Pump Diesel fill-ups have no cost (typically imported history),
	 * the total is shown with the unpriced volume stated, and cost per distance
	 * is withheld - dividing a partial cost by the full distance would understate it.
	 * </ul>
	 */
	private void appendTotalCostData() {

		String label = getString(R.string.stats_label_total_fuel_cost);

		if (!isWasteOilPriced()) {
			appendTableRow(new String[]{label,"-"});
			return;
		}

		double totalCost = fuel.getPumpDieselCost() + getWasteOilCost();
		String money = CurrencyManager.getInstance().getSymbolicFormatter().format(totalCost);
		float unpriced = getUnpricedPumpGallons();
		String value;

		if (unpriced > 0f) {
			value = String.format(App.getLocale(),getString(R.string.stats_calc_total_cost_excl),
					money, unpriced, units.getLiquidVolumeLabelLowerCase());
		} else {
			double per_mile = 0;
			if (total.getDistance() > 0) {
				per_mile = totalCost/total.getDistance();
			}
			value = String.format(App.getLocale(),getString(R.string.stats_calc_cost_permile),
					money,
					CurrencyManager.getInstance().getSymbolicFractionalFormatter().format(per_mile),
					units.getDistanceRatioLabel());
		}
		value = value.replace("(","<br/>(");
		appendTableRow(new String[]{label,value});
	}

	/**
	 * DESCRIPTION:
	 * Calculates what the Waste Oil Fuel burned in the displayed months cost to
	 * produce. Only meaningful when {@link #isWasteOilPriced()} is true.
	 * @return the production cost.
	 */
	private double getWasteOilCost() {
		return production.getCostPerUnit() * fuel.getWasteOilGallons();
	}

	/**
	 * DESCRIPTION:
	 * Indicates whether the cost of the waste oil fuel burned is known: either
	 * none was burned, or at least one batch has a production cost recorded.
	 * @return true if the waste oil fuel cost is known.
	 */
	private boolean isWasteOilPriced() {
		return (fuel.getWasteOilGallons() <= 0f) || production.hasCostPerUnit();
	}

	/**
	 * DESCRIPTION:
	 * @return the volume of Pump Diesel burned that has no cost recorded.
	 */
	private float getUnpricedPumpGallons() {
		return Math.max(0f, fuel.getPumpDieselGallons() - fuel.getPumpDieselCostedGallons());
	}

	/**
	 * DESCRIPTION:
	 * Counts the months in the range that have at least one fill-up. Per-month
	 * averages divide by this rather than by every month in the range, so that
	 * empty months - before the first fill-up, or with the 12m button pressed and
	 * six months of data - do not dilute them.
	 * @return the number of months with data (0 if none).
	 */
	private int getMonthsWithData() {
		int count = 0;
		for (TripRecord month : data) {
			if (!month.getGasRecords().isEmpty()) count++;
		}
		return count;
	}

	/**
	 * DESCRIPTION:
	 * Indents a table label, for rows that break down the row above them.
	 * @param label - the label to indent.
	 * @return the indented label as HTML.
	 */
	private String indent(String label) {
		return "&nbsp;&nbsp;&nbsp;&nbsp;" + label;
	}

	/**
	 * DESCRIPTION:
	 * Appends gallons statistical data to the table.
	 */
	private void appendGallonsData() {

		// calculate monthly average
		int months = getMonthsWithData();
		float average = 0;
		if (months > 0) {
			average = total.getGallons()/months;
		}

		// create table row
		String label = getString(R.string.stats_label_gallons);
		String value = String.format(App.getLocale(),getString(R.string.stats_calc_gallons),
    				total.getGallons(),
    				units.getLiquidVolumeLabelLowerCase(),
    				average);
		value = value.replace("(","<br/>(");
		appendTableRow(new String[]{label,value});
	}

	/**
	 * DESCRIPTION:
	 * Appends price statistical data to the table.
	 */
	private void appendPriceData() {

		// What every unit of fuel actually cost, purchased or produced.
		//
		// NOTE: the numerator and the denominator must cover exactly the same fuel.
		// Only fuel whose cost is known is included: priced Pump Diesel fill-ups
		// plus waste oil fuel at the production rate. An unpriced pump fill-up would
		// otherwise add its gallons with no cost, and unpriced waste oil fuel would
		// count as free - both drag the figure below anything actually paid. When
		// the waste oil fuel cost is unknown, "-" is shown rather than a guess. The
		// isolated pump price lives in the savings table.
		String label = getString(R.string.stats_label_blended_price);
		String value = "-";

		float gallons = fuel.getPumpDieselCostedGallons() + fuel.getWasteOilGallons();
		if (isWasteOilPriced() && (gallons > 0f)) {
			double price = (fuel.getPumpDieselCost() + getWasteOilCost()) / gallons;
			value = String.format("%s %s",
					CurrencyManager.getInstance().getSymbolicFractionalFormatter().format(price),
					units.getLiquidVolumeRatioLabel());
		}
		appendTableRow(new String[]{label,value});
	}

	/**
	 * DESCRIPTION:
	 * Appends mileage statistical data to the table.
	 */
	private void appendMileageData() {
		
		float min = Float.MAX_VALUE;
		float max = 0f;
		int distance = 0;
		float fuelUsed = 0f;
		int count = 0;
		for (GasRecord record : records) {
			if (!record.hasCalculation()) continue;
			if (record.isCalculationHidden()) continue;
			float mileage = record.getCalculation().getMileage();
			min = Math.min(min,mileage);
			max = Math.max(max,mileage);
			distance += record.getCalculation().getDistanceDriven();
			fuelUsed += record.getCalculation().getGasolineUsed();
			count++;
		}

		// NOTE: the average is total distance / total fuel across the full-tank
		// intervals, not the mean of each fill-up's figure. A mean of ratios lets a
		// short top-up count as much as a full tank: 100 mi on 2 gal and 400 mi on
		// 20 gal would average to 35.0 MPG when the vehicle actually got 22.7.
		float overall = MileageCalculation.calculate(distance,fuelUsed,units);
		
		String label;
		String value;

		// average
		label = getString(R.string.stats_label_mileage_avg);
		value = "-";
		if (count > 0) 	value = String.format(App.getLocale(),"%.2f %s",overall,units.getMileageLabel());
		appendTableRow(new String[]{label,value});
		
		// minimum
		label = getString(R.string.stats_label_mileage_min);
		value = "-";
		if (count > 0) 	value = String.format(App.getLocale(),"%.2f %s",min,units.getMileageLabel());
		appendTableRow(new String[]{label,value});

		// maximum
		label = getString(R.string.stats_label_mileage_max);
		value = "-";
		if (count > 0) 	value = String.format(App.getLocale(),"%.2f %s",max,units.getMileageLabel());
		appendTableRow(new String[]{label,value});
	}
	
	/**
	 * DESCRIPTION:
	 * Returns an HTML property definition in the form of: attribute="value".
	 * @param attribute - the attribute name of the property.
	 * @param value - the value to be assigned to the property.
	 * @return the property definition String.
	 */
	private String property(String attribute, String value) {
		return String.format(" %s=\"%s\"", attribute, value);
	}
	
	/**
	 * DESCRIPTION:
	 * Convenience method to retrieve a String resource.
	 * @param id - the desired resource identifier. 
	 * @return the String data associated with the resource.
	 * @see android.content.res.Resources#getString(int id)
	 */
	private String getString(int id) {
		return App.getContext().getResources().getString(id);
	}
	
}
