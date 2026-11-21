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

import android.app.Activity;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

/**
 * DESCRIPTION:
 * An Android ArrayAdapter to format and display centrifuge batch records in a
 * ListView. Structured to match {@link GasLogListAdapter}.
 */
public class BatchLogListAdapter extends ArrayAdapter<BatchRecord> {

	/// the Activity that owns the list
	private final Activity activity;

	/// the batch records to display
	private final List<BatchRecord> records;

	/// the current units preference
	private Units units;

	/// whether notes should be displayed in the log
	private boolean isNotesDisplayable;

	/**
	 * DESCRIPTION:
	 * Constructs an instance of BatchLogListAdapter.
	 * @param activity - the Activity that owns the list.
	 * @param records - the batch records to display.
	 */
	public BatchLogListAdapter(Activity activity, List<BatchRecord> records) {
		super(activity, R.layout.row_batch_log_list, records);
		this.activity = activity;
		this.records = records;
		getSettings();
	}

	/**
	 * DESCRIPTION:
	 * Reads the shared preferences that affect how rows are displayed.
	 */
	private void getSettings() {
		units = new Units(Settings.KEY_UNITS);
		isNotesDisplayable = Settings.isNotesDisplayable();
	}

	/**
	 * DESCRIPTION:
	 * Returns a View to display the batch record at the specified position.
	 * @see android.widget.ArrayAdapter#getView(int, android.view.View, android.view.ViewGroup)
	 */
	@Override
	public View getView(int position, View view, ViewGroup parent) {

		// create a view for the row if it doesn't already exist
		if (view == null) {
			LayoutInflater inflater = activity.getLayoutInflater();
			view = inflater.inflate(R.layout.row_batch_log_list,null);
		}

		// get widgets from the view
		TextView columnBatchDate = (TextView)view.findViewById(R.id.columnBatchDate);
		TextView columnBatchYield = (TextView)view.findViewById(R.id.columnBatchYield);
		TextView columnBatchPercent = (TextView)view.findViewById(R.id.columnBatchPercent);
		TextView rowBatchRaw = (TextView)view.findViewById(R.id.rowBatchRaw);
		TextView rowBatchCost = (TextView)view.findViewById(R.id.rowBatchCost);
		TextView rowBatchNotes = (TextView)view.findViewById(R.id.rowBatchNotes);

		// populate row widgets from record data
		BatchRecord record = records.get(position);

		// date
		columnBatchDate.setText(record.getDateString());

		// clean fuel yielded, e.g. "55.0 gallons clean"
		String format = App.getContext().getString(R.string.batch_row_yield);
		columnBatchYield.setText(String.format(App.getLocale(),format,
				record.getYieldGallons(),
				units.getLiquidVolumeLabelLowerCase()));

		// yield percentage (only calculable when raw oil was recorded)
		columnBatchPercent.setText(record.getYieldPercentString());

		// oil processed, oil collected and blend, joined on one line
		// (both optional - the line is hidden only if neither was recorded)
		StringBuilder inputs = new StringBuilder();
		if (record.hasRawGallons()) {
			format = App.getContext().getString(R.string.batch_row_raw);
			inputs.append(String.format(App.getLocale(),format,
					record.getRawGallons(),
					units.getLiquidVolumeLabelLowerCase()));
		}
		if (record.hasCollectedGallons()) {
			if (inputs.length() > 0) inputs.append(" · ");
			format = App.getContext().getString(R.string.batch_row_collected);
			inputs.append(String.format(App.getLocale(),format,
					record.getCollectedGallons(),
					units.getLiquidVolumeLabelLowerCase()));
		}
		if (record.hasBlendPercent()) {
			// the blend is added on top of the clean fuel, so show the total it makes
			if (inputs.length() > 0) inputs.append(" · ");
			format = App.getContext().getString(R.string.batch_row_blend);
			inputs.append(String.format(App.getLocale(),format,
					record.getBlendPercentString(),
					record.getTotalGallons(),
					units.getLiquidVolumeLabelLowerCase()));
		}
		if (inputs.length() == 0) {
			rowBatchRaw.setVisibility(View.GONE);
		} else {
			rowBatchRaw.setText(inputs.toString());
			rowBatchRaw.setVisibility(View.VISIBLE);
		}

		// production cost and cost per unit (optional)
		Double costPerUnit = record.getCostPerUnit();
		if (costPerUnit == null) {
			rowBatchCost.setVisibility(View.GONE);
		} else {
			format = App.getContext().getString(R.string.batch_row_cost);
			rowBatchCost.setText(String.format(App.getLocale(),format,
					CurrencyManager.getInstance().getSymbolicFormatter().format(record.getCost()),
					CurrencyManager.getInstance().getSymbolicFractionalFormatter().format(costPerUnit),
					units.getLiquidVolumeRatioLabel()));
			rowBatchCost.setVisibility(View.VISIBLE);
		}

		// notes (don't display if blank)
		String notes = record.getNotes();
		if (!isNotesDisplayable || (notes == null) || notes.trim().isEmpty()) {
			rowBatchNotes.setVisibility(View.GONE);
		} else {
			notes = String.format("<b>%s</b>: %s",
					App.getContext().getString(R.string.notes_label),
					notes);
			rowBatchNotes.setText(Html.fromHtml(notes));
			rowBatchNotes.setVisibility(View.VISIBLE);
		}

		// return the view
		return view;
	}

	/**
	 * DESCRIPTION:
	 * Called by parent when the underlying data set changes.
	 * @see android.widget.ArrayAdapter#notifyDataSetChanged()
	 */
	@Override
	public void notifyDataSetChanged() {
		getSettings();
		super.notifyDataSetChanged();
	}

}
