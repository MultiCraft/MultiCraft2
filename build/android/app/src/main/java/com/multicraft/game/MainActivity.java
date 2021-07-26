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

package com.multicraft.game;

import static android.content.DialogInterface.BUTTON_NEUTRAL;
import static android.provider.Settings.ACTION_WIFI_SETTINGS;
import static android.provider.Settings.ACTION_WIRELESS_SETTINGS;
import static android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
import static com.multicraft.game.UnzipService.ACTION_FAILURE;
import static com.multicraft.game.UnzipService.UNZIP_FAILURE;
import static com.multicraft.game.UnzipService.UNZIP_SUCCESS;
import static com.multicraft.game.helpers.Constants.FILES;
import static com.multicraft.game.helpers.Constants.NO_SPACE_LEFT;
import static com.multicraft.game.helpers.Constants.REQUEST_CONNECTION;
import static com.multicraft.game.helpers.Constants.versionName;
import static com.multicraft.game.helpers.PreferencesHelper.TAG_BUILD_NUMBER;
import static com.multicraft.game.helpers.PreferencesHelper.TAG_LAUNCH_TIMES;
import static com.multicraft.game.helpers.Utilities.addShortcut;
import static com.multicraft.game.helpers.Utilities.copyInputStreamToFile;
import static com.multicraft.game.helpers.Utilities.deleteFiles;
import static com.multicraft.game.helpers.Utilities.finishApp;
import static com.multicraft.game.helpers.Utilities.getIcon;
import static com.multicraft.game.helpers.Utilities.isConnected;
import static com.multicraft.game.helpers.Utilities.makeFullScreen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.BlendModeColorFilterCompat;
import androidx.core.graphics.BlendModeCompat;

