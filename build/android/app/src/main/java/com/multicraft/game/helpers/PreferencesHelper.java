/*
MultiCraft
Copyright (C) 2014-2021 MoNTE48, Maksim Gamarnik <MoNTE48@mail.ua>
Copyright (C) 2014-2021 ubulem,  Bektur Mambetov <berkut87@gmail.com>

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU Lesser General Public License as published by
the Free Software Foundation; either version 3.0 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public License along
with this program; if not, write to the Free Software Foundation, Inc.,
51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
*/

package com.multicraft.game.helpers;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferencesHelper {
	public static final String TAG_SHORTCUT_EXIST = "createShortcut";
	public static final String TAG_BUILD_NUMBER = "buildNumber";
	public static final String TAG_LAUNCH_TIMES = "launchTimes";
	private static final String SETTINGS = "MultiCraftSettings";

	private static PreferencesHelper instance;
	private static SharedPreferences sharedPreferences;

	private PreferencesHelper(Context context) {
		sharedPreferences = context.getSharedPreferences(SETTINGS, Context.MODE_PRIVATE);
	}

	public static PreferencesHelper getInstance(Context context) {
		if (instance == null) {
			synchronized (PreferencesHelper.class) {
				if (instance == null)
					instance = new PreferencesHelper(context.getApplicationContext());
			}
		}
		return instance;
	}

	public boolean isCreateShortcut() {
		return sharedPreferences.getBoolean(TAG_SHORTCUT_EXIST, false);
	}

	public String getBuildNumber() {
		return sharedPreferences.getString(TAG_BUILD_NUMBER, "0");
	}

	public int getLaunchTimes() {
		return sharedPreferences.getInt(TAG_LAUNCH_TIMES, 0);
	}

	public void saveSettings(String tag, boolean bool) {
		sharedPreferences.edit().putBoolean(tag, bool).apply();
	}

	public void saveSettings(String tag, String value) {
		sharedPreferences.edit().putString(tag, value).apply();
	}

	public void saveSettings(String tag, int value) {
		sharedPreferences.edit().putInt(tag, value).apply();
	}
}
