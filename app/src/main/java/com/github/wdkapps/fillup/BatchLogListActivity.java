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
import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;

/**
 * DESCRIPTION:
 * Displays the log of centrifuge batches - each batch of fuel the user has
 * processed from waste oil - and provides a user interface to add, edit and
 * delete batch records.<p>
 *
 * NOTE: batch records are <b>global</b>. Unlike the fuel log, this screen is not
 * scoped to a vehicle and does not receive one via Intent. See the class comment
 * on {@link BatchRecord} for the reasoning.
 */
public class BatchLogListActivity
extends Activity
implements ConfirmationDialog.Listener,
	OnItemClickListener,
	OnSharedPreferenceChangeListener
{

	/// request codes for Activities started by this Activity
	private static final int ADD_BATCH_REQUEST = 1;
	private static final int EDIT_ROW_REQUEST = 2;

	/// dialog id
	protected static final int DIALOG_CONFIRM_DELETE_ID = 1;

	/// the gasoline log (which also stores batch records)
	private GasLog gaslog;

	/// the batch records
	private List<BatchRecord> records;

	/// the currently selected row from the list of records
	private int selectedRow;

	/// the Android ListView for display of batch records
	private ListView listView;

	/// an adapter used to format and display each batch record
	private BatchLogListAdapter adapter;

	/**
	 * DESCRIPTION:
	 * Called when the activity is starting.
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_batch_log_list);

		// initialize attributes
		gaslog = GasLog.getInstance();
		records = gaslog.readAllBatchRecords();
		listView = (ListView)findViewById(R.id.batch_log_list);
		adapter = new BatchLogListAdapter(this,records);

		// configure ListView to use our adapter
		listView.setAdapter(adapter);

		// configure ListView to use our context menu when a record is clicked
		registerForContextMenu(listView);
		listView.setLongClickable(false);
		listView.setOnItemClickListener(this);

		// scroll ListView to last (most recent) record
		if (listView.getCount() > 0) {
			listView.setSelection(listView.getCount()-1);
		}

		// set column header labels to reflect current unit preference
		updateColumnHeaderLabels();

		// setup to be notified when preferences change
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.registerOnSharedPreferenceChangeListener(this);
	}

	/**
	 * DESCRIPTION:
	 * Initialize the Activity's standard options menu.
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_batch_log_list, menu);
		return true;
	}

	/**
	 * DESCRIPTION:
	 * Called when an item in the options menu is selected.
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {

		case R.id.itemAddBatch:
			addBatch(null);
			return true;

		case R.id.itemSettings:
			Intent intent = new Intent(this,Settings.class);
			startActivity(intent);
			return true;

		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * DESCRIPTION:
	 * Called every time the context menu is about to be shown.
	 * @see android.app.Activity#onCreateContextMenu(android.view.ContextMenu, android.view.View, android.view.ContextMenu.ContextMenuInfo)
	 */
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu,view,menuInfo);

		getMenuInflater().inflate(R.menu.context_batch_log_list, menu);

		// get index of currently selected row
		AdapterContextMenuInfo info = (AdapterContextMenuInfo)menuInfo;
		selectedRow = (int)info.id;
	}

	/**
	 * DESCRIPTION:
	 * Called when a record in the list is clicked.
	 * @see android.widget.AdapterView.OnItemClickListener#onItemClick(android.widget.AdapterView, android.view.View, int, long)
	 */
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		// display the context menu
		parent.showContextMenuForChild(view);
	}

	/**
	 * DESCRIPTION:
	 * Called when an item in a context menu is selected.
	 * @see android.app.Activity#onContextItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onContextItemSelected(MenuItem item) {

		// get index of currently selected row
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		selectedRow = (int)info.id;

		switch (item.getItemId()) {

		case R.id.itemEdit:
			editRow();
			return true;

		case R.id.itemDelete:
			showDialog(DIALOG_CONFIRM_DELETE_ID);
			return true;

		default:
			return super.onContextItemSelected(item);
		}
	}

	/**
	 * DESCRIPTION:
	 * Called when the user requests that a batch record be added to the log.
	 * Starts a new Activity to allow the user to enter the data.
	 * @param view - the View that activated this method (button click, etc).
	 */
	protected void addBatch(View view) {
		Intent intent = new Intent(this, BatchRecordActivity.class);
		intent.putExtra(BatchRecordActivity.RECORD, new BatchRecord());
		startActivityForResult(intent,ADD_BATCH_REQUEST);
	}

	/**
	 * DESCRIPTION:
	 * Called when the user finishes entering data for a new batch record.
	 * @param record - the BatchRecord data entered by the user.
	 */
	protected void onAddBatchResult(BatchRecord record) {

		if (!gaslog.createBatchRecord(record)) {
			Utilities.toast(this,getString(R.string.toast_error_saving_data));
			return;
		}

		Utilities.toast(this,getString(R.string.toast_data_saved));

		// re-read so that the list stays sorted by date
		records.clear();
		records.addAll(gaslog.readAllBatchRecords());
		adapter.notifyDataSetChanged();

		// scroll the new record into view
		int position = records.indexOf(record);
		listView.setSelection((position < 0) ? records.size()-1 : position);
	}

	/**
	 * DESCRIPTION:
	 * Called when the user requests to edit a specific batch record.
	 * Starts a new Activity to allow the user to edit the data.
	 */
	protected void editRow() {
		Intent intent = new Intent(this, BatchRecordActivity.class);
		BatchRecord record = records.get(selectedRow);
		intent.putExtra(BatchRecordActivity.RECORD, record);
		startActivityForResult(intent,EDIT_ROW_REQUEST);
	}

	/**
	 * DESCRIPTION:
	 * Called when the user finishes editing data for a batch record.
	 * @param record - the BatchRecord data edited by the user.
	 */
	protected void onEditRowResult(BatchRecord record) {
		if (!gaslog.updateBatchRecord(record)) {
			Utilities.toast(this,getString(R.string.toast_edit_failed));
			return;
		}

		// re-read so that the list stays sorted by date
		records.clear();
		records.addAll(gaslog.readAllBatchRecords());
		adapter.notifyDataSetChanged();
	}

	/**
	 * DESCRIPTION:
	 * Called when the user has selected a batch record to delete from the log
	 * and has confirmed deletion.
	 */
	protected void deleteRow() {

		BatchRecord record = records.get(selectedRow);

		if (gaslog.deleteBatchRecord(record)) {
			records.remove(selectedRow);
			adapter.notifyDataSetChanged();
		} else {
			Utilities.toast(this,getString(R.string.toast_delete_failed));
		}
	}

	/**
	 * DESCRIPTION:
	 * Updates the column header labels to reflect the preferred units for
	 * liquid volume.
	 */
	protected void updateColumnHeaderLabels() {
		Units units = new Units(Settings.KEY_UNITS);
		TextView header = (TextView)findViewById(R.id.headerBatchYield);
		header.setText(units.getLiquidVolumeLabel());
	}

	/**
	 * DESCRIPTION:
	 * Called when an Activity started by this Activity returns a result.
	 * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		if (resultCode != RESULT_OK) {
			Utilities.toast(this,getString(R.string.toast_canceled));
			return;
		}

		BatchRecord record = (BatchRecord)data.getSerializableExtra(BatchRecordActivity.RECORD);
		if (record == null) return;

		switch (requestCode) {
		case ADD_BATCH_REQUEST:
			onAddBatchResult(record);
			break;
		case EDIT_ROW_REQUEST:
			onEditRowResult(record);
			break;
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
		case DIALOG_CONFIRM_DELETE_ID:
			dialog = ConfirmationDialog.create(
					this,this,id,
					getString(R.string.title_confirm_batch_delete_dialog),
					getString(R.string.message_confirm_batch_delete_dialog));
			break;

		default:
			dialog = null;
		}

		return dialog;
	}

	/**
	 * DESCRIPTION:
	 * Called when a confirmation dialog gets a response from the user.
	 * @see com.github.wdkapps.fillup.ConfirmationDialog.Listener#onConfirmationDialogResponse(int, boolean)
	 */
	@Override
	public void onConfirmationDialogResponse(int id, boolean confirmed) {

		removeDialog(id);

		if (!confirmed) return;

		switch (id) {
		case DIALOG_CONFIRM_DELETE_ID:
			deleteRow();
			break;
		default:
			Utilities.toast(this,"Invalid dialog id.");
		}
	}

	/**
	 * DESCRIPTION:
	 * Called when one or more shared preferences have changed.
	 * @see android.content.SharedPreferences.OnSharedPreferenceChangeListener#onSharedPreferenceChanged(android.content.SharedPreferences, java.lang.String)
	 */
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (key.equals(Settings.KEY_UNITS)) {
			updateColumnHeaderLabels();
		}
		adapter.notifyDataSetChanged();
	}

	/**
	 * DESCRIPTION:
	 * Saves the activity state before screen rotation, etc.
	 * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
	 */
	@Override
	protected void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
		savedInstanceState.putInt("selectedRow",selectedRow);
	}

	/**
	 * DESCRIPTION:
	 * Restores the activity state after screen rotation, etc.
	 * @see android.app.Activity#onRestoreInstanceState(android.os.Bundle)
	 */
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		selectedRow = savedInstanceState.getInt("selectedRow");
	}

}
