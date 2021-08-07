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

import android.content.*
import android.graphics.Color
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import com.multicraft.game.UnzipService.Companion.enqueueWork
import com.multicraft.game.databinding.ActivityMainBinding
import com.multicraft.game.helpers.Constants.FILES
import com.multicraft.game.helpers.Constants.NO_SPACE_LEFT
import com.multicraft.game.helpers.Constants.REQUEST_CONNECTION
import com.multicraft.game.helpers.Constants.versionName
import com.multicraft.game.helpers.PreferenceHelper
import com.multicraft.game.helpers.PreferenceHelper.TAG_BUILD_VER
import com.multicraft.game.helpers.PreferenceHelper.TAG_LAUNCH_TIMES
import com.multicraft.game.helpers.PreferenceHelper.TAG_SHORTCUT_EXIST
import com.multicraft.game.helpers.PreferenceHelper.getBoolValue
import com.multicraft.game.helpers.PreferenceHelper.getIntValue
import com.multicraft.game.helpers.PreferenceHelper.getStringValue
import com.multicraft.game.helpers.PreferenceHelper.set
import com.multicraft.game.helpers.Utilities.addShortcut
import com.multicraft.game.helpers.Utilities.copyInputStreamToFile
import com.multicraft.game.helpers.Utilities.deleteFiles
import com.multicraft.game.helpers.Utilities.finishApp
import com.multicraft.game.helpers.Utilities.getIcon
import com.multicraft.game.helpers.Utilities.isConnected
import com.multicraft.game.helpers.Utilities.makeFullScreen
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import java.io.File
import java.io.IOException
import java.util.*

