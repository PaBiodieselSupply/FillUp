Building FillUp Bio
===================

Prerequisites
-------------

+ **JDK 17** — required by Android Gradle Plugin 8.x. Newer JDKs are not
  guaranteed to work with AGP 8.5.
+ **Android SDK** with platform 34 installed, and `ANDROID_HOME` (or
  `ANDROID_SDK_ROOT`) pointing at it. Installing Android Studio is the easiest
  way to get this.
+ **Gradle 8.7** on your PATH *for the first build only* — see below.

The Gradle wrapper JAR is not in this repository
------------------------------------------------

`gradle/wrapper/gradle-wrapper.properties` is present, but `gradle-wrapper.jar`,
`gradlew` and `gradlew.bat` are not — they are binaries and could not be
generated on the machine this port was written on.

Generate them once, from the project root:

```bash
gradle wrapper --gradle-version 8.7
```

After that, use `./gradlew` (or `gradlew.bat`) as normal and Gradle need not be
installed globally any more.

Alternatively, just open the project folder in Android Studio — it will offer to
generate the wrapper and download the SDK components for you.

Building
--------

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

Notable build configuration
---------------------------

Three settings in this project are load-bearing and should not be changed
casually.

**`android.nonFinalResIds=false` in `gradle.properties`.**
AGP 8 makes `R` fields non-`final` by default, and a non-final `int` cannot be
used as a `switch` case label. This codebase has 8 switch statements over `R.id`
covering 32 case labels. Removing this line means rewriting all of them as
if/else chains.

**`targetSdk 29` plus `android:requestLegacyExternalStorage="true"`.**
This opts the app out of scoped storage, which is what keeps CSV export,
`FileSelectionActivity`'s file browser and `ExternalStorage.getPublicDownloadDirectory()`
working unchanged. The app installs fine on Android 14/15 but **cannot be
published to the Google Play Store**, which requires targetSdk 34+. Raising it
means deleting `FileSelectionActivity` and rewriting CSV import/export onto the
Storage Access Framework and MediaStore — budget that as its own piece of work.

**`applicationId "com.pabiodiesel.fillup"`.**
The Java package is still `com.github.wdkapps.fillup` (kept for Apache 2.0
attribution and to avoid renaming 58 files), but the application id is
rebranded. This means the app installs **side by side** with a stock FillUp
install rather than upgrading it — a user migrating from stock FillUp must
export their CSV from the old app and import it into this one.

Database migration
------------------

Database version 6 adds `Records.fuel_type` (defaulting existing rows to
`diesel`) and creates the `Batches` table.

**The v5 → v6 migration does not run on a first install of this app, and will
not run for any existing FillUp user.** Because the application id was rebranded
to `com.pabiodiesel.fillup`, this app gets its own private database directory
and never sees a stock FillUp database. A first install therefore goes straight
to `onCreate()` at version 6; `onUpgrade()` is never called.

The migration code matters only for upgrades *between builds of this app* — for
example if an interim build reaches testers before a later schema change. It is
correct and worth keeping, but it is not on the path of a new install.

**The real migration path for an existing FillUp user is CSV**: export from
stock FillUp, import here. Prioritise testing that. Old exports have 7 or 8
fields, no fuel type column, and import as Pump Diesel.

`GasLogOpenHelper.onUpgrade()` responds to *any* failure by dropping and
recreating every table, which destroys all of the user's vehicles and fill-up
records. That behavior predates this fork and has been left alone, but a
best-effort copy of the database file is now made to `gaslog.db.bak` in the
app's private data directory before an upgrade is attempted.

There is no automated test coverage anywhere in this project.
