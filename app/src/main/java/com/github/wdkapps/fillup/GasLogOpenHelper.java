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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * DESCRIPTION:
 * An Android SQLiteOpenHelper for our gas log database.
 */
class GasLogOpenHelper extends SQLiteOpenHelper {
	
	/// tag for logging
	private static final String TAG = GasLogOpenHelper.class.getName();

	/// name of the backup copy made before a database upgrade is attempted
	private static final String BACKUP_NAME = GasLog.DATABASE_NAME + ".bak";

	/**
	 * DESCRIPTION:
	 * Constructs an instance of GasLogOpenHelper.
	 * @param context - the application or activity Context that owns the database.
	 */
	public GasLogOpenHelper(Context context) {
        super(context, GasLog.DATABASE_NAME, null, GasLog.DATABASE_VERSION);
	}

	/**
	 * DESCRIPTION:
	 * Creates the database.
	 * @see android.database.sqlite.SQLiteOpenHelper#onCreate(android.database.sqlite.SQLiteDatabase)
	 */
	@Override
	public void onCreate(SQLiteDatabase db) {
		execSQL(db,GasLog.DATABASE_CREATE);
    }

	/**
	 * DESCRIPTION:
	 * Upgrades the database to a new version.
	 * @see android.database.sqlite.SQLiteOpenHelper#onUpgrade(android.database.sqlite.SQLiteDatabase, int, int)
	 */
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		
		final String tag = TAG + ".onUpgrade()";
		
		Log.d(tag,"oldVersion="+oldVersion+"  newVersion="+newVersion);

		// If any statement below throws, the catch block drops and recreates every
		// table - destroying the user's entire fuel log behind a Toast. Take a copy
		// of the database file first so the data is at least recoverable.
		backupDatabase(db);

		LinkedList<String> sql = new LinkedList<String>();

		while (oldVersion < newVersion) {

			switch (oldVersion) {
			case 2:
				sql.add("ALTER TABLE Records ADD COLUMN hidden integer not null default 0;");
				break;

			case 3:
				sql.add("ALTER TABLE Vehicles ADD COLUMN tanksize real not null default 16.0;");
				break;

			case 4:
				sql.add("ALTER TABLE Records ADD COLUMN cost real not null default 0.0;");
				sql.add("ALTER TABLE Records ADD COLUMN notes text;");
				break;

			case 5:
				// SQLite permits ADD COLUMN ... NOT NULL only when a non-null DEFAULT
				// is supplied. It backfills every existing row with that default, which
				// is exactly the required behavior: pre-existing fill-ups become
				// Pump Diesel.
				sql.add("ALTER TABLE Records ADD COLUMN fuel_type text not null default 'diesel';");

				// Batches is a new table, so this is a CREATE rather than an ALTER.
				sql.add("create table Batches ( " +
						"_id integer primary key autoincrement, " +
						"time integer not null, " +
						"yield_gallons real not null, " +
						"raw_gallons real not null default 0, " +
						"notes text);");
				break;

			case 6:
				// blend percentage and what the batch cost to produce
				sql.add("ALTER TABLE Batches ADD COLUMN blend_percent real not null default 0;");
				sql.add("ALTER TABLE Batches ADD COLUMN cost real not null default 0;");
				break;
			}

			oldVersion++;
		}

		try {
			execSQL(db,(String[])sql.toArray(new String[sql.size()]));
		} catch (Throwable t) {
			String message = App.getContext().getString(R.string.toast_database_update_failed);
			Utilities.toast(App.getContext(),message );
			Log.e(tag,message,t);
			execSQL(db,GasLog.DATABASE_DELETE);
			onCreate(db);
		}
	}
	
	/**
	 * DESCRIPTION:
	 * Copies the database file to a backup alongside it, before an upgrade is
	 * attempted.<p>
	 *
	 * The upgrade path in {@link #onUpgrade} responds to any failure by dropping
	 * and recreating every table, which destroys all of the user's vehicles and
	 * fill-up records. That behavior predates this change and is left alone, but
	 * it means an upgrade is the single most dangerous thing this application
	 * does, so a copy is taken first.
	 *
	 * The backup is written next to the database in the application's private
	 * data directory. It is intentionally best-effort: a failure to write it must
	 * not itself abort the upgrade.
	 *
	 * @param db - the database being upgraded.
	 */
	private void backupDatabase(SQLiteDatabase db) {

		final String tag = TAG + ".backupDatabase()";

		InputStream in = null;
		OutputStream out = null;
		try {
			File source = new File(db.getPath());
			if (!source.exists()) return;

			File backup = new File(source.getParentFile(), BACKUP_NAME);

			in = new FileInputStream(source);
			out = new FileOutputStream(backup);

			byte[] buffer = new byte[8192];
			int count;
			while ((count = in.read(buffer)) > 0) {
				out.write(buffer,0,count);
			}
			out.flush();

			Log.d(tag,"database backed up to " + backup.getAbsolutePath());

		} catch (Throwable t) {
			// best effort only - do not prevent the upgrade from proceeding
			Log.e(tag,"unable to back up database",t);
		} finally {
			try { if (in != null) in.close(); } catch (Throwable ignored) {}
			try { if (out != null) out.close(); } catch (Throwable ignored) {}
		}
	}

	/**
	 * DESCRIPTION:
	 * Convenience method to execute an array or SQL command Strings.
	 * @param db - the database.
	 * @param statements - array of SQL statement Strings to execute.
	 */
	private void execSQL(SQLiteDatabase db, String [] statements) {
		final String tag = TAG + ".execSQL()";
		for (String sql : statements) {
			Log.d(tag,sql);
			db.execSQL(sql);
		}
	}

}
