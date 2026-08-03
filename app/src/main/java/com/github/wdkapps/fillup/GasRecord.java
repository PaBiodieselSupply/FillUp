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

import java.io.Serializable;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

/**
 * DESCRIPTION:
 * A gasoline log entry to document the details of a gasoline purchase. 
 * It records: purchase date, odometer reading, amount of fuel purchased, etc.
 * Needs to be Serializable in order to pass between Activity instances via 
 * an Intent instance.
 */
/**
 * DESCRIPTION:
 *
 */
public class GasRecord implements Serializable {

	private static final long serialVersionUID = -2409546847119346248L;
	
	// define maximum values (for display reasons)
	public static final int MAX_ODOMETER = 9999999;
	public static final float MAX_GALLONS = 9999.999f;
	public static final double MAX_COST = 999999.999d;
	public static final double MAX_PRICE = 999999.999d;
	
    /// get a locale specific date formatter 
    private static final java.text.DateFormat dateFormatter = 
    		android.text.format.DateFormat.getDateFormat(App.getContext());
    
    /// get a locale specific time formatter 
    private static final java.text.DateFormat timeFormatter = 
    		android.text.format.DateFormat.getTimeFormat(App.getContext());
    
    /// get non-locale specific date/time formatters for internal CSV use
    private static final SimpleDateFormat csvDateFormatter = 
    		new SimpleDateFormat("MM/dd/yyyy",Locale.US);
    private static final SimpleDateFormat csvDateTimeFormatter = 
    		new SimpleDateFormat("MM/dd/yyyy HH:mm",Locale.US);
    
    /// record id for database use (primary key)
    private Integer id;
    
    /// id of the vehicle this record corresponds to (foreign key)
    private Integer vid;
    
	/// the date gasoline was purchased 
    private Date date;
	
    /// odometer reading at the time of purchase 
    private Integer odometer;
    
    /// gasoline price per gallon 
    private Double price;
    
    /// amount of gasoline purchased 
    private Float gallons;
    
    /// the total cost of the gasoline purchased
    private Double cost;
    
    /// textual notes about the purchase 
    private String notes;
    
    /// flag to indicate whether gas tank was full after purchase 
    private Boolean fulltank;
    
    /// flag to indicate whether the calculation for this record is hidden
    private Boolean hidden;

    /// the type of fuel added (required, never null - see getFuelType())
    private FuelType fuelType;

    ///  gas mileage calculation (if the tank was full, null otherwise)
    private MileageCalculation calc;
    
    /// array of record attributes included in hash code calculations
    private Object[] hash;
    
	/**
	 * DESCRIPTION:
	 * Constructs a 'blank' instance of GasRecord.
	 */
	public GasRecord() {
		id = null;
		vid = null;
        date = new Date();
    	gallons = Float.valueOf(0.0f);
    	odometer = Integer.valueOf(0);
    	cost = Double.valueOf(0.0d);
    	price = Double.valueOf(0.0d);
    	notes = "";
        fulltank = false;
        hidden = false;
        fuelType = FuelType.DIESEL;
        calc = null;
	}
	
	/**
	 * DESCRIPTION:
	 * Constructs a copy of an existing GasRecord.
	 * @param that - the existing GasRecord instance to copy.
	 */
	public GasRecord(GasRecord that) {
		this.id = that.id;
		this.vid = that.vid;
		this.date = new Date();
		this.date.setTime(that.date.getTime());
		this.gallons = Float.valueOf(that.gallons);
		this.odometer = Integer.valueOf(that.odometer);
		this.cost = Double.valueOf(that.cost);
		this.price = Double.valueOf(that.price);
		this.notes = new String(that.notes);
		this.fulltank = Boolean.valueOf(that.fulltank);
		this.hidden = Boolean.valueOf(that.hidden);
		// NOTE: this copy is load-bearing. GasRecordList.calculateMileage() and
		// MileageEstimateDialog both copy records; omitting it silently reverts
		// the fuel type to null on those paths.
		this.fuelType = that.getFuelType();
		this.calc = null;
	}
	
	/**
	 * DESCRIPTION:
	 * Constructs an GasRecord instance for a specific vehicle.
	 * @param vehicle - the Vehicle that the record pertains to.
	 */
	public GasRecord(Vehicle vehicle) {
		this();
		vid = vehicle.getID();
	}
	