import com.multicraft.game.helpers.PreferencesHelper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public class MainActivity extends AppCompatActivity {
    private ProgressBar mProgressBar, mProgressBarIndet;
    private TextView mLoadingText;
    private PreferencesHelper pf;
    private final BroadcastReceiver myReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int progress = 0;
            if (intent != null)
                progress = intent.getIntExtra(UnzipService.ACTION_PROGRESS, 0);
            if (progress >= 0) {
                showProgress(R.string.loading, R.string.loadingp, progress);
            } else {
                deleteFiles(Collections.singletonList(FILES), getCacheDir());
                if (progress == UNZIP_FAILURE) {
                    String msg = intent.getStringExtra(ACTION_FAILURE);
                    showRestartDialog(msg);
                } else if (progress == UNZIP_SUCCESS) {
                    pf.saveSettings(TAG_BUILD_NUMBER, versionName);
                    startNative();
                }
            }
        }
    };
    private File externalStorage, filesDir, cacheDir;
    private Disposable cleanSub, copySub, connectionSub;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(FLAG_KEEP_SCREEN_ON);
        try {
            getSupportActionBar().hide();
        } catch (Exception ignored) {
        }
        setContentView(R.layout.activity_main);
        mLoadingText = findViewById(R.id.tv_progress);
        mProgressBar = findViewById(R.id.PB);
        mProgressBarIndet = findViewById(R.id.PB_Indet);
        boolean isException = false;
        try {
            filesDir = getFilesDir();
            cacheDir = getCacheDir();
            externalStorage = getExternalFilesDir(null);
            if (filesDir == null || cacheDir == null || externalStorage == null)
                throw new IOException(getString(R.string.space_error));
        } catch (IOException e) {
            isException = true;
            String msg = getString(R.string.restart, e.getLocalizedMessage());
            if (e.getMessage().contains(NO_SPACE_LEFT)) {
                msg = NO_SPACE_LEFT;
            }
            showRestartDialog(msg);
        }
        if (isException) {
            return;
        }
        pf = PreferencesHelper.getInstance(this);
        IntentFilter filter = new IntentFilter(UnzipService.ACTION_UPDATE);
        registerReceiver(myReceiver, filter);
        lateInit();
    }

    @Override
    public void onBackPressed() {
        // Prevent abrupt interruption when copy game files from assets
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cleanSub != null) cleanSub.dispose();
        if (copySub != null) copySub.dispose();
        if (connectionSub != null) connectionSub.dispose();
        unregisterReceiver(myReceiver);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CONNECTION) {
            checkAppVersion();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        makeFullScreen(getWindow());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) makeFullScreen(getWindow());
    }

    private void addLaunchTimes() {
        int launchTimes = pf.getLaunchTimes() + 1;
        pf.saveSettings(TAG_LAUNCH_TIMES, launchTimes);
    }

    // interface
    private void showProgress(int textMessage, int progressMessage, int progress) {
        if (mProgressBar == null) return;
        if (mProgressBar.getVisibility() == View.GONE) {
            updateViews(textMessage, View.GONE, View.VISIBLE);
            mProgressBar.setProgress(0);
        } else if (progress > 0) {
            mLoadingText.setText(String.format(getResources().getString(progressMessage), progress));
            mProgressBar.setProgress(progress);
            // colorize the progress bar
            Drawable progressDrawable = ((LayerDrawable)
                    mProgressBar.getProgressDrawable()).getDrawable(1);
            int color = Color.rgb(255 - progress * 2, progress * 2, 25);
            progressDrawable.setColorFilter(
                    BlendModeColorFilterCompat.createBlendModeColorFilterCompat(color, BlendModeCompat.SRC_IN));
        }
    }

    private void lateInit() {
        addLaunchTimes();
        if (!pf.isCreateShortcut()) addShortcut(this);
        connectionSub = checkConnection();
    }

    // check connection available
    private Disposable checkConnection() {
        return Observable.fromCallable(() -> isConnected(this))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                            if (result) checkAppVersion();
                            else showConnectionDialog();
                        },
                        throwable -> runOnUiThread(this::showConnectionDialog));
    }

    // connection dialog
    private void showConnectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(getIcon(this))
                .setTitle(R.string.conn_title)
                .setMessage(R.string.conn_message)
                .setPositiveButton(R.string.conn_wifi, (dialogInterface, i) -> startHandledActivity(new Intent(ACTION_WIFI_SETTINGS)))
                .setNegativeButton(R.string.conn_mobile, (dialogInterface, i) -> startHandledActivity(new Intent(ACTION_WIRELESS_SETTINGS)))
                .setNeutralButton(R.string.ignore, (dialogInterface, i) -> checkAppVersion())
                .setCancelable(false);
        final AlertDialog dialog = builder.create();
        makeFullScreen(dialog.getWindow());
        if (!isFinishing()) {
            dialog.show();
            Button button = dialog.getButton(BUTTON_NEUTRAL);
            if (button != null) button.setTextColor(Color.RED);
        }
    }

    private void startHandledActivity(Intent intent) {
        try {
            startActivityForResult(intent, REQUEST_CONNECTION);
        } catch (Exception e) {
            checkAppVersion();
        }
    }

    private void startNative() {
        Intent intent = new Intent(this, GameActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void cleanUpOldFiles() {
        updateViews(R.string.preparing, View.VISIBLE, View.GONE);
        List<File> filesList = Arrays.asList(
                new File(externalStorage, "cache"),
                new File(externalStorage, "debug.txt"),
                new File(filesDir, "builtin"),
                new File(cacheDir, FILES)
        );
        Completable delObs = Completable.fromAction(() -> deleteFiles(filesList));
        cleanSub = delObs.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(this::startCopy);
    }

    private void checkAppVersion() {
        String prefVersion;
        try {
            prefVersion = pf.getBuildNumber();
        } catch (ClassCastException e) {
            prefVersion = "1";
        }

        if (prefVersion.equals(versionName))
            startNative();
        else
            cleanUpOldFiles();
    }

    private void updateViews(int text, int progressIndetVisib, int progressVisib) {
        mLoadingText.setText(text);
        mLoadingText.setVisibility(View.VISIBLE);
        mProgressBarIndet.setVisibility(progressIndetVisib);
        mProgressBar.setVisibility(progressVisib);
    }

    private void startCopy() {
        List<String> zips = new ArrayList<>(Collections.singletonList(FILES));

        copySub = Observable.fromCallable(() -> copyAssets(zips))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(result -> {
                    if (result) startUnzipService(zips);
                });
    }

    private boolean copyAssets(List<String> zips) {
        for (String zipName : zips) {
            try (InputStream in = getAssets().open("data/" + zipName)) {
                copyInputStreamToFile(new File(cacheDir, zipName), in);
            } catch (IOException e) {
                if (e.getLocalizedMessage().contains(NO_SPACE_LEFT))
                    runOnUiThread(() -> showRestartDialog(NO_SPACE_LEFT));
                else {
                    runOnUiThread(() -> showRestartDialog(e.getLocalizedMessage()));
                }
                return false;
            }
        }
        return true;
    }

    private void startUnzipService(List<String> file) {
        Intent intent = new Intent(this, UnzipService.class);
        intent.putStringArrayListExtra(UnzipService.EXTRA_KEY_IN_FILE, (ArrayList<String>) file);
        UnzipService.enqueueWork(this, intent);
    }

    private void showRestartDialog(final String source) {
        boolean space = NO_SPACE_LEFT.equals(source);
        String message = space ? getString(R.string.no_space) : source;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setPositiveButton(R.string.ok, (dialogInterface, i) -> finishApp(!space, this))
                .setCancelable(false);
        final AlertDialog dialog = builder.create();
        makeFullScreen(dialog.getWindow());
        if (!isFinishing())
            dialog.show();
    }
}
