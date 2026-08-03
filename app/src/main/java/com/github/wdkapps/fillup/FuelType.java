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

import java.util.Locale;

/**
 * DESCRIPTION:
 * The type of fuel added in a gasoline record.<p>
 *
 * <ul>
 * <li>DIESEL - conventional diesel purchased at a fuel station. Has a cost.
 * <li>WVO - "Black Diesel", produced by the user from waste oil using a
 * centrifuge system. Has no purchase cost.
 * </ul>
 *
 * NOTES:<p>
 * <ol>
 * <li>This is deliberately a plain enum rather than a class modeled on
 * {@link Units} or {@link DataEntryMode}. Those read a value out of
 * SharedPreferences in their constructor - they represent a user
 * <i>preference</i>. Fuel type is a per-record persisted value, so it must
 * not touch preferences at all.
 * <li>Java enums are Serializable, so {@link GasRecord} remains passable
 * between Activities via Intent extras with no extra work.
 * <li>{@link #getToken()} is the single canonical string used for BOTH the
 * database column value and the CSV field value. Keeping one source of truth
 * is what makes the SQL default ('diesel') trivially correct.
 * </ol>
 */
public enum FuelType {

	DIESEL("diesel", R.string.fuel_type_diesel, R.color.fuel_type_diesel_fg),
	WVO   ("wvo",    R.string.fuel_type_wvo,    R.color.fuel_type_wvo_fg);

	/// the persisted value: used for the database column and the CSV field
	private final String token;

	/// resource id of the localized display label
	private final int labelResId;

	/// resource id of the color used to display this fuel type in the log list
	private final int colorResId;

	/**
	 * DESCRIPTION:
	 * Constructs an instance of FuelType.
	 * @param token - the persisted string value.
	 * @param labelResId - resource id of the display label.
	 * @param colorResId - resource id of the display color.
	 */
	private FuelType(String token, int labelResId, int colorResId) {
		this.token = token;
		this.labelResId = labelResId;
		this.colorResId = colorResId;
	}

	/**
	 * DESCRIPTION:
	 * Returns the persisted value for this fuel type. This is what is stored in
	 * the database and written to CSV - never the display label, which is
	 * localized and would break both.
	 * @return the token String ("diesel" or "wvo").
	 */
	public String getToken() {
		return token;
	}

	/**
	 * DESCRIPTION:
	 * Returns the localized label to display for this fuel type.
	 * @return the label String.
	 */
	public String getLabel() {
		return App.getContext().getString(labelResId);
	}

	/**
	 * DESCRIPTION:
	 * Returns the resource id of the color used to display this fuel type.
	 * @return the color resource id.
	 */
	public int getColorResId() {
		return colorResId;
	}

	/**
	 * DESCRIPTION:
	 * Determines whether this is user-produced fuel (Black Diesel), which has
	 * no purchase cost.
	 * @return true if this is Black Diesel.
	 */
	public boolean isBlackDiesel() {
		return (this == WVO);
	}

	/**
	 * DESCRIPTION:
	 * Converts a persisted token String to a FuelType.<p>
	 *
	 * The three return cases are deliberately distinct:
	 * <ul>
	 * <li>null or blank returns DIESEL - the column is absent (an older CSV
	 * export, or a cell a user cleared in a spreadsheet). Defaulting is the
	 * documented backward-compatible behavior.
	 * <li>a recognized token returns the matching FuelType (case insensitive,
	 * so hand-edited "Diesel" and "WVO" both work).
	 * <li>a non-blank unrecognized token returns <b>null</b>, so the caller can
	 * treat it as an error. That almost always means the CSV columns are
	 * misaligned, and silently coercing it to DIESEL would write bad data
	 * into the log.
	 * </ul>
	 *
	 * @param token - the token String to convert (may be null).
	 * @return the FuelType, or null if the token is non-blank and unrecognized.
	 */
	public static FuelType fromToken(String token) {

		if (token == null) return DIESEL;

		String value = token.trim().toLowerCase(Locale.US);
		if (value.length() == 0) return DIESEL;

		for (FuelType type : values()) {
			if (type.token.equals(value)) return type;
		}

		return null;
	}

	/**
	 * DESCRIPTION:
	 * Converts a persisted token String to a FuelType, defaulting to DIESEL for
	 * anything unrecognized. Use this when reading from the database, where a
	 * bad value should not be able to crash the log display.
	 * @param token - the token String to convert (may be null).
	 * @return the FuelType (never null).
	 */
	public static FuelType fromTokenOrDefault(String token) {
		FuelType type = fromToken(token);
		return (type == null) ? DIESEL : type;
	}

}