class MainActivity : AppCompatActivity() {
	private lateinit var binding: ActivityMainBinding
	private var externalStorage: File? = null
	private lateinit var prefs: SharedPreferences
	private val myReceiver: BroadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent?) {
			var progress = 0
			if (intent != null) progress = intent.getIntExtra(UnzipService.ACTION_PROGRESS, 0)
			if (progress >= 0) {
				showProgress(R.string.loading, R.string.loadingp, progress)
			} else {
				deleteFiles(listOf(FILES), cacheDir)
				if (progress == UnzipService.UNZIP_FAILURE) {
					showRestartDialog(false)
				} else if (progress == UnzipService.UNZIP_SUCCESS) {
					prefs[TAG_BUILD_VER] = versionName
					startNative()
				}
			}
		}
	}
	private var connectionSub: Disposable? = null
	private var cleanSub: Disposable? = null
	private var copySub: Disposable? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
		binding = ActivityMainBinding.inflate(layoutInflater)
		setContentView(binding.root)
		prefs = PreferenceHelper.init(this)
		var storageUnavailable = false
		try {
			externalStorage = getExternalFilesDir(null)
			if (filesDir == null || cacheDir == null || externalStorage == null) throw IOException("Bad disk space state")
		} catch (e: IOException) {
			storageUnavailable = true
			showRestartDialog(e.message!!.contains(NO_SPACE_LEFT))
		}
		if (storageUnavailable) return
		val filter = IntentFilter(UnzipService.ACTION_UPDATE)
		registerReceiver(myReceiver, filter)
		lateInit()
	}

	override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
		@Suppress("DEPRECATION")
		super.onActivityResult(requestCode, resultCode, data)
		if (requestCode == REQUEST_CONNECTION)
			checkAppVersion()
	}

	override fun onBackPressed() {
		// Prevent abrupt interruption when copy game files from assets
	}

	override fun onDestroy() {
		super.onDestroy()
		if (connectionSub != null) connectionSub!!.dispose()
		if (cleanSub != null) cleanSub!!.dispose()
		if (copySub != null) copySub!!.dispose()
		unregisterReceiver(myReceiver)
	}

	override fun onResume() {
		super.onResume()
		makeFullScreen(window)
	}

	override fun onWindowFocusChanged(hasFocus: Boolean) {
		super.onWindowFocusChanged(hasFocus)
		if (hasFocus) makeFullScreen(window)
	}

	private fun addLaunchTimes() {
		val launchTimes = prefs.getIntValue(TAG_LAUNCH_TIMES) + 1
		prefs[TAG_LAUNCH_TIMES] = launchTimes
	}

	// interface
	private fun showProgress(textMessage: Int, progressMessage: Int, progress: Int) {
		if (binding.progressBar.visibility == View.GONE) {
			updateViews(textMessage, View.GONE, View.VISIBLE)
			binding.progressBar.progress = 0
		} else if (progress > 0) {
			binding.tvProgress.text =
				String.format(resources.getString(progressMessage), progress)
			binding.progressBar.progress = progress
			// colorize the progress bar
			val progressDrawable =
				(binding.progressBar.progressDrawable as LayerDrawable).getDrawable(1)
			val color = Color.rgb(255 - progress * 2, progress * 2, 25)
			progressDrawable.colorFilter =
				BlendModeColorFilterCompat.createBlendModeColorFilterCompat(
					color, BlendModeCompat.SRC_IN
				)
		}
	}

	private fun lateInit() {
		addLaunchTimes()
		if (!prefs.getBoolValue(TAG_SHORTCUT_EXIST)) addShortcut(this)
		connectionSub = checkConnection()
	}

	private fun startNative() {
		val intent = Intent(this, GameActivity::class.java)
		intent.flags =
			Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK
		startActivity(intent)
	}

	private fun cleanUpOldFiles() {
		updateViews(R.string.preparing, View.VISIBLE, View.GONE)
		val filesList = listOf(
			File(externalStorage, "cache"),
			File(externalStorage, "debug.txt"),
			File(filesDir, "builtin"),
			File(cacheDir, FILES),
		)
		cleanSub = Completable.fromAction { deleteFiles(filesList) }
			.subscribeOn(Schedulers.io())
			.observeOn(AndroidSchedulers.mainThread())
			.subscribe { startCopy() }
	}

	private fun checkAppVersion() {
		val prefVersion = prefs.getStringValue(TAG_BUILD_VER)
		if (prefVersion == versionName)
			startNative()
		else
			cleanUpOldFiles()
	}

	private fun updateViews(text: Int, progressIndetVisib: Int, progressVisib: Int) {
		binding.tvProgress.setText(text)
		binding.tvProgress.visibility = View.VISIBLE
		binding.progressCircle.visibility = progressIndetVisib
		binding.progressBar.visibility = progressVisib
	}

	// check connection available
	private fun checkConnection() = Observable.fromCallable { isConnected(this) }
		.subscribeOn(Schedulers.io())
		.observeOn(AndroidSchedulers.mainThread())
		.subscribe { result: Boolean ->
			if (result) checkAppVersion() else showConnectionDialog()
		}

	private fun startCopy() {
		val zips = mutableListOf(FILES)
		copySub = Observable.fromCallable { copyAssets(zips) }
			.subscribeOn(Schedulers.io())
			.observeOn(AndroidSchedulers.mainThread())
			.subscribe { result: Boolean -> if (result) startUnzipService(zips) }
	}

	private fun copyAssets(zips: List<String>): Boolean {
		for (zipName in zips) {
			try {
				assets.open("data/$zipName")
					.use { input -> File(cacheDir, zipName).copyInputStreamToFile(input) }
			} catch (e: IOException) {
				runOnUiThread { showRestartDialog(e.message!!.contains(NO_SPACE_LEFT)) }
				return false
			}
		}
		return true
	}

	private fun startUnzipService(file: MutableList<String>) {
		val intent = Intent(this, UnzipService::class.java)
		intent.putStringArrayListExtra(
			UnzipService.EXTRA_KEY_IN_FILE,
			file as ArrayList<String>
		)
		enqueueWork(this, intent)
	}

	private fun showRestartDialog(space: Boolean) {
		val message = if (space) getString(R.string.no_space) else getString(R.string.restart)
		val builder = AlertDialog.Builder(this)
		builder.setMessage(message)
			.setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
				finishApp(!space, this)
			}
			.setCancelable(false)
		val dialog = builder.create()
		makeFullScreen(dialog.window!!)
		if (!isFinishing) dialog.show()
	}

	// connection dialog
	private fun showConnectionDialog() {
		val builder = AlertDialog.Builder(this)
		builder.setIcon(getIcon(this))
			.setTitle(R.string.conn_title)
			.setMessage(R.string.conn_message)
			.setPositiveButton(R.string.conn_wifi) { _: DialogInterface?, _: Int ->
				startHandledActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
			}
			.setNegativeButton(R.string.conn_mobile) { _: DialogInterface?, _: Int ->
				startHandledActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
			}
			.setNeutralButton(R.string.ignore) { _: DialogInterface?, _: Int -> checkAppVersion() }
			.setCancelable(false)
		val dialog = builder.create()
		makeFullScreen(dialog.window!!)
		if (!isFinishing) {
			dialog.show()
			dialog.getButton(DialogInterface.BUTTON_NEUTRAL)?.setTextColor(Color.RED)
		}
	}

	private fun startHandledActivity(intent: Intent) {
		try {
			@Suppress("DEPRECATION")
			startActivityForResult(intent, REQUEST_CONNECTION)
		} catch (e: Exception) {
			checkAppVersion()
		}
	}
}
