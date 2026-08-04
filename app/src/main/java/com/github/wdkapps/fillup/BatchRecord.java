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

import java.io.Serializable;
import java.util.Arrays;
import java.util.Date;

/**
 * DESCRIPTION:
 * A record of one batch of fuel processed through the user's waste oil
 * centrifuge system.<p>
 *
 * NOTES:<p>
 * <ol>
 * <li>Batch records are <b>global</b> - they are deliberately not associated
 * with a Vehicle. A batch of centrifuged fuel exists before it is poured into
 * anything, and may be split across several vehicles, a generator or a storage
 * drum. Scoping batches per-vehicle would force users with more than one
 * vehicle either to enter each batch twice (doubling their apparent
 * production) or to pick one arbitrarily. It would also mean that deleting a
 * vehicle - which cascades into deleting its gas records - destroyed years of
 * unrelated production history.
 * <li>Raw oil processed is optional. A value of 0 means "not recorded", which
 * mirrors how the cost attribute of {@link GasRecord} already behaves.
 * <li>Yield percentage is derived, never stored.
 * <li>Volumes are formatted to one decimal place rather than the three used by
 * GasRecord - centrifuge batches are not measured to the thousandth.
 * </ol>
 */
public class BatchRecord implements Serializable {

	private static final long serialVersionUID = 7451903668213544219L;

	// define maximum values (for display reasons)
	public static final float MAX_GALLONS = 9999.999f;
	public static final float MAX_BLEND_PERCENT = 100.0f;
	public static final double MAX_COST = 999999.999d;

	/// get a locale specific date formatter
	private static final java.text.DateFormat dateFormatter =
			android.text.format.DateFormat.getDateFormat(App.getContext());

	/// get a locale specific time formatter
	private static final java.text.DateFormat timeFormatter =
			android.text.format.DateFormat.getTimeFormat(App.getContext());

	/// record id for database use (primary key)
	private Integer id;

	/// the date the batch was processed
	private Date date;

	/// amount of usable fuel produced by the batch
	private Float yield;

	/// amount of waste oil processed to produce the batch (0 = not recorded)
	private Float raw;

	/// percentage of gasoline or diesel blended into the batch (0 = none/not recorded)
	private Float blend;

	/// what the batch cost to produce (0 = not recorded)
	private Double cost;

	/// textual notes about the batch
	private String notes;

	/// array of record attributes included in hash code calculations
	private Object[] hash;

	/**
	 * DESCRIPTION:
	 * Constructs a 'blank' instance of BatchRecord.
	 */
	public BatchRecord() {
		id = null;
		date = new Date();
		yield = Float.valueOf(0.0f);
		raw = Float.valueOf(0.0f);
		blend = Float.valueOf(0.0f);
		cost = Double.valueOf(0.0d);
		notes = "";
	}

	/**
	 * DESCRIPTION:
	 * Constructs a copy of an existing BatchRecord.
	 * @param that - the existing BatchRecord instance to copy.
	 */
	public BatchRecord(BatchRecord that) {
		this.id = that.id;
		this.date = new Date();
		this.date.setTime(that.date.getTime());
		this.yield = Float.valueOf(that.yield);
		this.raw = Float.valueOf(that.raw);
		this.blend = Float.valueOf(that.getBlendPercent());
		this.cost = Double.valueOf(that.getCost());
		this.notes = new String(that.notes);
	}

