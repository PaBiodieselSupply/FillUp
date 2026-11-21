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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Spinner;

import androidx.core.content.FileProvider;

/**
 * DESCRIPTION:
 * The main Activity for the application. Displays a drop down list of 
 * vehicles from the log and allows a user to add/edit/or delete vehicles, 
 * view vehicle logs, and add a gas record to the log for a selected a 
 * vehicle.  
 */
public class MainActivity 
extends Activity 
implements VehicleDialog.Listener,
	ConfirmationDialog.Listener,
	StorageSelectionDialog.Listener,
	View.OnClickListener {

	/// the gas log
	private GasLog gaslog;

	/// request code for the runtime external storage permission request
	private static final int REQUEST_STORAGE_PERMISSION = 100;

	/// actions that require external storage permission
	private static final int PENDING_NONE = 0;
	private static final int PENDING_EXPORT = 1;
	private static final int PENDING_IMPORT_INTERNAL = 2;

	/// the action to perform once the user grants storage permission
	private int pendingStorageAction = PENDING_NONE;
	
	/// a list of vehicles from the log
	private List<Vehicle> vehicles;
	
	/// the selected vehicle
	private Vehicle selectedVehicle;
	
	/// a "drop down list" of vehicles
	private Spinner spinnerVehicles;
	
	/// list of views that require an existing vehicle
	private List<View> listViewsThatNeedVehicle;
	
	/// an adapter to populate the spinner with vehicle names
	private ArrayAdapter<Vehicle> adapter;
	
    /**
     * DESCRIPTION
     * Called when the activity is starting.
     * @see android.app.Activity#onCreate(android.os.Bundle)
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // restore preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false);
        
        // register as click listener for our views
        ImageButton buttonVehicleAdd = (ImageButton)findViewById(R.id.buttonVehicleAdd);
        ImageButton buttonVehicleEdit = (ImageButton)findViewById(R.id.buttonVehicleEdit);
        ImageButton buttonVehicleDelete = (ImageButton)findViewById(R.id.buttonVehicleDelete);
        Button buttonGetGas = (Button)findViewById(R.id.buttonGetGas);
        Button buttonViewLog = (Button)findViewById(R.id.buttonViewLog);
        Button buttonPlotData = (Button)findViewById(R.id.buttonPlotData);
        Button buttonViewStatistics = (Button)findViewById(R.id.buttonViewStatistics);
        Button buttonBatchLog = (Button)findViewById(R.id.buttonBatchLog);
        Button buttonAddBatch = (Button)findViewById(R.id.buttonAddBatch);
        buttonAddBatch.setOnClickListener(this);
        buttonBatchLog.setOnClickListener(this);
        ImageButton buttonAppBarMenu = (ImageButton)findViewById(R.id.buttonAppBarMenu);
        buttonAppBarMenu.setOnClickListener(this);
        buttonVehicleAdd.setOnClickListener(this);
        buttonVehicleEdit.setOnClickListener(this);
        buttonVehicleDelete.setOnClickListener(this);
        buttonGetGas.setOnClickListener(this);
        buttonViewLog.setOnClickListener(this);
        buttonPlotData.setOnClickListener(this);
        buttonViewStatistics.setOnClickListener(this);
        
        // create a list of views that require an existing vehicle
        listViewsThatNeedVehicle = new LinkedList<View>();
        listViewsThatNeedVehicle.add(buttonVehicleEdit);
        listViewsThatNeedVehicle.add(buttonVehicleDelete);
        listViewsThatNeedVehicle.add(buttonGetGas);
        listViewsThatNeedVehicle.add(buttonViewLog);
        listViewsThatNeedVehicle.add(buttonPlotData);
        listViewsThatNeedVehicle.add(buttonViewStatistics);

        // NOTE: buttonBatchLog is deliberately NOT in the list above. Centrifuge
        // batches are global rather than per-vehicle, so the batch log must stay
        // reachable even when no vehicle has been defined yet. This omission is
        // intentional - please do not "fix" it.

        // create a log instance for use by this application
        gaslog = GasLog.getInstance();
        
        // get a list of all vehicles currently documented in the log
        vehicles = GasLog.getInstance().readAllVehicles();
        
        // create a drop down list for vehicle selection
        adapter = new ArrayAdapter<Vehicle>(this,android.R.layout.simple_spinner_item,vehicles);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVehicles = (Spinner)findViewById(R.id.spinnerVehicles);
        spinnerVehicles.setAdapter(adapter);
        updateVehiclesSpinnerState();
        
        // if the activity is not being re-initialized (for example after screen rotate)
        // start by adding a vehicle if there are none currently defined
        if ((savedInstanceState == null) && vehicles.isEmpty()) {
        	showDialog(DIALOG_ADD_VEHICLE_ID);
        }
        
    }
    
    /**
     * DESCRIPTION:
     * Enable/disable the "drop down list" of vehicles and all
     * vehicle related View instances depending on whether any vehicles
     * are currently defined. 
     */
    private void updateVehiclesSpinnerState() {
    	boolean enabled = !vehicles.isEmpty();
    	spinnerVehicles.setEnabled(enabled);
    	for (View view : listViewsThatNeedVehicle) {
    		view.setEnabled(enabled);
    	}
    }

    /**
     * DESCRIPTION:
     * Initialize the contents of the Activity's standard options menu. 
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }
    
    /**
     * DESCRIPTION:
     * Called when an item in the options menu is selected.
     * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	
    	Intent intent;

    	switch (item.getItemId()) {

    	case R.id.itemVehicleAdd:
    		showDialog(DIALOG_ADD_VEHICLE_ID);
    		return true;

    	case R.id.itemVehicleEdit:
    		if (getSelectedVehicle() != null) {
    			showDialog(DIALOG_EDIT_VEHICLE_ID);
    		}
    		return true;

    	case R.id.itemVehicleDelete:
    		if (getSelectedVehicle() != null) {
    			showDialog(DIALOG_DELETE_VEHICLE_ID);
    		}
    		return true;

    	case R.id.itemBatchLog:
    		viewBatchLog(null);
    		return true;

    	case R.id.itemImport:
    		if (getSelectedVehicle() != null) {
    			showDialog(DIALOG_SELECT_STORAGE_LOCATION_ID);
    		}
    		return true;

    	case R.id.itemExport:
    		if (getSelectedVehicle() == null) return true;
    		if (ensureStoragePermission(PENDING_EXPORT)) {
    			startExport();
    		}
    		return true;

    	case R.id.itemHelp:
			intent = new Intent(this, HtmlViewerActivity.class);
			intent.putExtra(HtmlViewerActivity.URL,getString(R.string.url_help_html));
			startActivity(intent);
			return true;

    	case R.id.itemSettings:
    		intent = new Intent(this,Settings.class);
    		startActivity(intent);
    		return true;

    	default:
    		return super.onOptionsItemSelected(item);
    	}
    }
    
	/**
	 * DESCRIPTION:
	 * Called when a View (i.e Button) that this Activity is a registered 
	 * listener for is clicked. 
	 * @see android.view.View.OnClickListener#onClick(android.view.View)
	 */
	@Override
	public void onClick(View v) {
		
		switch (v.getId()) {
		
		case R.id.buttonVehicleAdd:
	       	showDialog(DIALOG_ADD_VEHICLE_ID);
			break;

		case R.id.buttonVehicleEdit:
            if (getSelectedVehicle() != null) {
            	showDialog(DIALOG_EDIT_VEHICLE_ID);
            }
			break;

		case R.id.buttonVehicleDelete:
            if (getSelectedVehicle() != null) {
            	showDialog(DIALOG_DELETE_VEHICLE_ID);
            }
			break;
			
		case R.id.buttonGetGas:
			getGas(v);
			break;
			
		case R.id.buttonViewLog:
			viewLog(v);
			break;
			
		case R.id.buttonPlotData:
			plotData(v);
			break;

		case R.id.buttonViewStatistics:
			viewStatistics(v);
			break;

		case R.id.buttonAddBatch:
			addBatch(v);
			break;

		case R.id.buttonBatchLog:
			viewBatchLog(v);
			break;

		case R.id.buttonAppBarMenu:
			showOverflowMenu(v);
			break;


		default:
			Utilities.toast(this,"Invalid view id.");
		}
		
	}

	/**
	 * DESCRIPTION:
	 * Displays the options menu as a dropdown anchored to the app bar's overflow
	 * button.<p>
	 *
	 * NOTE: this exists because raising targetSdk above 10 removed the virtual
	 * menu key Android used to place in the navigation bar, and the app theme has
	 * no ActionBar to hold an overflow. Without it there is no way to open an
	 * options menu at all.<p>
	 *
	 * The same menu resource and the same {@link #onOptionsItemSelected} handler
	 * are used, so nothing about menu handling is duplicated here.
	 *
	 * @param anchor - the View to anchor the dropdown to.
	 */
	public void showOverflowMenu(View anchor) {

		PopupMenu popup = new PopupMenu(this, anchor);
		popup.getMenuInflater().inflate(R.menu.activity_main, popup.getMenu());

		// enable/disable the items that need a vehicle to act on
		boolean hasVehicle = !vehicles.isEmpty();
		int[] needVehicle = new int[] {
				R.id.itemVehicleEdit, R.id.itemVehicleDelete,
				R.id.itemImport, R.id.itemExport};
		for (int id : needVehicle) {
			MenuItem item = popup.getMenu().findItem(id);
			if (item != null) item.setEnabled(hasVehicle);
		}

		popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
			@Override
			public boolean onMenuItemClick(MenuItem item) {
				return onOptionsItemSelected(item);
			}
		});

		popup.show();
	}

    /**
     * DESCRIPTION:
     * Determines which vehicle is currently selected in the spinner.
     * @return the selected Vehicle. Also sets the selectedVehicle class attribute.
     */
    protected Vehicle getSelectedVehicle() {
        selectedVehicle = (Vehicle)spinnerVehicles.getSelectedItem();
        if (selectedVehicle == null) {
        	Utilities.toast(this,getString(R.string.toast_select_a_vehicle));
        }
        return selectedVehicle;
    }
    
    /**
     * DESCRIPTION:
     * Selects a vehicle based on its position in the vehicles list.
     * @param position - the list index of the vehicle to select
     */
    protected void setSelectedVehicle(int position) {
    	updateVehiclesSpinnerState();
    	if ((position >= 0) && (position < vehicles.size())) {
    		spinnerVehicles.setSelection(position);
    	}
    }
    
    /**
     * DESCRIPTION:
     * Locates a specified vehicle in the spinner and selects it. If unable
     * to find vehicle, the first vehicle in spinner is selected. 
     * @param name - the name of the vehicle to select.
     */
    protected void setSelectedVehicle(String name) {
    	
    	// find the vehicle by name
    	int position = 0;
    	for (int i = 0; i < vehicles.size(); i++) {
    		if (vehicles.get(i).getName().equals(name)) {
    			position = i;
    		}
    	}
    	
    	// select it
    	setSelectedVehicle(position);
    }
    
    /**
     * DESCRIPTION:
     * Starts an Activity to view the log records for the 
     * selected vehicle.
     * @param view - currently unused - necessary only if called as click listener.
     */
    public void viewLog(View view) {

    	// get the selected vehicle
        if (getSelectedVehicle() == null) return;
        
        // start an Activity to display gas records for the vehicle
    	Intent intent = new Intent(this, GasLogListActivity.class);
    	intent.putExtra(GasLogListActivity.VEHICLE, selectedVehicle);
    	startActivity(intent);
    }
    
    /**
     * DESCRIPTION:
     * Starts an Activity to add a centrifuge batch directly from the home
     * screen, the way Add Fuel adds a fill-up.<p>
     *
     * NOTE: no vehicle is selected or passed. Centrifuge batches are global -
     * a batch of fuel exists before it is poured into any particular vehicle -
     * so this works with no vehicles defined at all.
     *
     * @param view - currently unused - necessary only if called as click listener.
     */
    public void addBatch(View view) {
    	Intent intent = new Intent(this, BatchRecordActivity.class);
    	intent.putExtra(BatchRecordActivity.RECORD, new BatchRecord());
    	startActivityForResult(intent, ADD_BATCH_REQUEST);
    }

    /**
     * DESCRIPTION:
     * Called when the user finishes entering a centrifuge batch started from the
     * home screen.
     * @param record - the BatchRecord entered by the user.
     */
    protected void onAddBatchResult(BatchRecord record) {
    	if (gaslog.createBatchRecord(record)) {
    		Utilities.toast(this,getString(R.string.toast_data_saved));
    	} else {
    		Utilities.toast(this,getString(R.string.toast_error_saving_data));
    	}
    }

    /**
     * DESCRIPTION:
     * Starts an Activity to view the centrifuge batch log.<p>
     *
     * NOTE: no vehicle is selected or passed - see viewBatchLog().
     *
     * @param view - currently unused - necessary only if called as click listener.
     */
    public void viewBatchLog(View view) {
    	Intent intent = new Intent(this, BatchLogListActivity.class);
    	startActivity(intent);
    }

    /**
     * DESCRIPTION:
     * Starts an Activity to get a new gasoline record to be added to
     * the gas log for the selected vehicle.
     * @param view - currently unused - necessary only if called as click listener.
     */
    public void getGas(View view) {

    	// get the selected vehicle
        if (getSelectedVehicle() == null) return;

        // prepare input for GasRecordActivity
    	GasRecord record = new GasRecord(selectedVehicle);
        int current_odometer = gaslog.readCurrentOdometer(selectedVehicle);
        
        // start a GasRecordActivity to get a new gas record for the vehicle
    	Intent intent = new Intent(this, GasRecordActivity.class);
    	intent.putExtra(GasRecordActivity.RECORD, record);
        intent.putExtra(GasRecordActivity.CURRENT_ODOMETER, current_odometer);
        intent.putExtra(GasRecordActivity.TANK_SIZE, selectedVehicle.getTankSize());
    	startActivityForResult(intent,GET_GAS_REQUEST);
    }
    
    /**
     * DESCRIPTION:
     * Starts an Activity to plot data for the selected vehicle.
     * @param view - currently unused - necessary only if called as click listener.
     */
    public void plotData(View view) {

    	// get the selected vehicle
        if (getSelectedVehicle() == null) return;
        
        // start an Activity to display gas records for the vehicle
    	Intent intent = new Intent(this, PlotActivity.class);
    	intent.putExtra(PlotActivity.VEHICLE, selectedVehicle);
    	startActivity(intent);
    }

    /**
     * DESCRIPTION:
     * Starts an Activity to display statistical data for the selected vehicle.
     * @param view - currently unused - necessary only if called as click listener.
     */
    public void viewStatistics(View view) {

    	// get the selected vehicle
        if (getSelectedVehicle() == null) return;
        
        // start an Activity to display statistics for the vehicle
    	Intent intent = new Intent(this, StatisticsActivity.class);
    	intent.putExtra(StatisticsActivity.VEHICLE, selectedVehicle);
    	startActivity(intent);
    }
    
    /**
     * DESCRIPTION:
     * Adds a new vehicle to the log.
     * @param vehicle - the new Vehicle.
     * @return boolean - true if successful.
     */
    protected boolean addVehicle(Vehicle vehicle) {

    	// attempt to add the vehicle to the log
    	if (!gaslog.createVehicle(vehicle)) {
    		Utilities.toast(this,getString(R.string.toast_add_failed));
    		return false;
    	}
    	
    	// read updated list of vehicles from the log
    	vehicles.clear();
    	vehicles.addAll(gaslog.readAllVehicles());
        adapter.notifyDataSetChanged();
    	
        // select the new vehicle by its name
        setSelectedVehicle(vehicle.getName());

        // success
        return true;
    }
    
    /**
     * DESCRIPTION:
     * Updates data for a specified vehicle in the log.
     *
     * @param vehicle - the edited vehicle.
     * @return boolean - true if successful.
     */
    protected boolean editVehicle(Vehicle vehicle) {
    	
    	// attempt to update the vehicle data in the log
    	if (!gaslog.updateVehicle(vehicle)) {
    		Utilities.toast(this,getString(R.string.toast_edit_failed));
    		return false;
    	}

    	// read updated list of vehicles from the log
    	vehicles.clear();
    	vehicles.addAll(gaslog.readAllVehicles());
        adapter.notifyDataSetChanged();
        
        // select the edited vehicle by its new name
        setSelectedVehicle(vehicle.getName());
        
        // success
        return true;
    }
    
    /**
     * DESCRIPTION:
     * Removes the selected vehicle and all its gas records from
     * the log.
     * @return boolean - true if successful.
     */
    protected boolean deleteVehicle() {

    	// attempt to update the vehicle data in the log
    	if (!gaslog.deleteVehicle(selectedVehicle)) {
    		Utilities.toast(this,getString(R.string.toast_delete_failed));
    		return false;
    	}
    	
    	// read updated list of vehicles from the log
    	vehicles.clear();
    	vehicles.addAll(gaslog.readAllVehicles());
        adapter.notifyDataSetChanged();
        
    	// select the first vehicle in the list
       	setSelectedVehicle(0);

        // success
        return true;
    }
    
    /**
     * DESCRIPTION:
     * Dialog box integer ID constants
     * @see #onCreateDialog(int)
     */
    protected static final int DIALOG_ADD_VEHICLE_ID = 1;
    protected static final int DIALOG_EDIT_VEHICLE_ID = 2;
    protected static final int DIALOG_DELETE_VEHICLE_ID = 3;
    protected static final int DIALOG_SHOW_CALCULATION_ID = 4;
    protected static final int DIALOG_SHOW_ESTIMATE_ID = 5;
    protected static final int DIALOG_TANK_NEVER_FILLED_ID = 6;
    protected static final int DIALOG_SELECT_STORAGE_LOCATION_ID = 7;
    protected static final int DIALOG_CONFIRM_EXPORT_OVERWRITE_ID = 8;
    protected static final int DIALOG_CONFIRM_EXPORT_SHARE_ID = 9;

    /**
     * DESCRIPTION:
     * Called as needed by the framework to create dialog boxes used by the Activity.
     * Each dialog box is referenced by a locally defined id integer. 
     * @see android.app.Activity#onCreateDialog(int)
     */
    protected Dialog onCreateDialog(int id) {
        Dialog dialog = null;
        String title;
        String message;
    	
    	switch (id) {
    	case DIALOG_ADD_VEHICLE_ID:
    		title = getString(R.string.vehicle_add_label);
        	dialog = VehicleDialog.create(this,this,id,title,new Vehicle());
        	break;

    	case DIALOG_EDIT_VEHICLE_ID:
    		title = getString(R.string.vehicle_edit_label);
    		dialog = VehicleDialog.create(this,this,id,title,new Vehicle(selectedVehicle));
            break;
            
    	case DIALOG_DELETE_VEHICLE_ID:
    		title = getString(R.string.title_confirm_delete_vehicle);
    		message = getString(R.string.message_confirm_delete_vehicle);
   			dialog = ConfirmationDialog.create(this,this,id,title,message);
        	break;
        	
        case DIALOG_SHOW_CALCULATION_ID:
        	dialog = MileageCalculationDialog.create(this,id);
            break;

        case DIALOG_SHOW_ESTIMATE_ID:
        	dialog = MileageEstimateDialog.create(this,id);
            break;
            
        case DIALOG_TANK_NEVER_FILLED_ID:
   			dialog = TankNeverFilledDialog.create(this,id);
            break;

        case DIALOG_SELECT_STORAGE_LOCATION_ID:
        	dialog = StorageSelectionDialog.create(this,this,id);
        	break;

        case DIALOG_CONFIRM_EXPORT_OVERWRITE_ID:
        	title = getString(R.string.title_confirm_export_overwrite_dialog);
        	message = String.format(App.getLocale(),
        			getString(R.string.message_confirm_export_overwrite_dialog),
        			getExportFile().getAbsolutePath());
        	dialog = ConfirmationDialog.create(this,this,id,title,message);
        	break;

        case DIALOG_CONFIRM_EXPORT_SHARE_ID:
        	title = getString(R.string.title_confirm_export_share_dialog);
        	message = getString(R.string.message_confirm_export_share_dialog);
        	dialog = ConfirmationDialog.create(this,this,id,title,message);
        	break;


        default:
        	Utilities.toast(this,"Invalid dialog id.");
    	}
    	return dialog;
    }
    
	/**
	 * DESCRIPTION:
	 * Called when the user is done entering/editing vehicle data.
	 * @see com.github.wdkapps.fillup.VehicleDialog.Listener#onVehicleDialogClosure(int, com.github.wdkapps.fillup.Vehicle)
	 */
	@Override
	public void onVehicleDialogClosure(int id, Vehicle vehicle) {
		
		removeDialog(id);
		
		if (vehicle == null) {
			Utilities.toast(this,getString(R.string.toast_canceled));
			return;
		}
		
		switch (id) {

		case DIALOG_ADD_VEHICLE_ID:
			addVehicle(vehicle);
			break;

		case DIALOG_EDIT_VEHICLE_ID:
			editVehicle(vehicle);
			break;

		default:
			Utilities.toast(this,"Invalid dialog id.");
		}
	}
	
	/**
	 * DECSRIPTION:
	 * Called when a user response has been obtained from the dialog.
	 * @see com.github.wdkapps.fillup.ConfirmationDialog.Listener#onConfirmationDialogResponse(int, boolean)
	 */
	@Override
	public void onConfirmationDialogResponse(int id, boolean confirmed) {

		removeDialog(id);
		
		if (!confirmed) return;
		
		switch (id) {

		case DIALOG_DELETE_VEHICLE_ID:
			deleteVehicle();
			break;

		case DIALOG_CONFIRM_EXPORT_OVERWRITE_ID:
			exportData();
			break;

		case DIALOG_CONFIRM_EXPORT_SHARE_ID:
			shareExportFile();
			break;

		default:
			Utilities.toast(this,"Invalid dialog id.");
		}
	}

	/**
	 * DESCRIPTION:
	 * Called when the user chooses where to import a CSV file from.
	 * @see com.github.wdkapps.fillup.StorageSelectionDialog.Listener#onStorageSelectionDialogResponse(int, com.github.wdkapps.fillup.StorageSelectionDialog.Result, java.lang.String)
	 */
	@Override
	public void onStorageSelectionDialogResponse(int id, StorageSelectionDialog.Result result, String value) {

		removeDialog(id);

		if (result == StorageSelectionDialog.Result.RESULT_CANCEL) {
			Utilities.toast(this,getString(R.string.toast_canceled));
			return;
		}

		if (id != DIALOG_SELECT_STORAGE_LOCATION_ID) {
			Utilities.toast(this,"Invalid dialog id.");
			return;
		}

		if (value.equals("cloud")) {
			showCloudStorageChooser();
		} else {
			showInternalStorageChooser();
		}
	}

    /**
     * DESCRIPTION:
     * Return the name and path to a file for exporting log data.
     * @return the File.
     */
    protected File getExportFile() {
    	File dir = ExternalStorage.getPublicDownloadDirectory();
    	return new File(dir, selectedVehicle.getName() + ".csv");
    }

    /**
     * DESCRIPTION:
     * Determines whether the export file already exists, and either asks the user
     * to confirm overwriting it or exports immediately.
     */
    protected void startExport() {

    	if (gaslog.readAllRecords(selectedVehicle).isEmpty()) {
    		Utilities.toast(this,getString(R.string.toast_no_data_to_export));
    		return;
    	}

    	if (getExportFile().exists()) {
    		showDialog(DIALOG_CONFIRM_EXPORT_OVERWRITE_ID);
    	} else {
    		exportData();
    	}
    }

    /**
     * DESCRIPTION:
     * Exports data for the selected vehicle to an ASCII CSV file.
     */
    protected void exportData() {

    	if (!ExternalStorage.isWritable()) {
    		Utilities.toast(this,getString(R.string.toast_external_storage_not_writable));
    		return;
    	}

    	File file = getExportFile();

    	if (gaslog.exportData(selectedVehicle,file)) {
    		Utilities.toast(this,getString(R.string.toast_export_complete));
    		Utilities.toast(this,file.getAbsolutePath());
    		showDialog(DIALOG_CONFIRM_EXPORT_SHARE_ID);
    	} else {
    		Utilities.toast(this,getString(R.string.toast_export_failed));
    	}
    }

    /**
     * DESCRIPTION:
     * Offers the exported CSV file to another application.
     */
    protected void shareExportFile() {

    	File file = getExportFile();

    	// NOTE: Uri.fromFile() throws FileUriExposedException at targetSdk >= 24.
    	Uri uri;
    	try {
    		uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
    	} catch (IllegalArgumentException e) {
    		Utilities.toast(this,getString(R.string.toast_failed));
    		return;
    	}

    	Intent intent = new Intent(Intent.ACTION_SEND);
    	intent.setType("text/plain");
    	intent.putExtra(Intent.EXTRA_STREAM,uri);
    	intent.putExtra(Intent.EXTRA_SUBJECT,file.getName());
    	intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
    	intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
    	startActivity(Intent.createChooser(intent, getString(R.string.title_chooser_share_csv)));
    }

    /**
     * DESCRIPTION:
     * Allow the user to select a file to import using an installed cloud application.
     */
    private void showCloudStorageChooser() {
        try {
            Intent target = new Intent(Intent.ACTION_GET_CONTENT);
            // text/csv would be better, but Google Drive needs */* to allow selection
            target.setType("*/*");
            target.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(target,CHOOSE_IMPORT_FILE);
        } catch (android.content.ActivityNotFoundException ex) {
    		Utilities.toast(this,getString(R.string.toast_activity_not_found));
        }
    }

    /**
     * DESCRIPTION:
     * Allow the user to select a file to import from internal storage.
     */
    private void showInternalStorageChooser() {

    	if (!ensureStoragePermission(PENDING_IMPORT_INTERNAL)) return;

    	if (!ExternalStorage.isReadable()) {
    		Utilities.toast(this,getString(R.string.toast_external_storage_not_readable));
    		return;
    	}

    	Intent intent = new Intent(this, FileSelectionActivity.class);
    	File root = Environment.getExternalStorageDirectory();
    	File path = Environment.getExternalStoragePublicDirectory(DOWNLOAD_SERVICE);
    	intent.putExtra(FileSelectionActivity.ROOT,root.getAbsolutePath());
    	intent.putExtra(FileSelectionActivity.PATH,path.getAbsolutePath());
    	intent.putExtra(FileSelectionActivity.EXT,".csv");
    	startActivityForResult(intent,CHOOSE_IMPORT_FILE);
    }

    /**
     * DESCRIPTION:
     * Imports data from an ASCII CSV file into the log for the selected vehicle.
     * @param uri - the file to import.
     */
    protected void importData(Uri uri) {

        InputStream file;
		try {
			file = getContentResolver().openInputStream(uri);
		} catch (FileNotFoundException e) {
    		Utilities.toast(this,getString(R.string.toast_import_failed));
    		return;
		}

    	if (!gaslog.importData(selectedVehicle, file)) {
    		Utilities.toast(this,getString(R.string.toast_import_failed));
    		return;
    	}

		Utilities.toast(this,getString(R.string.toast_import_complete));
    }

    /**
     * DESCRIPTION:
     * Ensures that the app holds the external storage permission required to read
     * and write CSV files. Declared in the manifest is not sufficient from API 23.
     * @param pendingAction - the action to perform once permission is granted.
     * @return true if the permission is already held and the caller may proceed.
     */
    private boolean ensureStoragePermission(int pendingAction) {

    	// permissions are granted at install time below API 23
    	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;

    	if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    			== PackageManager.PERMISSION_GRANTED) {
    		return true;
    	}

    	pendingStorageAction = pendingAction;
    	requestPermissions(new String[] {
    			Manifest.permission.WRITE_EXTERNAL_STORAGE,
    			Manifest.permission.READ_EXTERNAL_STORAGE},
    			REQUEST_STORAGE_PERMISSION);

    	return false;
    }

    /**
     * DESCRIPTION:
     * Called when the user responds to a runtime permission request.
     * @see android.app.Activity#onRequestPermissionsResult(int, java.lang.String[], int[])
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {

    	super.onRequestPermissionsResult(requestCode, permissions, results);

    	if (requestCode != REQUEST_STORAGE_PERMISSION) return;

    	int action = pendingStorageAction;
    	pendingStorageAction = PENDING_NONE;

    	boolean granted = (results.length > 0) && (results[0] == PackageManager.PERMISSION_GRANTED);
    	if (!granted) {
    		Utilities.toast(this,getString(R.string.toast_external_storage_not_writable));
    		return;
    	}

    	switch (action) {
    	case PENDING_EXPORT:
    		startExport();
    		break;
    	case PENDING_IMPORT_INTERNAL:
    		showInternalStorageChooser();
    		break;
    	default:
    		break;
    	}
    }

    /**
     * DESCRIPTION:
     * Adds a gas record to the log for the selected vehicle.
     * @param record - the GasRecord data entered by the user.
     */
    protected void addGasRecord(GasRecord record) {

    	// get a list of records from the log before adding new record
		List<GasRecord> list = gaslog.readAllRecords(selectedVehicle);
    	
		// attempt to add the new record to the log
    	if (!gaslog.createRecord(selectedVehicle,record)) { 
			Utilities.toast(this,getString(R.string.toast_error_saving_data));
    		return;
		}
    	
    	// success!
    	Utilities.toast(this,getString(R.string.toast_data_saved));
    	
    	// need a previous full tank in the log to do any calculations
    	if (TankNeverFilledDialog.isDisplayable(list)) {
    		showDialog(DIALOG_TANK_NEVER_FILLED_ID);
    		return;
    	}
    	
    	// add the new record to the list
    	list.add(record);
    	
    	// recalculate mileage
    	GasRecordList.calculateMileage(list);
    	
    	// find the new location of the record in the list (after sort)
    	int location = GasRecordList.find(list,record);
			
    	// display mileage calculation if possible
    	if (MileageCalculationDialog.isDisplayable(record)) {
    		MileageCalculationDialog.init(record);
    		showDialog(DIALOG_SHOW_CALCULATION_ID);
    		return;
    	} 

    	// display mileage estimate if possible
    	if (MileageEstimateDialog.isDisplayable(selectedVehicle,list,location)) {
    		MileageEstimateDialog.init(selectedVehicle,list,location);
    		showDialog(DIALOG_SHOW_ESTIMATE_ID);
    	} 
    
    }
    
	/**
	 * DESCRIPTION:
	 * Request code constants for onActivityResult()
	 * @see #onActivityResult(int, int, Intent)
	 */
	private static final int GET_GAS_REQUEST = 1;
	private static final int CHOOSE_IMPORT_FILE = 2;
	private static final int ADD_BATCH_REQUEST = 3;
    
    /**
     * DESCRIPTION:
     * Called when an activity launched by this activity exits, giving the 
     * requestCode it was started with, the resultCode it returned, and any 
     * additional data from it.
     * 
     * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);  

        switch(requestCode) {     
        case GET_GAS_REQUEST: 
        	if (resultCode == Activity.RESULT_OK) {
        		GasRecord record = (GasRecord)intent.getSerializableExtra(GasRecordActivity.RECORD);
        		addGasRecord(record);
        	} else {
        		Utilities.toast(this,getString(R.string.toast_canceled));
        	}
        	break;

        case ADD_BATCH_REQUEST:
        	if ((resultCode == Activity.RESULT_OK) && (intent != null)) {
        		BatchRecord batch = (BatchRecord)intent.getSerializableExtra(BatchRecordActivity.RECORD);
        		if (batch != null) onAddBatchResult(batch);
        	} else {
        		Utilities.toast(this,getString(R.string.toast_canceled));
        	}
        	break;

        case CHOOSE_IMPORT_FILE:
        	if ((resultCode == Activity.RESULT_OK) && (intent != null) && (intent.getData() != null)) {
        		importData(intent.getData());
        	} else {
        		Utilities.toast(this,getString(R.string.toast_canceled));
        	}
        	break;

        default:
        	Utilities.toast(this,"Invalid Request Code.");
        }
    }
    
	/**
	 * DESCRIPTION:
	 * Save current state data.
	 * NOTE: This gets called when the screen is rotated. The
	 * Activity is then destroyed, re-created, and state restored.
	 * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
	 */
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putSerializable("selectedVehicle", selectedVehicle);
	}

	/**
	 * DESCRIPTION:
	 * Restore previously saved state data.
	 * @see android.app.Activity#onRestoreInstanceState(android.os.Bundle)
	 */
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		selectedVehicle = (Vehicle)savedInstanceState.getSerializable("selectedVehicle");
	}

}
