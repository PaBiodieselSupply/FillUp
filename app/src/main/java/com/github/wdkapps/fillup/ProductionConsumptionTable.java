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
 * A table comparing how much fuel the user's centrifuge produced against how
 * much Black Diesel they burned, over the selected date range. A positive net
 * means production is keeping ahead of driving.<p>
 *
 * NOTES:<p>
 * <ol>
 * <li>The Black Diesel consumed figure comes from the shared
 * {@link FuelTypeTotals}, which is what guarantees it is identical to the
 * "Black Diesel Burned" figure in the savings table directly above this one.
 * Two independent loops would eventually drift apart.
 * <li>Batch records are global while gas records are per-vehicle, so with more
 * than one vehicle defined the two sides of the subtraction have different
 * scopes and the net overstates the surplus. Rather than hide that, a footnote
 * row is emitted whenever more than one vehicle exists. For the single-vehicle
 * user - the overwhelming majority for this application - the two scopes are
 * identical and the footnote never appears.
 * </ol>
 */
public class ProductionConsumptionTable implements HtmlData {

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

	/// gas records for the active vehicle
	private final List<GasRecord> records;

	/// all centrifuge batch records
	private final List<BatchRecord> batches;

	/// computed totals for the selected date range
	private int batchCount;
	private float totalYield;
	private float wvoConsumed;
	private float net;

	/// end of line string
	private static final String newline = System.getProperty("line.separator");

	/**
	 * DESCRIPTION:
	 * Constructs an instance of ProductionConsumptionTable.
	 * @param records - ALL gas records for the vehicle.
	 * @param batches - ALL centrifuge batch records (global).
	 * @param title - the current date range summary, for the table header.
	 */
	public ProductionConsumptionTable(List<GasRecord> records, List<BatchRecord> batches, String title) {
		this.records = records;
		this.batches = batches;
		this.title = String.format(App.getLocale(),
				getString(R.string.stats_table_title),
				getString(R.string.stats_heading_production),
				title);
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
		computeTotals();
		html = new StringBuilder();
		html.append("<table"+property("class",cssClass)+">").append(newline);
		appendTableHeaderRow(title,"2");
		row = 0;
		appendBatchCountData();
		appendTotalYieldData();
		appendConsumedData();
		appendNetData();
		appendScopeFootnote();
		html.append("</table>").append(newline);
	}

	/**
	 * DESCRIPTION:
	 * Calculates all four figures for the currently selected date range. A single
	 * PlotDateRange instance drives every one of them.
	 */
	private void computeTotals() {

		PlotDateRange range = new PlotDateRange(App.getContext(),Settings.KEY_PLOT_DATE_RANGE);

		// both sides come from the shared aggregators, which is what guarantees the
		// consumed figure here matches "Waste Oil Fuel Burned" in the table above
		BatchTotals production = new BatchTotals(batches,range);
		batchCount = production.getCount();
		totalYield = production.getTotalYield();

		wvoConsumed = new FuelTypeTotals(records,range).getWasteOilGallons();
		net = totalYield - wvoConsumed;
	}

	/**
	 * DESCRIPTION:
	 * Appends the number of batches processed to the table.
	 */
	private void appendBatchCountData() {
		String label = getString(R.string.stats_label_batch_count);
		String value = String.format(App.getLocale(),"%d",batchCount);
		appendTableRow(new String[]{label,value});
	}

	/**
	 * DESCRIPTION:
	 * Appends the total volume of clean fuel produced to the table.
	 */
	private void appendTotalYieldData() {
		String label = getString(R.string.stats_label_total_yield);
		String value = String.format(App.getLocale(),getString(R.string.stats_calc_wvo_gallons),
				totalYield, units.getLiquidVolumeLabelLowerCase());
		appendTableRow(new String[]{label,value});
	}

	/**
	 * DESCRIPTION:
	 * Appends the volume of Black Diesel consumed to the table.
	 */
	private void appendConsumedData() {
		String label = getString(R.string.stats_label_wvo_consumed);
		String value = String.format(App.getLocale(),getString(R.string.stats_calc_wvo_gallons),
				wvoConsumed, units.getLiquidVolumeLabelLowerCase());
		appendTableRow(new String[]{label,value});
	}

	/**
	 * DESCRIPTION:
	 * Appends the net production surplus or deficit to the table. The value is
	 * always signed, which is what makes "producing faster than burning" readable
	 * at a glance.
	 */
	private void appendNetData() {
		String label = getString(R.string.stats_label_net);
		String value = String.format(App.getLocale(),getString(R.string.stats_calc_net),
				net, units.getLiquidVolumeLabelLowerCase());
		appendTableRow(new String[]{label,value});
	}

	/**
	 * DESCRIPTION:
	 * Appends a footnote disclosing that batch totals are global while consumption
	 * is per-vehicle. Emitted only when more than one vehicle is defined, since
	 * with a single vehicle the two scopes are the same thing.
	 */
	private void appendScopeFootnote() {
		if (GasLog.getInstance().readAllVehicles().size() > 1) {
			appendSpanningRow(getString(R.string.stats_note_batch_scope));
		}
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
