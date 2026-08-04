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
 * A table estimating what the user has saved by burning Waste Oil Fuel they
 * produced themselves, instead of buying Pump Diesel.<p>
 *
 * Both rates come from the user's own records, so this is a personalised figure
 * rather than an estimate they have to type in and keep up to date:
 * <pre>
 *     pump price      = cost of costed Pump Diesel fill-ups / their volume
 *     production cost = cost of costed centrifuge batches   / their yield
 *     net savings     = (pump price - production cost) x volume burned
 * </pre>
 *
 * NOTES:<p>
 * <ol>
 * <li>The savings figure is <b>net</b>: what the fuel cost to produce is
 * subtracted. Reporting the gross figure would overstate the saving by exactly
 * the production cost, and would not survive a customer checking it.
 * <li>The date range applies to the volume burned and therefore to the savings,
 * but <b>both rates deliberately use the complete history</b>, so the baseline
 * stays as accurate as possible when looking at a short window. The rate rows
 * say "all time" explicitly - without that, a user switching from All to Past
 * Month would watch every figure change except two and assume a bug.
 * <li>If no batch has a cost recorded, production cost is zero and the result is
 * the gross figure. That is the honest answer to "I have not told the app what
 * my fuel costs to make".
 * <li>A negative result is displayed rather than clamped: if producing costs
 * more per unit than buying, that is worth knowing.
 * <li>The css class is "summary", which is already defined in the report's
 * header asset. Introducing a new class would mean editing three localised asset
 * files, and missing one would leave those users with an unstyled table.
 * </ol>
 */
public class WasteOilSavingsTable implements HtmlData {

	/// a title for the table
	private final String title;

	/// css class value
	private final String cssClass = "summary";

	/// the html data for the table
	private StringBuilder html;

	/// current units of measurement for display of labels
	private Units units;

	/// a row index used during creation
	private int row;

	/// fill-up totals over the complete history - the source of the pump price
	private final FuelTypeTotals allTimeFuel;

	/// fill-up totals over the selected date range - the source of the volume burned
	private final FuelTypeTotals inRangeFuel;

	/// batch totals over the complete history - the source of the production cost
	private final BatchTotals allTimeBatches;

	/// end of line string
	private static final String newline = System.getProperty("line.separator");

	/**
	 * DESCRIPTION:
	 * Constructs an instance of WasteOilSavingsTable.
	 * @param records - ALL gas records for the vehicle, unfiltered by date.
	 * @param batches - ALL centrifuge batch records, unfiltered by date.
	 * @param title - the current date range summary, for the table header.
	 */
	public WasteOilSavingsTable(List<GasRecord> records, List<BatchRecord> batches, String title) {

		this.title = String.format(App.getLocale(),
				getString(R.string.stats_table_title),
				getString(R.string.stats_heading_savings),
				title);

		// no range: both rates use the complete history
		this.allTimeFuel = new FuelTypeTotals(records,null);
		this.allTimeBatches = new BatchTotals(batches,null);

		// ranged: the volume burned and the savings follow the range buttons
		this.inRangeFuel = new FuelTypeTotals(records,
				new PlotDateRange(App.getContext(),Settings.KEY_PLOT_DATE_RANGE));

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
		if (allTimeFuel.hasAveragePumpPrice()) {
			appendAvgPumpPriceData();
			appendProductionCostData();
			appendWasteOilBurnedData();
			appendEstimatedSavingsData();
		} else {
			appendSpanningRow(getString(R.string.stats_message_no_cost_data));
		}
		html.append("</table>").append(newline);
	}

	/**
	 * DESCRIPTION:
	 * Appends the user's average Pump Diesel price to the table.
	 */
	private void appendAvgPumpPriceData() {
		String label = getString(R.string.stats_label_avg_pump_price);
		String value = String.format(App.getLocale(),getString(R.string.stats_calc_avg_pump_price),
				CurrencyManager.getInstance().getSymbolicFractionalFormatter()
						.format(allTimeFuel.getAveragePumpPrice()),
				units.getLiquidVolumeRatioLabel(),
				allTimeFuel.getPumpDieselCostedCount());
		value = value.replace("(","<br/>(");
		appendTableRow(new String[]{label,value});
	}

	/**
	 * DESCRIPTION:
	 * Appends what the user's own fuel costs to produce.
	 */
	private void appendProductionCostData() {

		String label = getString(R.string.stats_label_production_cost);
		String value;

		if (allTimeBatches.hasCostPerUnit()) {
			value = String.format(App.getLocale(),getString(R.string.stats_calc_production_cost),
					CurrencyManager.getInstance().getSymbolicFractionalFormatter()
							.format(allTimeBatches.getCostPerUnit()),
					units.getLiquidVolumeRatioLabel(),
					allTimeBatches.getCostedCount());
			value = value.replace("(","<br/>(");
		} else {
			value = getString(R.string.stats_calc_production_cost_none);
		}

		appendTableRow(new String[]{label,value});
	}

	/**
	 * DESCRIPTION:
	 * Appends the volume of Waste Oil Fuel burned to the table.
	 */
	private void appendWasteOilBurnedData() {
		String label = getString(R.string.stats_label_wvo_burned);
		String value = String.format(App.getLocale(),getString(R.string.stats_calc_wvo_gallons),
				inRangeFuel.getWasteOilGallons(),
				units.getLiquidVolumeLabelLowerCase());
		appendTableRow(new String[]{label,value});
	}

	/**
	 * DESCRIPTION:
	 * Appends the estimated net savings to the table.
	 */
	private void appendEstimatedSavingsData() {
		String label = getString(R.string.stats_label_est_savings);
		String value = CurrencyManager.getInstance().getSymbolicFormatter().format(getNetSavings());
		appendTableRow(new String[]{label,value});
	}

	/**
	 * DESCRIPTION:
	 * Calculates the estimated net savings: what the fuel would have cost at the
	 * pump, less what it cost to produce.
	 * @return the net savings.
	 */
	private double getNetSavings() {
		double saving = allTimeFuel.getAveragePumpPrice() - allTimeBatches.getCostPerUnit();
		return saving * inRangeFuel.getWasteOilGallons();
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
	 * Generates an HTML table row consisting of a single cell that spans both
	 * table columns.
	 * @param cell - the cell content as a String.
	 */
	private void appendSpanningRow(String cell) {
		html.append("<tr"+property("class",cssClass)+">").append(newline);
		html.append("  <td"+property("class",cssClass)+property("colspan","2")+">").append(cell).append("</td>").append(newline);
		html.append("</tr>").append(newline);
		row++;
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
