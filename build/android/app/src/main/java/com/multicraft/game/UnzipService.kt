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

package com.multicraft.game

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService
import com.multicraft.game.helpers.ApiLevelHelper.isOreo
import com.multicraft.game.helpers.Utilities.copyInputStreamToFile
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.model.LocalFileHeader
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.*

class UnzipService : JobIntentService() {
	private val id = 1
	private var mNotifyManager: NotificationManager? = null
	private lateinit var failureMessage: String
	private var isSuccess = true

	override fun onHandleWork(intent: Intent) {
		createNotification()
		unzip(intent)
	}

	override fun onDestroy() {
		super.onDestroy()
		if (mNotifyManager != null) mNotifyManager!!.cancel(id)
		publishProgress(if (isSuccess) UNZIP_SUCCESS else UNZIP_FAILURE)
	}

	private fun createNotification() {
		val name = "com.multicraft.game"
		val channelId = "MultiCraft channel"
		val description = "notifications from MultiCraft"
		val builder: Notification.Builder
		if (mNotifyManager == null) mNotifyManager =
			getSystemService(NOTIFICATION_SERVICE) as NotificationManager
		if (isOreo) {
			val importance = NotificationManager.IMPORTANCE_LOW
			var mChannel: NotificationChannel? = null
			if (mNotifyManager != null) mChannel =
				mNotifyManager!!.getNotificationChannel(channelId)
			if (mChannel == null) {
				mChannel = NotificationChannel(channelId, name, importance)
				mChannel.description = description
				// Configure the notification channel, NO SOUND
				mChannel.setSound(null, null)
				mChannel.enableLights(false)
				mChannel.enableVibration(false)
				mNotifyManager!!.createNotificationChannel(mChannel)
			}
			builder = Notification.Builder(this, channelId)
		} else @Suppress("DEPRECATION") {
			builder = Notification.Builder(this)
		}
		builder.setContentTitle(getString(R.string.notification_title))
			.setContentText(getString(R.string.notification_description))
			.setSmallIcon(R.drawable.update)
		mNotifyManager!!.notify(id, builder.build())
	}

	@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
	private fun unzip(intent: Intent?) {
		try {
			val zips: ArrayList<String> =
				intent?.getStringArrayListExtra(EXTRA_KEY_IN_FILE)
					?: throw NullPointerException("No data received")
			val cache = cacheDir.toString()
			val files = filesDir.toString()
			var per = 0
			val size = getSummarySize(zips, cache)
			for (zip in zips) {
				val zipFile = File(cache, zip)
				var localFileHeader: LocalFileHeader?
				FileInputStream(zipFile).use { fileInputStream ->
					ZipInputStream(
						fileInputStream
					).use { zipInputStream ->
						while (zipInputStream.nextEntry.also { localFileHeader = it } != null) {
							if (localFileHeader == null) continue
							val extracted = File(files, localFileHeader!!.fileName)
							if (localFileHeader!!.isDirectory)
								extracted.mkdirs()
							else
								extracted.copyInputStreamToFile(zipInputStream)
							++per
							publishProgress(100 * per / size)
						}
					}
				}
			}
		} catch (e: IOException) {
			failureMessage = e.localizedMessage
			isSuccess = false
		} catch (e: NullPointerException) {
			failureMessage = e.localizedMessage
			isSuccess = false
		}
	}

	private fun publishProgress(progress: Int) {
		val intentUpdate = Intent(ACTION_UPDATE)
		intentUpdate.putExtra(ACTION_PROGRESS, progress)
		if (!isSuccess) intentUpdate.putExtra(ACTION_FAILURE, failureMessage)
		sendBroadcast(intentUpdate)
	}

	private fun getSummarySize(zips: List<String>, path: String): Int {
		var size = 1
		for (zip in zips) {
			val zipFile = ZipFile(File(path, zip))
			size += zipFile.fileHeaders.size
		}
		return size
	}

	companion object {
		const val ACTION_UPDATE = "com.multicraft.game.UPDATE"
		const val EXTRA_KEY_IN_FILE = "com.multicraft.game.file"
		const val ACTION_PROGRESS = "com.multicraft.game.progress"
		const val ACTION_FAILURE = "com.multicraft.game.failure"
		const val UNZIP_SUCCESS = -1
		const val UNZIP_FAILURE = -2
		private const val JOB_ID = 1

		@JvmStatic
		fun enqueueWork(context: Context, work: Intent) {
			enqueueWork(context, UnzipService::class.java, JOB_ID, work)
		}
	}
}
