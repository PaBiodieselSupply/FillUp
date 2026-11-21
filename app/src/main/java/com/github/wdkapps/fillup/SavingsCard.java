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

import java.io.File;
import java.io.FileOutputStream;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.Uri;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.FileProvider;

/**
 * DESCRIPTION:
 * Draws and shares the savings card: a 1080 x 1080 image summarising one
 * vehicle's waste oil fuel savings over the selected date range, made for
 * posting to a Facebook group.<p>
 *
 * NOTES:<p>
 * <ol>
 * <li>The design is the layout res/layout/savings_card.xml - a reusable
 * template. This class only fills in the numbers and turns it into an image.
 * <li>Every figure comes from {@link SavingsSummary}, the same calculation as
 * the savings table, so the card always matches the app.
 * <li>Apps cannot post into a Facebook group themselves - Meta removed that.
 * What the Facebook app does offer is a "Share to Groups" entry point, which
 * this opens directly with the image attached, so the user only has to pick the
 * group and tap Post. That entry point is Facebook's internal component name,
 * not a documented API, so it is looked up first and the ordinary share list is
 * used whenever it is missing or refuses.
 * <li>Facebook ignores text that other apps try to pre-fill in a post, so the
 * message is on the image, and a ready-made caption is copied to the clipboard
 * for the user to paste.
 * </ol>
 */
final class SavingsCard {

	private static final String TAG = SavingsCard.class.getName();

	/// the card is square: the shape Facebook shows largest in a feed
	private static final int SIZE_PX = 1080;

	/// the Facebook app's "Share to Groups" entry point (see class notes)
	private static final ComponentName FACEBOOK_SHARE_TO_GROUPS = new ComponentName(
			"com.facebook.katana",
			"com.facebook.composer.shareintent.ShareToGroupsAlias");

	private SavingsCard() {}

