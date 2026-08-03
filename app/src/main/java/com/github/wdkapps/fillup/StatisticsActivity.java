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
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.List;

import android.Manifest;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;

/**
 * DESCRIPTION:
 * Implements an Activity that calculates and displays statistics for 
 * gas record data for a specified vehicle.
 */
public class StatisticsActivity extends Activity implements OnSharedPreferenceChangeListener
{
	/// key name for the Vehicle to pass via Intent
	/// gas records for this vehicle are the data to gather statistics for 
	public final static String VEHICLE = StatisticsActivity.class.getName() + ".VEHICLE";

	/// the vehicle (obtained via Intent)
	private Vehicle vehicle;
	
	/// the data to plot
	public static List<GasRecord> records = null;

	/// the centrifuge batch records (global, not vehicle specific)
	private List<BatchRecord> batches;

	/// the data to display statistics for
	private MonthlyTrips monthly;
	
	/// the Android WebView for display of statistics data
	private WebView webview;
	
	/// buttons for selection of range of data to evaluate
	private PlotDateRangeButtons rangeButtons;
	
	/// the report
	private HtmlData report;

	/// request code for the runtime external storage permission request
	private static final int REQUEST_STORAGE_PERMISSION = 100;
	
    /**
     * DESCRIPTION:
     * Called when the Activity is created.
     * @see android.app.ActivityGroup#onCreate(android.os.Bundle)
     */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_statistics);
		
        // get the vehicle from Intent
        Intent intent = getIntent();
        vehicle = (Vehicle)intent.getSerializableExtra(VEHICLE);
        
        // read the data from the gas log 
        GasLog gaslog = GasLog.getInstance();
        records = gaslog.readAllRecords(vehicle);

        // read the centrifuge batch records
        // NOTE: batches are global, not vehicle specific
        batches = gaslog.readAllBatchRecords();

        // calculate monthly totals
        monthly = new MonthlyTrips(records);
        
    	// initialize the layout
    	rangeButtons = new PlotDateRangeButtons(this,Settings.KEY_PLOT_DATE_RANGE);
		webview = (WebView)findViewById(R.id.webviewStats);
    	webview.getSettings().setDefaultTextEncodingName("utf-8");
    	webview.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
    	
    	// automatically scroll to top of page when data changes
    	webview.setWebViewClient(new WebViewClient() {
    		public void onPageFinished(WebView view, String url) {
    			view.scrollTo(0,0);
    		}
    	});
    	
        // setup to be notified when settings change (plot range)
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);;
		prefs.registerOnSharedPreferenceChangeListener(this);

		// generate the report
		generateReport();
		
		// display the report
		displayReport();
	}
	
	
	/**
	 * DESCRIPTION:
	 * Generates the statistics report from the monthly data.
	 */
	private void generateReport() {
    	String title = rangeButtons.getPlotDateRange().getSummary();
    	// NOTE: 'records' is the complete unfiltered history for the vehicle. The
    	// savings table needs it that way, to derive an all-time average pump price.
    	report = new StatisticsReport(title,monthly,records,batches);
	}
	
	/**
	 * DESCRIPTION:
	 * Display a StatisticsReport for the current monthly data set in the WebView.
	 */
	private void displayReport() {
       	webview.loadDataWithBaseURL(null, report.getHtml(), "text/html", "utf-8", null);
	}

	/**
     * DESCRIPTION:
     * Initialize the contents of the Activity's standard options menu. 
     * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
     */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_statistics, menu);
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

    	case R.id.itemShare:
    		if (!ensureStoragePermission()) return true;
    	   	if (ExternalStorage.isWritable()) {
    	   		shareReport();
        	} else {
        		Utilities.toast(this,getString(R.string.toast_external_storage_not_writable));
        	}
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
     * Ensures that the app holds the external storage permission required to write
     * the shared report file.
     *
     * The permission is declared in the manifest, but as of API 23 that is no longer
     * sufficient - it must also be granted at runtime, or createReportFile() fails
     * inside its catch-all and the user only sees a generic error.
     *
     * @return true if the permission is already held and the caller may proceed.
     */
    private boolean ensureStoragePermission() {

    	// permissions are granted at install time below API 23
    	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;

    	if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
    			== PackageManager.PERMISSION_GRANTED) {
    		return true;
    	}

    	requestPermissions(new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE},
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

    	boolean granted = (results.length > 0) && (results[0] == PackageManager.PERMISSION_GRANTED);
    	if (granted) {
    		shareReport();
    	} else {
    		Utilities.toast(this,getString(R.string.toast_external_storage_not_writable));
    	}
    }

    /**
     * DESCRIPTION:
     * Return the name and path to a file for storage statistics report data.
     * @return the File.
     */
    protected File getReportFile() {
    	
    	// get path to external storage directory 
    	File dir = ExternalStorage.getPublicDownloadDirectory();
    	
    	// export file is named after vehicle and stored in external storage directory
    	String format = getString(R.string.stats_report_filename);
    	String file = String.format(App.getLocale(),format,vehicle.getName());
    	return new File(dir,file);
    }
    
    /**
     * DESCRIPTION:
     * Creates a statistics report file containing the report html data.
     * @param file - the report File to create.
     * @return boolean - true if file creation successful.
     */
    private boolean createReportFile(File file) {
    	boolean status = false;
    	PrintStream out = null;
    	try {
    		out = new PrintStream(new FileOutputStream(file));
    		out.println(report.getHtml());
    		status = true;
    	} catch(Throwable t) {
    		Log.e(getClass().getName(),"createReportFile() failed",t);
    	} finally {
    		if (out != null) out.close();
    	}
    	return status;
    }
    
    /**
     * DESCRIPTION:
     * Create a file containing statistics report data (HTML) and prompt
     * the user for a method to share the file. 
     */
    private void shareReport() {
    	
    	File file = getReportFile();
	
    	if (!createReportFile(file)) {
    		Utilities.toast(this,getString(R.string.toast_create_report_failed));
    	}

		Utilities.toast(this, file.getAbsolutePath());

		// NOTE: Uri.fromFile() throws FileUriExposedException at targetSdk >= 24.
		// The file must be handed to the share target through a FileProvider
		// (declared in AndroidManifest.xml, paths in res/xml/file_paths.xml).
		Uri uri;
		try {
			uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
		} catch (IllegalArgumentException e) {
			Utilities.toast(this,getString(R.string.toast_create_report_failed));
			return;
		}

		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("text/html");
		intent.putExtra(Intent.EXTRA_STREAM,uri);
		intent.putExtra(Intent.EXTRA_SUBJECT,file.getName());
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		startActivity(Intent.createChooser(intent, getString(R.string.title_chooser_share_html)));
    }

	/**
	 * DESCRIPTION:
	 * Called when a shared preference is changed, added, or removed.
	 * @see android.content.SharedPreferences.OnSharedPreferenceChangeListener#onSharedPreferenceChanged(android.content.SharedPreferences, java.lang.String)
	 */
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		
		// update the display when the plot date range changes
		if (key.equals(Settings.KEY_PLOT_DATE_RANGE)) {
			generateReport();
			displayReport();
		} 

		// update the display when units of measurement change
		if (key.equals(Settings.KEY_UNITS)) {
        	GasRecordList.calculateMileage(records);
            monthly = new MonthlyTrips(records);
			generateReport();
			displayReport();
		} 
		
	}
	
}
