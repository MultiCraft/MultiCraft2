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

package com.multicraft.game.helpers

import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.KITKAT
import android.os.Build.VERSION_CODES.O

object ApiLevelHelper {
	private fun isGreaterOrEqual(versionCode: Int): Boolean {
		return SDK_INT >= versionCode
	}

	@JvmStatic
	val isKitKat: Boolean
		get() = isGreaterOrEqual(KITKAT)

	val isMarshmallow: Boolean
		get() = isGreaterOrEqual(Build.VERSION_CODES.M)

	@JvmStatic
	val isOreo: Boolean
		get() = isGreaterOrEqual(O)
}