	/**
	 * DESCRIPTION:
	 * Constructs an instance of GasRecord reflecting values specified
	 * by an ASCII comma-separated-values (CSV) String.<p>
	 * 
	 * NOTES:<p>
	 * <ol>
	 * <li>This method needs to be backwards compatible with CSV
	 * data created with previous versions of the application database.
	 *     <ul>
	 *     <li>formatting prior to database version 5 was:<br>
	 *     <pre>date,odometer,gallons,fulltank,hidden,[calculation]</pre>
	 *     <li>formatting for database version 5 is:<br>
	 *     <pre>date,odometer,gallons,fulltank,hidden,cost,notes,[calculation]</pre>
	 *     <li>formatting for database version 6 is:<br>
	 *     <pre>date,odometer,gallons,fulltank,hidden,cost,notes,calculation,fueltype</pre>
	 *     The calculation field may be empty, but the separator before it is
	 *     always written, so a version 6 row is <b>always</b> exactly 9 fields.
	 *     </ul>
	 * <li>Calculation values are for user information only in the CSV. They
	 * are ignored in this constructor and re-calculated later if necessary.
	 * <li>Because the fuel type field is last and is never empty, and because
	 * the calculation separator is always written, no field count is shared
	 * between the version 6 format and any earlier format. The format can
	 * therefore be identified purely by counting fields, with no inspection of
	 * the field contents. A row with no fuel type column defaults to
	 * {@link FuelType#DIESEL}, as required for backward compatibility.
	 * </ol>
	 *
	 * @param csv - the ASCI CSV String that specified the record's values.
	 * @throws ParseException if parse of CSV fails.
	 * @throws NumberFormatException if CSV contains invalid numeric values.
	 */
	public GasRecord(String csv) throws ParseException, NumberFormatException {
		this();

		// NOTE: the limit of -1 is required. String.split(regex) with the default
		// limit discards trailing empty fields, so a version 5 record with empty
		// notes and no calculation split into 6 fields instead of 7, was parsed as
		// a pre-version-5 record, and had its cost silently discarded on import.
		String[] values = csv.split(",",-1);

		switch(values.length){
		case 9:  // db_version=6
			setCsvDateTime(values[0]);
			setOdometer(values[1]);
			setGallons(values[2]);
			setFullTank(values[3]);
			setHiddenCalculation(values[4]);
			setCost(values[5]);
			setNotes(values[6]);
			// values[7] is the calculation: informational only, recalculated later
			setCsvFuelType(values[8]);
			calculatePrice();
			break;

		case 8:  // db_version=5 with calculation
		case 7:  // db_version=5
			setCsvDateTime(values[0]);
			setOdometer(values[1]);
			setGallons(values[2]);
			setFullTank(values[3]);
			setHiddenCalculation(values[4]);
			setCost(values[5]);
			setNotes(values[6]);
			setFuelType(FuelType.DIESEL);  // no fuel type column - default
			calculatePrice();
			break;

		case 6:  // db_version<5 with calculation
		case 5:  // db_version<5
			setCsvDateTime(values[0]);
			setOdometer(values[1]);
			setGallons(values[2]);
			setFullTank(values[3]);
			setHiddenCalculation(values[4]);
			setCost(0d);
			setNotes("");
			setFuelType(FuelType.DIESEL);  // no fuel type column - default
			calculatePrice();
			break;

		default:
			throw new ParseException("Invalid CSV length",values.length);
		}
	}

	/**
	 * DESCRIPTION:
	 * Sets the fuel type attribute from a CSV field value.<p>
	 *
	 * A blank value is accepted and treated as Pump Diesel - users edit these
	 * files in spreadsheets, and an emptied cell is an obvious and harmless
	 * default. A non-blank unrecognized value is an error, because it almost
	 * always means the columns are misaligned; importing it as Pump Diesel would
	 * write incorrect data into the log without telling anyone. GasLog.importData()
	 * wraps the whole file in a transaction and reports the offending line number,
	 * so the user gets a precise pointer instead of silent corruption.
	 *
	 * @param token - the CSV field value.
	 * @throws ParseException if the value is non-blank and unrecognized.
	 */
	private void setCsvFuelType(String token) throws ParseException {
		FuelType value = FuelType.fromToken(token);
		if (value == null) {
			throw new ParseException("Invalid fuel type: " + token, 8);
		}
		this.fuelType = value;
	}
	
	/**
	 * DESCRIPTION:
	 * Calculates price based on the current values for cost and gallons.
 	 * @throws NumberFormatException if the calculated value is not a valid price value.
	 */
	public void calculatePrice() {
		Double value = 0d;
		if (gallons != 0) {
			value = (double)(cost/gallons);
		}
		setPrice(value.toString());
	}
	