	/**
	 * DESCRIPTION:
	 * Draws the savings card.
	 * @param context - a Context.
	 * @param vehicleName - the vehicle the figures are for.
	 * @param rangeName - the selected date range, e.g. "Past 6 months".
	 * @param savings - the savings calculation. {@link SavingsSummary#hasSavings()}
	 * must be true.
	 * @return the card as a 1080 x 1080 image.
	 */
	static Bitmap render(Context context, String vehicleName, String rangeName, SavingsSummary savings) {

		Units units = new Units(Settings.KEY_UNITS);
		CurrencyFormatter money = CurrencyManager.getInstance().getSymbolicFormatter();
		FuelTypeTotals fuel = savings.getInRange();

		View card = LayoutInflater.from(context).inflate(R.layout.savings_card, null);

		text(card, R.id.cardContext, context.getString(R.string.card_context, vehicleName, rangeName));
		text(card, R.id.cardSavings, money.format(savings.getSavings()));

		// fuel split
		weight(card, R.id.cardSplitWasteOil, fuel.getWasteOilFraction() * 100f);
		weight(card, R.id.cardSplitPump, fuel.getPumpDieselFraction() * 100f);
		text(card, R.id.cardSplitLabel, String.format(App.getLocale(),
				context.getString(R.string.card_split),
				fuel.getWasteOilPercent(), fuel.getPumpDieselPercent()));

		// waste oil fuel burned
		text(card, R.id.cardGallons, String.format(App.getLocale(), "%.1f", fuel.getWasteOilGallons()));
		text(card, R.id.cardGallonsLabel, String.format(App.getLocale(),
				context.getString(R.string.card_gallons_label), units.getLiquidVolumeLabelLowerCase()));

		// what the user's fuel cost against the pump, or just the pump price
		if (savings.hasBlendedPrice()) {
			text(card, R.id.cardPrice, String.format(App.getLocale(),
					context.getString(R.string.card_price_vs),
					money.format(savings.getBlendedPrice()), money.format(savings.getPumpPrice())));
			text(card, R.id.cardPriceLabel, String.format(App.getLocale(),
					context.getString(R.string.card_price_vs_label), units.getLiquidVolumeRatioLabel()));
		} else {
			text(card, R.id.cardPrice, money.format(savings.getPumpPrice()));
			text(card, R.id.cardPriceLabel, String.format(App.getLocale(),
					context.getString(R.string.card_price_pump_label), units.getLiquidVolumeRatioLabel()));
		}

		// lay the template out at exactly 1080 x 1080 and draw it
		int spec = View.MeasureSpec.makeMeasureSpec(SIZE_PX, View.MeasureSpec.EXACTLY);
		card.measure(spec, spec);
		card.layout(0, 0, SIZE_PX, SIZE_PX);
		Bitmap image = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888);
		card.draw(new Canvas(image));
		return image;
	}

	/**
	 * DESCRIPTION:
	 * Builds the caption copied to the clipboard alongside the card.
	 * @param context - a Context.
	 * @param vehicleName - the vehicle the figures are for.
	 * @param rangeName - the selected date range.
	 * @param savings - the savings calculation.
	 * @return the caption text.
	 */
	static String caption(Context context, String vehicleName, String rangeName, SavingsSummary savings) {
		return context.getString(R.string.card_caption,
				CurrencyManager.getInstance().getSymbolicFormatter().format(savings.getSavings()),
				vehicleName, rangeName.toLowerCase(App.getLocale()));
	}

	/**
	 * DESCRIPTION:
	 * Saves the card, copies the caption, and hands both to Facebook's
	 * "Share to Groups" screen - or to the ordinary share list if that isn't
	 * available.
	 * @param activity - the Activity sharing the card.
	 * @param image - the card.
	 * @param caption - the caption to copy for the user to paste.
	 * @return false if the card could not be saved.
	 */
	static boolean share(Activity activity, Bitmap image, String caption) {

		// written to the app's private cache: no storage permission needed, and
		// FileProvider (res/xml/file_paths.xml) grants the receiving app read access
		Uri uri;
		try {
			File dir = new File(activity.getCacheDir(), "share");
			if (!dir.exists() && !dir.mkdirs()) return false;
			File file = new File(dir, "savings.png");
			FileOutputStream out = new FileOutputStream(file);
			try {
				image.compress(Bitmap.CompressFormat.PNG, 100, out);
			} finally {
				out.close();
			}
			uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", file);
		} catch (Exception e) {
			Log.e(TAG, "unable to save savings card", e);
			return false;
		}

		// Facebook ignores pre-filled text, so put the caption on the clipboard
		ClipboardManager clipboard = (ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
		if (clipboard != null) {
			clipboard.setPrimaryClip(ClipData.newPlainText(
					activity.getString(R.string.card_caption_clip_label), caption));
			Utilities.toast(activity, activity.getString(R.string.toast_caption_copied));
		}

		Intent intent = new Intent(Intent.ACTION_SEND);
		intent.setType("image/png");
		intent.putExtra(Intent.EXTRA_STREAM, uri);
		intent.putExtra(Intent.EXTRA_TEXT, caption);   // used by apps other than Facebook
		intent.setClipData(ClipData.newRawUri(null, uri));
		intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

		// straight to Facebook's group picker when it exists
		Intent toGroups = new Intent(intent);
		toGroups.setComponent(FACEBOOK_SHARE_TO_GROUPS);
		if (toGroups.resolveActivity(activity.getPackageManager()) != null) {
			try {
				activity.startActivity(toGroups);
				return true;
			} catch (ActivityNotFoundException e) {
				Log.w(TAG, "Facebook Share to Groups unavailable", e);
			} catch (SecurityException e) {
				Log.w(TAG, "Facebook Share to Groups refused", e);
			}
		}

		// otherwise the ordinary share list
		activity.startActivity(Intent.createChooser(intent,
				activity.getString(R.string.title_chooser_share_savings)));
		return true;
	}

	private static void text(View card, int id, String value) {
		((TextView)card.findViewById(id)).setText(value);
	}

	private static void weight(View card, int id, float weight) {
		View view = card.findViewById(id);
		LinearLayout.LayoutParams params = (LinearLayout.LayoutParams)view.getLayoutParams();
		params.weight = weight;
		view.setLayoutParams(params);
	}

}
