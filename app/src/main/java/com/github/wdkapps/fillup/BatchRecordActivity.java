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

import java.util.Date;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

/**
 * DESCRIPTION:
 * Implements an Android Activity class to display one BatchRecord and allow the
 * user to enter/edit values. The record is passed in/out of the Activity via the
 * Android Intent mechanism.<p>
 *
 * Structured to match {@link GasRecordActivity}, but considerably simpler: there
 * is no data entry mode (so a single layout rather than three), no odometer, and
 * no tank full checkbox.
 */
public class BatchRecordActivity extends Activity
implements ConfirmationDialog.Listener {

	/// key name for the BatchRecord to pass via Intent
	public final static String RECORD = BatchRecordActivity.class.getName() + ".RECORD";

	/// request code for the date/time editing Activity
	private static final int EDIT_DATE_TIME_REQUEST = 1;

	/// dialog id
	protected static final int DIALOG_CONFIRM_YIELD_HIGH_ID = 1;

	/// the BatchRecord being edited
	private BatchRecord record;

	/// the Activity's widgets (Views)
	private EditText editTextDate;
	private EditText editTextYield;
	private EditText editTextRaw;
	private EditText editTextNotes;
	private TextView textViewYieldPercent;

	/**
	 * DESCRIPTION:
	 * Called when the activity is starting.
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (savedInstanceState == null) {
			Intent intent = getIntent();
			record = (BatchRecord)intent.getSerializableExtra(RECORD);
		} else {
			record = (BatchRecord)savedInstanceState.getSerializable("record");
		}

		if (record == null) record = new BatchRecord();

		loadForm();
	}

	/**
	 * DESCRIPTION:
	 * Loads and initializes the UI form (layout).
	 */
	private void loadForm() {

		setContentView(R.layout.activity_batch_record);

		// get view instances
		editTextDate = (EditText)findViewById(R.id.editTextDate);
		editTextYield = (EditText)findViewById(R.id.editTextYield);
		editTextRaw = (EditText)findViewById(R.id.editTextRaw);
		editTextNotes = (EditText)findViewById(R.id.editTextNotes);
		textViewYieldPercent = (TextView)findViewById(R.id.textViewYieldPercent);

		// update labels and hints to reflect current units
		Units units = new Units(Settings.KEY_UNITS);
		String volume = units.getLiquidVolumeLabelLowerCase();

		TextView label = (TextView)findViewById(R.id.textViewYield);
		label.setText(String.format(App.getLocale(),getString(R.string.batch_yield_label),volume));
		label = (TextView)findViewById(R.id.textViewRaw);
		label.setText(String.format(App.getLocale(),getString(R.string.batch_raw_label),volume));

		// disallow newline in notes field
		editTextNotes.addTextChangedListener(new TextWatcher() {
			public void onTextChanged(CharSequence s, int start, int before, int count) {}
			public void beforeTextChanged(CharSequence s, int start, int count,int after) {}
			public void afterTextChanged(Editable s) {
				for(int i = s.length(); i > 0; i--){
					if(s.subSequence(i-1, i).toString().equals("\n"))
						s.replace(i-1, i, "");
				}
			}
		});

		// copy data: record => form
		setData();

		// keep the derived yield percentage up to date as the user types
		TextWatcher yieldWatcher = new TextWatcher() {
			public void onTextChanged(CharSequence s, int start, int before, int count) {}
			public void beforeTextChanged(CharSequence s, int start, int count,int after) {}
			public void afterTextChanged(Editable s) {
				updateYieldPercent();
			}
		};
		editTextYield.addTextChangedListener(yieldWatcher);
		editTextRaw.addTextChangedListener(yieldWatcher);
	}

	/**
	 * DESCRIPTION:
	 * Initialize the Activity's standard options menu.
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return false;
	}

	/**
	 * DESCRIPTION:
	 * Set the form values based on the BatchRecord data values.
	 */
	protected void setData() {

		editTextDate.setText(record.getDateTimeString());

		// a brand new record has a yield of 0, which is not a meaningful value to
		// show in a required field - leave it blank so the hint shows instead
		if (record.getYieldGallons() > 0f) {
			editTextYield.setText(record.getYieldGallonsString());
		} else {
			editTextYield.setText("");
		}

		if (record.hasRawGallons()) {
			editTextRaw.setText(record.getRawGallonsString());
		} else {
			editTextRaw.setText("");
		}

		editTextNotes.setText(record.getNotes());

		updateYieldPercent();
	}

	/**
	 * DESCRIPTION:
	 * Get the current data values from the form after user edit, validate them,
	 * and update the BatchRecord being edited.
	 * @return boolean - indicates if form data is valid (valid=true)
	 */
	protected boolean getData() {

		Units units = new Units(Settings.KEY_UNITS);
		String volume = units.getLiquidVolumeLabelLowerCase();
		String message;

		// reset any previous errors
		editTextYield.setError(null);
		editTextRaw.setError(null);

		// clean fuel yielded (required)
		try {
			record.setYieldGallons(editTextYield.getText().toString());
		} catch (NumberFormatException e) {
			message = String.format(App.getLocale(),getString(R.string.toast_invalid_yield_value),volume);
			editTextYield.setError(message);
			editTextYield.requestFocus();
			return false;
		}

		// raw oil processed (optional - blank is valid and means "not recorded")
		try {
			record.setRawGallons(editTextRaw.getText().toString());
		} catch (NumberFormatException e) {
			message = String.format(App.getLocale(),getString(R.string.toast_invalid_raw_value),volume);
			editTextRaw.setError(message);
			editTextRaw.requestFocus();
			return false;
		}

		// notes
		record.setNotes(editTextNotes.getText().toString());

		return true;
	}

	/**
	 * DESCRIPTION:
	 * Evaluate the data and, if necessary, confirm that the user intended to enter
	 * values that are in range but seem odd.
	 * @param id - the id of the last confirmation dialog displayed (0 = starting point)
	 * @return true if all data values are acceptable/confirmed.
	 */
	protected boolean confirmData(int id) {

		switch (id) {

		case 0: // starting point
			// A yield greater than the raw oil input is physically impossible, and
			// entering the two values the wrong way round is the single most likely
			// data entry mistake on this form.
			if (record.hasRawGallons() &&
					(record.getYieldGallons() > record.getRawGallons())) {
				showDialog(DIALOG_CONFIRM_YIELD_HIGH_ID);
				return false;
			}
			break;

		case DIALOG_CONFIRM_YIELD_HIGH_ID:
			break;
		}

		return true;
	}

	/**
	 * DESCRIPTION:
	 * Updates the displayed yield percentage from the values currently in the
	 * form. The percentage is derived, and is blank until raw oil is entered.
	 */
	private void updateYieldPercent() {

		Float yield = parse(editTextYield.getText().toString());
		Float raw = parse(editTextRaw.getText().toString());

		if ((yield == null) || (raw == null) || (raw <= 0f) || (yield <= 0f)) {
			textViewYieldPercent.setText("");
			return;
		}

		// formatted by BatchRecord so that this live preview and the batch log
		// list can never display the same batch differently
		String percent = BatchRecord.formatYieldPercent((yield / raw) * 100f);
		textViewYieldPercent.setText(
				String.format(App.getLocale(),getString(R.string.batch_yield_percent_value),percent));
	}

	/**
	 * DESCRIPTION:
	 * Leniently parses a volume value entered by the user, for the purpose of
	 * updating the derived display only.
	 * @param value - the String to parse.
	 * @return the value, or null if it is not a number.
	 */
	private Float parse(String value) {
		if (value == null) return null;
		value = value.trim().replace(',','.');
		if (value.length() == 0) return null;
		try {
			return Float.valueOf(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	/**
	 * DESCRIPTION:
	 * Called when the OK button is clicked.
	 * @param view - the View that activated this method.
	 */
	public void clickedOk(View view) {
		hideSoftKeyboard();
		if (getData() && confirmData(0)) {
			returnResult(Activity.RESULT_OK);
		}
	}

	/**
	 * DESCRIPTION:
	 * Called when the CANCEL button is clicked.
	 * @param view - the View that activated this method.
	 */
	public void clickedCancel(View view) {
		hideSoftKeyboard();
		returnResult(Activity.RESULT_CANCELED);
	}

	/**
	 * DESCRIPTION:
	 * Called when the EDIT DATE button is clicked.
	 * @param view - the View that activated this method.
	 */
	public void clickedEditDate(View view) {
		Intent intent = new Intent(this, DateTimeActivity.class);
		intent.putExtra(DateTimeActivity.MILLISECONDS, record.getDate().getTime());
		startActivityForResult(intent,EDIT_DATE_TIME_REQUEST);
	}

	/**
	 * DESCRIPTION:
	 * Returns results to the caller and closes this Activity.
	 * @param resultCode - the integer result code to return to caller.
	 */
	protected void returnResult(int resultCode) {
		Intent intent = new Intent();
		intent.putExtra(RECORD, record);
		setResult(resultCode, intent);
		finish();
	}

	/**
	 * DESCRIPTION:
	 * Called when an Activity started by this Activity returns a result.
	 * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		if (resultCode != RESULT_OK) return;

		if (requestCode == EDIT_DATE_TIME_REQUEST) {
			long milliseconds = data.getLongExtra(DateTimeActivity.MILLISECONDS,
					record.getDate().getTime());
			record.setDate(new Date(milliseconds));
			editTextDate.setText(record.getDateTimeString());
		}
	}

	/**
	 * DESCRIPTION:
	 * Creates dialogs displayed by this Activity.
	 * @see android.app.Activity#onCreateDialog(int)
	 */
	@Override
	protected Dialog onCreateDialog(int id) {

		Dialog dialog;

		switch (id) {
		case DIALOG_CONFIRM_YIELD_HIGH_ID:
			dialog = ConfirmationDialog.create(
					this,this,id,
					getString(R.string.title_confirm_yield_high),
					getString(R.string.message_confirm_yield_high));
			break;

		default:
			dialog = null;
		}

		return dialog;
	}

	/**
	 * DESCRIPTION:
	 * Called when a user response has been obtained from a confirmation dialog.
	 * @see com.github.wdkapps.fillup.ConfirmationDialog.Listener#onConfirmationDialogResponse(int, boolean)
	 */
	@Override
	public void onConfirmationDialogResponse(int id, boolean confirmed) {

		removeDialog(id);

		if (confirmed && confirmData(id)) {
			returnResult(Activity.RESULT_OK);
		}
	}

	/**
	 * DESCRIPTION:
	 * Saves the activity state before screen rotation, etc.
	 * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
	 */
	@Override
	protected void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
		savedInstanceState.putSerializable("record", record);
	}

	/**
	 * DESCRIPTION:
	 * Dismisses the soft keyboard.
	 */
	private void hideSoftKeyboard() {
		InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
		if (imm != null) {
			imm.hideSoftInputFromWindow(editTextYield.getWindowToken(), 0);
		}
	}

}
