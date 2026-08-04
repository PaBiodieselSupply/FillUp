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

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import android.content.res.AssetManager;
import android.util.Log;

/**
 * DESCRIPTION:
 * A report for display of statistics derived from monthly trip data.
 */
public class StatisticsReport implements HtmlData {

	/// a tag string for debug logging (the name of this class)
	private static final String TAG = StatisticsReport.class.getName();
	
	/// end of line string
	private static final String newline = System.getProperty("line.separator");
	
	/// the report title
	private final String title;
	
	/// the monthly trip data for the report
	private final MonthlyTrips monthly;
	
	/// the html data for the report
	private StringBuilder html;
	
	/// one table for each month of statistical data
	private List<HtmlData> tables;

	/// ALL gas records for the vehicle, unfiltered by date
	private final List<GasRecord> records;

	/// ALL centrifuge batch records (global, not vehicle specific)
	private final List<BatchRecord> batches;

	/**
	 * DESCRIPTION:
	 * Constructs an instance of StatisticsReport.
	 * @param title - the report title
	 * @param monthly - the monthly trip data used to generate the report.
	 * @param records - ALL gas records for the vehicle, unfiltered by date. The
	 * savings table needs the complete history to compute an all-time average
	 * pump price, which is why this is passed in addition to the monthly data.
	 * @param batches - ALL centrifuge batch records.
	 */
	public StatisticsReport (String title, MonthlyTrips monthly,
			List<GasRecord> records, List<BatchRecord> batches) {

		this.title = title;
		this.monthly = monthly;
		this.records = records;
		this.batches = batches;


		// create the statistics tables
		createTables();
		
		// create the entire report from the table data
		createReport();
	}

	/**
	 * DESCRIPTION:
	 * Returns the report as an HTML String.
	 * @see com.github.wdkapps.fillup.HtmlData#getHtml()
	 */
	@Override
	public String getHtml() {
		return html.toString();
	}
	
	/**
	 * DESCRIPTION:
	 * Generates statistical tables for the report.
	 */
	private void createTables() {
		String label;
		TripRecord data;
		tables = new LinkedList<HtmlData>();
		List<TripRecord> months = new ArrayList<TripRecord>();

		// create tables for months in range
		for (Month month : monthly) {
			data = monthly.getTrips(month);
			label = month.getLongLabel();
			tables.add(0,new StatisticsMonthTable(data,label));
			months.add(0,data);
		}

		// create table for summary of all data in range
		// note: no need for summary if only displaying one month table
		if (months.size() > 1) {
			tables.add(0,new StatisticsSummaryTable(months,batches,title));
		}

		// PA Biodiesel additions. NOTE: every insert above and below is at index 0,
		// so the LAST insert ends up at the top of the report. The final order is:
		// savings, production vs consumption, summary, then the month tables
		// newest first.

		// production vs consumption stays invisible until the user logs a batch
		if ((batches != null) && !batches.isEmpty()) {
			tables.add(0,new ProductionConsumptionTable(records,batches,title));
		}

		tables.add(0,new WasteOilSavingsTable(records,batches,title));
	}
	
	/**
	 * DESCRIPTION:
	 * Generates an HTML page for the report.
	 */
	private void createReport() {
		String stats_top = App.getContext().getString(R.string.asset_stats_top_html);
		String stats_bottom = App.getContext().getString(R.string.asset_stats_bottom_html);
		html = new StringBuilder();
		try {
			// append stats_top.html
			html.append(readAssetFile(stats_top));

			// append table data
			for (HtmlData table : tables) {
				html.append("<div>").append(newline);
				html.append(table.getHtml());
				html.append("</div>").append(newline);
				html.append("<p/>").append(newline);
			}
			
			// append stats_bottom.html
			html.append(readAssetFile(stats_bottom));
		} catch (Throwable t) {
			String errmsg = App.getContext().getString(R.string.toast_create_report_failed);
			Log.e(TAG,"Error creating report",t);
			html = new StringBuilder();
			html.append("<html>");
			html.append(errmsg).append("<br/>");
			html.append(t.getMessage());
			html.append("/html>");
		}
		
	}
	
	/**
	 * DESCRIPTION:
	 * Reads the content of a specified asset file as String data.
	 * @param asset - the name of the asset file.
	 * @return the content of the file as a String.
	 * @throws IOException if an error occurs reading the file.
	 */
	private String readAssetFile(String asset) throws IOException {
		AssetManager assetmgr = App.getContext().getAssets();
		StringBuilder text = new StringBuilder();
		Scanner scanner = null;
		try {
			scanner = new Scanner(assetmgr.open(asset));
			while (scanner.hasNextLine()){
				text.append(scanner.nextLine());
				text.append(newline);
			}
		} finally {
			if (scanner != null) scanner.close();
		}		
		
		return text.toString();
	}

}