	/**
	 * DESCRIPTION:
	 * Calculates gallons based on the current values for cost and price.
	 * @throws NumberFormatException if the calculated value is not a valid gallons value.
	 */
	public void calculateGallons() {
		Float value = 0f;
		if (price != 0) {
			value = (float)(cost/price);
		}
		setGallons(value.toString());
	}

	/**
	 * DESCRIPTION:
	 * Calculates cost based on the current values for gallons and price.
	 * @throws NumberFormatException if the calculated value is not a valid cost value.
	 */
	public void calculateCost() {
		Double value = price * gallons;
		setCost(value.toString());
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
	 * Getter method for the vehicle id attribute.
	 * @return Integer - the vehicle id value.
	 */
	public Integer getVehicleID() {
		return vid;
	}
	
	/**
	 * DESCRIPTION:
	 * Setter method for the vehicle id attribute.
	 * @param vid - the Integer vehicle id value.
	 */
	public void setVehicleID(Integer vid) {
		this.vid = vid;
	}
	
	/**
	 * DESCRIPTION:
	 * Getter method for the date attribute.
	 * @return Date - the date value
	 */
	public Date getDate() {
		return date;
	}
	
	/**
	 * DESCRIPTION:
	 * Getter method for the date attribute as a String value.
	 * @return String - the date value (MM/dd/yyyy).
	 */
	public String getDateString() {
		return dateFormatter.format(date);
	}
	
	/**
	 * DESCRIPTION:
	 * Getter method for the date attribute as a date/time String value.
	 * @return String - the date/time value.
	 */
	public String getDateTimeString() {
		StringBuffer sb = new StringBuffer();
		sb.append(dateFormatter.format(date));
		sb.append(" ");
		sb.append(timeFormatter.format(date));
		return sb.toString();
	}
	
	/**
	 * DESCRIPTION:
	 * Getter method for the date attribute as a String value.
	 * @return String - the date/time value (MM/dd/yyyy HH:mm).
	 */
	private String getCsvDateTimeString() {
		return csvDateTimeFormatter.format(date);
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the date attribute.
	 * @param date - the Date value.
	 */
	public void setDate(Date date) {
		this.date = date;
	}
	
	/**
	 * DESCRIPTION:
	 * Setter method for the date attribute.
	 * @param date - the date/time as a String value.
	 * @throws ParseException if the String is not a valid date/time or date.
	 */
	private void setCsvDateTime(String date) throws ParseException {
		try {
			// assume date & time specified in string
			this.date = csvDateTimeFormatter.parse(date);
		} catch (ParseException e) {
			//...but also allow just date 
			this.date = csvDateFormatter.parse(date);
		}
	}
	
	/**
	 * DESCRIPTION:
	 * Getter method for the odometer attribute.
	 * @return Integer - the odometer value.
	 */
	public Integer getOdometer() {
		return odometer;
	}
	
	/**
	 * DESCRIPTION:
	 * Getter method for the odometer attribute as a String value.
	 * @return String - the odometer value. 
	 */
	public String getOdometerString() {
		return odometer.toString();
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the odometer attribute.
	 * @param odometer - the odometer value as an Integer.
	 */
	public void setOdometer(Integer odometer) {
		this.odometer = odometer;
	}
	
	/**
	 * DESCRIPTION:
	 * Setter method for the odometer attribute as a String value.
	 * @param odometer - the odometer String value.
	 * @throws NumberFormatException if the String is not a valid odometer value.
	 */
	public void setOdometer(String odometer) throws NumberFormatException {
		Integer value = Integer.valueOf(odometer);
		if ((value < 0) || (value > MAX_ODOMETER)) {
			throw new NumberFormatException("Value out of range.");
		}
		setOdometer(value);
	}

	/**
	 * DESCRIPTION:
	 * Getter method for the gallons attribute.
	 * @return Float - the gallons value.
	 */
	public Float getGallons() {
		return gallons;
	}
	
	/**
	 * DESCRIPTION:
	 * Getter method for the gallons attribute as a String value.
	 * @return String - the gallons value.
	 */
	public String getGallonsString() {
		return String.format(App.getLocale(),"%.3f",gallons);
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the gallons attribute.
	 * @param gallons - the gallons value as a Float.
	 */
	public void setGallons(Float gallons) {
		this.gallons = gallons;
	}
	
	/**
	 * DESCRIPTION:
	 * Setter method for the gallons attribute.
	 * @param gallons - the gallons attribute as a String value.
	 * @throws NumberFormatException if the String is not a valid gallons value.
	 */
	public void setGallons(String gallons) throws NumberFormatException {
		this.gallons = 0f;
		Float value = Float.valueOf(gallons.replace(',','.'));
		if ((value <= 0) || (value > MAX_GALLONS)) {
			throw new NumberFormatException("Value out of range.");
		}
		setGallons(value);
	}

	/**
	 * DESCRIPTION:
	 * Getter method for the cost attribute.
	 * @return Double - the cost value.
	 */
	public Double getCost() {
		return cost;
	}
	
	/**
	 * DESCRIPTION:
	 * Getter method for the cost attribute as a String value.
	 * @return String - the cost value.
	 */
	public String getCostString() {
		return CurrencyManager.getInstance().getNumericFormatter().format(cost);
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the cost attribute.
	 * @param cost - the cost value as a Double.
	 */
	public void setCost(Double cost) {
		this.cost = cost;
	}
	
	/**
	 * DESCRIPTION:
	 * Setter method for the cost attribute.
	 * @param cost - the cost attribute as a String value.
	 * @throws NumberFormatException if the String is not a valid cost value.
	 */
	public void setCost(String cost) throws NumberFormatException {
		this.cost = 0d;
		Double value = Double.valueOf(cost.replace(',','.'));
		if ((value < 0) || (value > MAX_COST)) {
			throw new NumberFormatException("Value out of range.");
		}
		setCost(value);
	}

	/**
	 * DESCRIPTION:
	 * Getter method for the calculated price per gallon value.
	 * @return Double - the cost per gallon value.
	 */
	public Double getPrice() {
		return price;
	}
	
	/**
	 * DESCRIPTION:
	 * Getter method for the price per gallon attribute as a String value.
	 * @return String - the price value.
	 */
	public String getPriceString() {
		return CurrencyManager.getInstance().getNumericFractionalFormatter().format(price);
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the price per gallon attribute.
	 * <p>
	 * NOTE: 
	 * Price is not a persistent attribute. It is a calculated  
	 * ratio of cost and gallons. This setter method is provided
	 * to assist data entry.
	 * </p>
	 * @param price - the price attribute as a Double.
	 */
	private void setPrice(Double price) {
		this.price = price;
	}
	
	/**
	 * DESCRIPTION:
	 * Setter method for the price per gallon attribute.
	 * <p>
	 * NOTE: 
	 * Price is not a persistent attribute. It is a calculated  
	 * ratio of cost and gallons. This setter method is provided
	 * to assist data entry.
	 * </p>
	 * @param price - the price attribute as a String value.
	 * @throws NumberFormatException if the String is not a valid price value.
	 */
	public void setPrice(String price) {
		this.price = 0d;
		Double value = Double.valueOf(price.replace(',','.'));
		if ((value < 0) || (value > MAX_PRICE)) {
			throw new NumberFormatException("Value out of range.");
		}
		setPrice(value);
	}
	
	/**
	 * DESCRIPTION:
	 * Getter method for the notes attribute.
	 * @return String - the notes attribute value.
	 */
	public String getNotes() {
		return this.notes;
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the notes attribute.
	 * @param notes - the notes value as a String.
	 */
	public void setNotes(String notes) {
		if (notes == null) notes = "";
		this.notes = notes;
	}
	
	/**
	 * DESCRIPTION:
	 * Getter method for the full tank attribute. 
	 * @return Boolean indicating whether the tank was full after purchase (true=full)
	 */
	public Boolean isFullTank() {
		return fulltank;
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the full tank attribute.
	 * @param fulltank - the Boolean full tank value.
	 */
	public void setFullTank(Boolean fulltank) {
		this.fulltank = fulltank;
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the full tank attribute. 
	 * @param fulltank - the full tank attribute as a String value ("true","false")
	 */
	public void setFullTank(String fulltank) {
		this.fulltank = Boolean.parseBoolean(fulltank);
	}

	/**
	 * DESCRIPTION:
	 * Getter method for the calculation hidden attribute. 
	 * @return Boolean indicating whether the calculation is hidden (true=hidden)
	 */
	public Boolean isCalculationHidden() {
		return hidden;
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the calculation hidden attribute. 
	 * @param hidden - the Boolean hidden value.
	 */
	public void setHiddenCalculation(Boolean hidden) {
		this.hidden = hidden;
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the calculation hidden attribute. 
	 * @param hidden - the hidden attribute as a String value ("true","false")
	 */
	public void setHiddenCalculation(String hidden) {
		this.hidden = Boolean.parseBoolean(hidden);
	}
	
	/**
	 * DESCRIPTION:
	 * Getter method for the fuel type attribute.<p>
	 *
	 * NOTE: the null guard covers a GasRecord deserialized from a Bundle written
	 * by an older version of the application (for example an in-flight
	 * onSaveInstanceState across an app upgrade). Defaulting to DIESEL matches
	 * the database migration, which backfills every pre-existing record the same way.
	 *
	 * @return the FuelType (never null).
	 */
	public FuelType getFuelType() {
		if (fuelType == null) fuelType = FuelType.DIESEL;
		return fuelType;
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the fuel type attribute.
	 * @param fuelType - the FuelType (null is treated as DIESEL).
	 */
	public void setFuelType(FuelType fuelType) {
		this.fuelType = (fuelType == null) ? FuelType.DIESEL : fuelType;
	}

	/**
	 * DESCRIPTION:
	 * Setter method for the fuel type attribute, from a persisted token String.
	 * @param token - the token String ("diesel" or "wvo").
	 * @throws IllegalArgumentException if the token is non-blank and unrecognized.
	 */
	public void setFuelType(String token) throws IllegalArgumentException {
		FuelType value = FuelType.fromToken(token);
		if (value == null) {
			throw new IllegalArgumentException("invalid fuel type: " + token);
		}
		this.fuelType = value;
	}

	/**
	 * DESCRIPTION:
	 * Getter method for the fuel type attribute, as a localized display label.
	 * @return the fuel type label String.
	 */
	public String getFuelTypeString() {
		return getFuelType().getLabel();
	}

	/**
	 * DESCRIPTION:
	 * Getter method for the mileage calculation attribute.
	 * @return the MileageCalculation instance (null=no calculation).
	 */
	public MileageCalculation getCalculation() {
		return calc;
	}
	
	/**
	 * DESCRIPTION:
	 * Setter method for the mileage calculation attribute.
	 * @param calc - the MileageCalculation instance.
	 */
	public void setCalculation(MileageCalculation calc) {
		this.calc = calc;
	}
	
	/**
	 * DESCRIPTION:
	 * Indicates whether the record has a mileage calculation.
	 * @return true if mileage calculation is available.
	 */
	public boolean hasCalculation() {
		return (calc != null);
	}

	/**
	 * DESCRIPTION:
	 * Returns an ASCII CSV String representation of the record.<p>
	 *
	 * The format is:
	 * <pre>date,odometer,gallons,fulltank,hidden,cost,notes,calculation,fueltype</pre>
	 *
	 * NOTE: unlike earlier versions, the separator before the calculation field is
	 * written even when there is no calculation, so the output is always exactly
	 * 9 fields. That, combined with the fuel type field never being empty, is what
	 * lets the parsing constructor identify the format by field count alone.
	 *
	 * @return String reflecting record attribute values.
	 */
	public String toStringCSV() {

		String mileage = hasCalculation() ? calc.getMileageString().replace(',','.') : "";

		return  getCsvDateTimeString() + "," +
				getOdometerString() + "," +
				getGallonsString().replace(',','.') + "," +
				isFullTank() + "," +
				isCalculationHidden() + "," +
				getCostString().replace(',','.') + "," +
				getNotes().replace(',',' ').replace('\n',' ') + "," +
				mileage + "," +
				getFuelType().getToken();
	}
	
	/**
	 * DESCRIPTION:
	 * Returns a String representation of the record for debug/logging purposes.
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "GasRecord [id=" + id + 
				", vid=" + vid + 
				", date=" + getDateString() +
				", odometer=" + odometer + 
				", gallons=" + gallons +
				", fulltank=" + fulltank + 
				", hidden=" + hidden +
				", cost=" + cost +
				", notes=" + notes +
				", fuelType=" + fuelType +
				", calc=" + calc +
				", price=" + price +
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
		// Changing only one of them yields a silently incorrect equals().
		if (hash == null) {
			hash = new Object[11];
		}

		// populate the array
		hash[0] = id;
		hash[1] = vid;
        hash[2] = date;
    	hash[3] = gallons;
    	hash[4] = odometer;
    	hash[5] = cost;
    	hash[6] = notes;
        hash[7] = fulltank;
        hash[8] = hidden;
        hash[9] = price;
        hash[10] = getFuelType();
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
	 * @return if the specified object is equal to this record; false otherwise.
	 */
	@Override
	public boolean equals(Object obj) {
		if (obj == null)
			return false;
		if (obj == this)
			return true;
		if (!(obj instanceof GasRecord))
			return false;

		GasRecord that = (GasRecord)obj;
		return Arrays.equals(this.getHashArray(),that.getHashArray());
	}
}