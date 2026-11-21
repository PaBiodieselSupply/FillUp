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

import android.graphics.Paint;

import com.androidplot.util.FontUtils;
import com.androidplot.util.PixelUtils;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;

/**
 * DESCRIPTION:
 * Layout adjustments shared by every chart on the plots screen.
 */
final class PlotLayout {

	private PlotLayout() {}

	/**
	 * DESCRIPTION:
	 * Reserves enough room beneath a chart's grid for the domain (x-axis) labels
	 * - the dates or month names - at their current font size.<p>
	 *
	 * NOTE: androidplot reserves a fixed 15 RAW PIXELS for these labels, which
	 * the plot classes only scaled by 1.5. It is not scaled for screen density,
	 * so on a high-density phone (a Pixel 7 is 2.6x) a 12dp label is about 37px
	 * tall and its lower half was cut off by the bottom of the chart.<p>
	 *
	 * androidplot draws each label with its baseline at
	 * <code>grid bottom + tick extension + vertical offset + font height</code>,
	 * and the descenders hang below that baseline, so the space reserved must
	 * cover all of it. Measured from the label paints themselves, so it follows
	 * the plot font size setting and any screen density.<p>
	 *
	 * Call after the label font sizes have been set.
	 *
	 * @param plot - the chart to adjust.
	 */
	static void fitDomainLabels(XYPlot plot) {

		XYGraphWidget graph = plot.getGraphWidget();
		Paint label = graph.getDomainLabelPaint();
		Paint origin = graph.getDomainOriginLabelPaint();

		float height = Math.max(FontUtils.getFontHeight(label), FontUtils.getFontHeight(origin));
		float descent = Math.max(label.getFontMetrics().descent, origin.getFontMetrics().descent);

		float needed = graph.getDomainLabelTickExtension()
				+ graph.getDomainLabelVerticalOffset()
				+ height
				+ descent
				+ PixelUtils.dpToPix(4);   // breathing room above the card's edge

		graph.setDomainLabelWidth(needed);
	}

}
