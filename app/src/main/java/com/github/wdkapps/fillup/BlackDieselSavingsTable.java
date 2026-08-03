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
 * A table estimating what the user has saved by burning Black Diesel they
 * produced themselves, instead of buying Pump Diesel.<p>
 *
 * The price used is derived entirely from the user's own purchase history, so it
 * is a genuine personalized average rather than an estimate they have to type in
 * and keep up to date:
 * <pre>
 *     average price = total cost of costed Pump Diesel fill-ups
 *                     / total volume of those same fill-ups
 *     savings       = average price * volume of Black Diesel burned
 * </pre>
 *
 * NOTES:<p>
 * <ol>
 * <li>The date range applies to the Black Diesel volume and therefore to the
 * savings figure, but the <b>average price deliberately uses the complete
 * history</b>, so that the price baseline stays as accurate as possible even
 * when the user is looking at a short window.
 * <li>Because of that, the price row carries an explicit "all time"
 * qualification. Without it, a user switching from All to Past Month would watch
 * every figure on the screen change except one, and reasonably conclude it was
 * broken. Surfacing the mixed semantics is required, not decorative.
 * <li>The css class is "summary", which is already defined in the report's
 * header asset. That is deliberate: asset_stats_top_html is a localized string
 * resource, so introducing a new css class would mean editing three separate
 * asset files, and missing one would leave those users with an unstyled table.
 * </ol>
 */
public class BlackDieselSavingsTable implements HtmlData {

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

	/// totals over the complete history - the source of the average price
	private final FuelTypeTotals allTime;

	/// totals over the selected date range - the source of the volume burned
	private final FuelTypeTotals inRange;

	/// end of line string
	private static final String newline = System.getProperty("line.separator");

	/**
	 * DESCRIPTION:
	 * Constructs an instance of BlackDieselSavingsTable.
	 * @param records - ALL gas records for the vehicle, unfiltered by date.
	 * @param title - the current date range summary, for the table header.
	 */
	public BlackDieselSavingsTable(List<GasRecord> records, String title) {
		this.title = getString(R.string.stats_savings_prefix) + title;

		// no range: the price baseline uses the complete history
		this.allTime = new FuelTypeTotals(records,null);

		// ranged: the volume burned and the savings follow the range buttons
		this.inRange = new FuelTypeTotals(records,
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
		if (allTime.hasAveragePumpPrice()) {
			appendAvgPumpPriceData();
			appendBlackDieselBurnedData();
			appendEstimatedSavingsData();
		} else {
			appendNoCostDataMessage();
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
						.format(allTime.getAveragePumpPrice()),
				units.getLiquidVolumeRatioLabel(),
				allTime.getPumpDieselCostedCount());
		value = value.replace("(","<br/>(");
		appendTableRow(new String[]{label,value});
	}

	/**
	 * DESCRIPTION:
	 * Appends the volume of Black Diesel burned to the table.
	 */
	private void appendBlackDieselBurnedData() {
		String label = getString(R.string.stats_label_wvo_burned);
		String value = String.format(App.getLocale(),getString(R.string.stats_calc_wvo_gallons),
				inRange.getBlackDieselGallons(),
				units.getLiquidVolumeLabelLowerCase());
		appendTableRow(new String[]{label,value});
	}

	/**
	 * DESCRIPTION:
	 * Appends the estimated savings to the table.
	 */
	private void appendEstimatedSavingsData() {
		double savings = allTime.getAveragePumpPrice() * inRange.getBlackDieselGallons();
		String label = getString(R.string.stats_label_est_savings);
		String value = CurrencyManager.getInstance().getSymbolicFormatter().format(savings);
		appendTableRow(new String[]{label,value});
	}

	/**
	 * DESCRIPTION:
	 * Appends a message explaining why no savings can be calculated.
	 */
	private void appendNoCostDataMessage() {
		appendSpanningRow(getString(R.string.stats_message_no_cost_data));
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
