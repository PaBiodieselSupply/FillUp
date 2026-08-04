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

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * DESCRIPTION:
 * Manages the fuel split section of the plot screen: a horizontal stacked bar
 * showing what proportion of the fuel consumed over the selected date range was
 * Waste Oil Fuel the user produced themselves, versus Pump Diesel they bought.<p>
 *
 * NOTES:<p>
 * <ol>
 * <li>This is shaped like the sibling plot classes ({@link MileagePlot},
 * {@link GallonsPlot}, and so on) so that it reads as one of them, but it is
 * <b>not</b> an AndroidPlot chart. The bundled AndroidPlot 0.6.0 has no
 * horizontal stacked bar renderer, and the section it draws is a percentage
 * strip, not a graph.
 * <li>It deliberately has <b>no</b> setHeight(int) method. Every real plot on
 * the screen is resized to 85% of the scroll view height by PlotActivity's
 * layout listener; a percentage strip must be excluded from that, and the
 * missing method is what documents the exclusion.
 * <li>Date range control comes for free: {@link PlotDateRangeButtons} writes the
 * range preference, PlotActivity is already registered as a preference listener,
 * and {@link #update()} reads a fresh {@link PlotDateRange} on every call.
 * </ol>
 */
public class FuelSplitPlot implements OnSharedPreferenceChangeListener {

	/// the parent Activity
	private Activity activity;

	/// the stacked bar and its two segments
	private LinearLayout bar;
	private View barWasteOil;
	private View barPumpDiesel;

	/// the summary label, and the message shown when there is no data
	private TextView label;
	private TextView labelEmpty;

	/// the current units preference
	private Units units;

	/**
	 * DESCRIPTION:
	 * Called when the parent Activity is created.
	 * @param savedInstanceState - the saved state (unused, for signature parity
	 * with the sibling plot classes).
	 * @param parent - the parent Activity.
	 * @param container - the container View for this section.
	 */
	public void onCreate(Bundle savedInstanceState, Activity parent, View container) {

		this.activity = parent;

		bar = (LinearLayout)container.findViewById(R.id.barFuelSplit);
		barWasteOil = container.findViewById(R.id.barWasteOilFuel);
		barPumpDiesel = container.findViewById(R.id.barPumpDiesel);
		label = (TextView)container.findViewById(R.id.labelFuelSplit);
		labelEmpty = (TextView)container.findViewById(R.id.labelFuelSplitEmpty);

		units = new Units(Settings.KEY_UNITS);

		setFontSizes();
		update();
	}

	/**
	 * DESCRIPTION:
	 * Recalculates the split for the currently selected date range and redraws.
	 */
	private void update() {

		PlotDateRange range = new PlotDateRange(activity,Settings.KEY_PLOT_DATE_RANGE);
		FuelTypeTotals totals = new FuelTypeTotals(PlotActivity.records,range);

		if (totals.isEmpty()) {
			bar.setVisibility(View.GONE);
			label.setVisibility(View.GONE);
			labelEmpty.setVisibility(View.VISIBLE);
			return;
		}

		bar.setVisibility(View.VISIBLE);
		label.setVisibility(View.VISIBLE);
		labelEmpty.setVisibility(View.GONE);

		// A segment for a fuel type with no volume gets a weight of 0, and the
		// segments are declared with a width of 0dp, so an all-one-type range
		// renders as a single solid bar with no special casing.
		setWeight(barWasteOil,totals.getWasteOilFraction() * 100f);
		setWeight(barPumpDiesel,totals.getPumpDieselFraction() * 100f);

		String volume = units.getLiquidVolumeLabelLowerCase();
		String format = activity.getString(R.string.fuel_split_summary);
		label.setText(String.format(App.getLocale(),format,
				totals.getWasteOilPercent(), totals.getWasteOilGallons(), volume,
				totals.getPumpDieselPercent(),  totals.getPumpDieselGallons(),  volume));
	}

	/**
	 * DESCRIPTION:
	 * Sets the layout weight of one segment of the stacked bar.
	 * @param view - the segment View.
	 * @param weight - the weight to apply (0 - 100).
	 */
	private void setWeight(View view, float weight) {
		LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)view.getLayoutParams();
		params.weight = weight;
		view.setLayoutParams(params);
	}

	/**
	 * DESCRIPTION:
	 * Sets label font sizes to reflect the plot font size preference.
	 */
	private void setFontSizes() {
		PlotFontSize size = new PlotFontSize(activity,Settings.KEY_PLOT_FONT_SIZE);
		label.setTextSize(size.getSizeDp());
		labelEmpty.setTextSize(size.getSizeDp());
	}

	/**
	 * DESCRIPTION:
	 * Called when one or more shared preferences have changed.
	 * @see android.content.SharedPreferences.OnSharedPreferenceChangeListener#onSharedPreferenceChanged(android.content.SharedPreferences, java.lang.String)
	 */
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

		if (key.equals(Settings.KEY_PLOT_DATE_RANGE)) {
			update();
		}

		if (key.equals(Settings.KEY_PLOT_FONT_SIZE)) {
			setFontSizes();
			update();
		}

		if (key.equals(Settings.KEY_UNITS)) {
			units = new Units(Settings.KEY_UNITS);
			update();
		}
	}

}