	/**
	 * DESCRIPTION:
	 * Getter method for the id attribute.
	 * @return Integer - the id value.
	 */
	public Integer getID() {
		return id;
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the id attribute.
	 * @param id - the Integer id value.
	 */
	public void setID(Integer id) {
		this.id = id;
	}

	/**
	 * DESCRIPTION:
	 * Getter method for the date attribute.
	 * @return the Date the batch was processed.
	 */
	public Date getDate() {
		return date;
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the date attribute.
	 * @param date - the Date the batch was processed.
	 */
	public void setDate(Date date) {
		this.date = date;
	}

	/**
	 * DESCRIPTION:
	 * Getter method for the date attribute, formatted for the current locale.
	 * @return the date as a String.
	 */
	public String getDateString() {
		return dateFormatter.format(date);
	}

	/**
	 * DESCRIPTION:
	 * Getter method for the date attribute including the time of day,
	 * formatted for the current locale.
	 * @return the date and time as a String.
	 */
	public String getDateTimeString() {
		return dateFormatter.format(date) + " " + timeFormatter.format(date);
	}

	/**
	 * DESCRIPTION:
	 * Getter method for the clean fuel yield attribute.
	 * @return Float - the amount of usable fuel produced.
	 */
	public Float getYieldGallons() {
		return yield;
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the clean fuel yield attribute.
	 * @param yield - the amount of usable fuel produced.
	 */
	public void setYieldGallons(Float yield) {
		this.yield = yield;
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the clean fuel yield attribute. This attribute is
	 * required, so a blank value is rejected.
	 * @param yield - the yield as a String.
	 * @throws NumberFormatException if the String is not a valid yield value.
	 */
	public void setYieldGallons(String yield) throws NumberFormatException {
		Float value = Float.valueOf(yield.replace(',', '.'));
		if ((value <= 0f) || (value > MAX_GALLONS)) {
			throw new NumberFormatException("invalid yield value");
		}
		this.yield = value;
	}

	/**
	 * DESCRIPTION:
	 * Getter method for the clean fuel yield attribute, formatted for display.
	 * @return the yield as a String.
	 */
	public String getYieldGallonsString() {
		return String.format(App.getLocale(), "%.1f", yield);
	}

	/**
	 * DESCRIPTION:
	 * Getter method for the raw oil processed attribute.
	 * @return Float - the amount of waste oil processed (0 = not recorded).
	 */
	public Float getRawGallons() {
		return raw;
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the raw oil processed attribute.
	 * @param raw - the amount of waste oil processed.
	 */
	public void setRawGallons(Float raw) {
		this.raw = raw;
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the raw oil processed attribute. This attribute is
	 * optional - a blank value is accepted and means "not recorded".
	 * @param raw - the raw oil amount as a String (may be blank).
	 * @throws NumberFormatException if the String is not a valid volume.
	 */
	public void setRawGallons(String raw) throws NumberFormatException {

		if (raw == null || raw.trim().length() == 0) {
			this.raw = 0f;
			return;
		}

		Float value = Float.valueOf(raw.trim().replace(',', '.'));
		if ((value < 0f) || (value > MAX_GALLONS)) {
			throw new NumberFormatException("invalid raw oil value");
		}
		this.raw = value;
	}

	/**
	 * DESCRIPTION:
	 * Getter method for the raw oil processed attribute, formatted for display.
	 * @return the raw oil amount as a String.
	 */
	public String getRawGallonsString() {
		return String.format(App.getLocale(), "%.1f", raw);
	}

	/**
	 * DESCRIPTION:
	 * Indicates whether an amount of raw oil processed was recorded for this
	 * batch. It is an optional attribute.
	 * @return true if raw oil was recorded.
	 */
	public boolean hasRawGallons() {
		return (raw != null) && (raw > 0f);
	}

	/**
	 * DESCRIPTION:
	 * Getter method for the blend percentage attribute - the proportion of
	 * gasoline or diesel mixed into the batch.
	 * @return Float - the blend percentage (0 = none recorded).
	 */
	public Float getBlendPercent() {
		if (blend == null) blend = 0f;   // defensive: record from an older version
		return blend;
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the blend percentage attribute.
	 * @param blend - the blend percentage.
	 */
	public void setBlendPercent(Float blend) {
		this.blend = (blend == null) ? 0f : blend;
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the blend percentage attribute. This attribute is
	 * optional - a blank value is accepted and means "none recorded".
	 * @param blend - the blend percentage as a String (may be blank).
	 * @throws NumberFormatException if the String is not a valid percentage.
	 */
	public void setBlendPercent(String blend) throws NumberFormatException {

		if (blend == null || blend.trim().length() == 0) {
			this.blend = 0f;
			return;
		}

		Float value = Float.valueOf(blend.trim().replace(',', '.'));
		if ((value < 0f) || (value > MAX_BLEND_PERCENT)) {
			throw new NumberFormatException("invalid blend percentage");
		}
		this.blend = value;
	}

	/**
	 * DESCRIPTION:
	 * Getter method for the blend percentage, formatted for display.
	 * @return the blend percentage as a String, or "" if none recorded.
	 */
	public String getBlendPercentString() {
		if (!hasBlendPercent()) return "";
		return String.format(App.getLocale(), "%.0f%%", getBlendPercent());
	}

	/**
	 * DESCRIPTION:
	 * Indicates whether a blend percentage was recorded for this batch.
	 * @return true if a blend percentage was recorded.
	 */
	public boolean hasBlendPercent() {
		return (blend != null) && (blend > 0f);
	}

	/**
	 * DESCRIPTION:
	 * Getter method for the batch cost attribute - what the batch cost to
	 * produce, including any blend stock, consumables and power.
	 * @return Double - the batch cost (0 = not recorded).
	 */
	public Double getCost() {
		if (cost == null) cost = 0d;   // defensive: record from an older version
		return cost;
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the batch cost attribute.
	 * @param cost - the batch cost.
	 */
	public void setCost(Double cost) {
		this.cost = (cost == null) ? 0d : cost;
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the batch cost attribute. This attribute is optional -
	 * a blank value is accepted and means "not recorded".
	 * @param cost - the batch cost as a String (may be blank).
	 * @throws NumberFormatException if the String is not a valid cost.
	 */
	public void setCost(String cost) throws NumberFormatException {

		if (cost == null || cost.trim().length() == 0) {
			this.cost = 0d;
			return;
		}

		Double value = Double.valueOf(cost.trim().replace(',', '.'));
		if ((value < 0d) || (value > MAX_COST)) {
			throw new NumberFormatException("invalid batch cost");
		}
		this.cost = value;
	}

	/**
	 * DESCRIPTION:
	 * Indicates whether a production cost was recorded for this batch.
	 * @return true if a cost was recorded.
	 */
	public boolean hasCost() {
		return (cost != null) && (cost > 0d);
	}

	/**
	 * DESCRIPTION:
	 * Calculates what each unit of clean fuel in this batch cost to produce.
	 * This is a derived value - it is never stored.
	 * @return the cost per unit, or null if no cost was recorded.
	 */
	public Double getCostPerUnit() {
		if (!hasCost() || (yield == null) || (yield <= 0f)) return null;
		return getCost() / yield;
	}

	/**
	 * DESCRIPTION:
	 * Calculates the percentage of the raw oil input that became usable fuel.
	 * This is a derived value - it is never stored.
	 * @return the yield percentage, or null if raw oil was not recorded.
	 */
	public Float getYieldPercent() {
		if (!hasRawGallons()) return null;
		return (yield / raw) * 100f;
	}

	/**
	 * DESCRIPTION:
	 * Indicates whether a yield percentage can be calculated for this batch.
	 * @return true if a yield percentage is available.
	 */
	public boolean hasYieldPercent() {
		return hasRawGallons();
	}

	/**
	 * DESCRIPTION:
	 * Getter method for the derived yield percentage, formatted for display.
	 * @return the yield percentage as a String, or "" if not calculable.
	 */
	public String getYieldPercentString() {
		Float percent = getYieldPercent();
		if (percent == null) return "";
		return formatYieldPercent(percent);
	}

	/**
	 * DESCRIPTION:
	 * Formats a yield percentage for display, to two decimal places.<p>
	 *
	 * NOTE: this is static, and is the single formatting point for yield
	 * percentages anywhere in the application. The batch log list formats a
	 * stored record, while the add/edit form previews a percentage live from
	 * whatever is currently typed into the two fields - routing both through
	 * here is what stops the two displays from ever drifting apart.
	 *
	 * @param percent - the percentage value.
	 * @return the formatted percentage String.
	 */
	public static String formatYieldPercent(float percent) {
		return String.format(App.getLocale(), "%.2f%%", percent);
	}

	/**
	 * DESCRIPTION:
	 * Getter method for the notes attribute.
	 * @return the notes String.
	 */
	public String getNotes() {
		return notes;
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the notes attribute.
	 * @param notes - the notes String (null is treated as blank).
	 */
	public void setNotes(String notes) {
		this.notes = (notes == null) ? "" : notes;
	}

	/**
	 * DESCRIPTION:
	 * Returns a String representation of the record for debug/logging purposes.
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "BatchRecord [id=" + id +
				", date=" + getDateString() +
				", yield=" + yield +
				", raw=" + raw +
				", blend=" + blend +
				", cost=" + cost +
				", notes=" + notes +
				"]";
	}

	/**
	 * DESCRIPTION:
	 * Constructs an array of all attributes to be considered in
	 * hash code calculations for this record.
	 * @return an Object[] of attributes to be hashed.
	 */
	private Object[] getHashArray() {

		// create the array (only once for performance)
		// NOTE: the array size and the assignments below must be kept in step.
		if (hash == null) {
			hash = new Object[7];
		}

		// populate the array
		hash[0] = id;
		hash[1] = date;
		hash[2] = yield;
		hash[3] = raw;
		hash[4] = notes;
		hash[5] = getBlendPercent();
		hash[6] = getCost();
		return hash;
	}

	/**
	 * DESCRIPTION:
	 * Calculates an integer hash code for this record.
	 * @see java.lang.Object#hashCode()
	 * @return the integer hash code.
	 */
	@Override
	public int hashCode() {
		return Arrays.hashCode(getHashArray());
	}

	/**
	 * DESCRIPTION:
	 * Compares this instance with the specified object and indicates
	 * if they are equal.
	 * @see java.lang.Object#equals(java.lang.Object)
	 * @return true if the specified object is equal to this record; false otherwise.
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (obj == this)
			return true;
		if (!(obj instanceof BatchRecord))
			return false;

		BatchRecord that = (BatchRecord)obj;
		return Arrays.equals(this.getHashArray(), that.getHashArray());
	}

}
