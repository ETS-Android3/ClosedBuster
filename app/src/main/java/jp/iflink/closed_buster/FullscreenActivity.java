package jp.iflink.closed_buster;

import android.Manifest;
import android.annotation.SuppressLint;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.navigation.NavController;
import androidx.navigation.NavGraph;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;
import androidx.preference.PreferenceManager;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

import jp.iflink.closed_buster.iai.ClosedBusterIaiService;
import jp.iflink.closed_buster.model.SensorData;
import jp.iflink.closed_buster.task.BleScanTask;
import jp.iflink.closed_buster.ui.ISensorFragment;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity {
    private static final String TAG = "Main";

    // アプリ共通設定
    private SharedPreferences prefs;
    // フラグメントマネージャ
    private FragmentManager fragmentManager;
    // データ送信先フラグメント
    private ISensorFragment sensorFragment;
    // Appバー構成設定
    private AppBarConfiguration mAppBarConfig;

    // アプリの必要権限
    String[] permissions = new String[] {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fullscreen);
        mVisible = true;
        // アプリ共通設定を取得
        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        Resources rsrc = getResources();

        mContentView = findViewById(R.id.fullscreen_content);
        fragmentManager = getSupportFragmentManager();

        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);

        Log.d(TAG, "onCreate：navController start");
        NavGraph navGraph = navController.getNavInflater() .inflate(R.navigation.nav_graph);

        // 起動時のフラグメントを指定
        navGraph.setStartDestination(R.id.HomeFragment);
        navController.setGraph(navGraph);

        mAppBarConfig = new AppBarConfiguration.Builder(navController.getGraph()).build();
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfig);

        // Android6.0以降の場合、権限を要求
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermission();
        }
        // BLEスキャン用のレシーバーを登録
        LocalBroadcastManager broadcastMgr = LocalBroadcastManager.getInstance(getApplicationContext());
        broadcastMgr.registerReceiver(bleScanReceiver, new IntentFilter(BleScanTask.ACTION_SCAN));

        // BluetoothをONにする
        BleScanTask.enableBluetooth(this);

        // サービスを開始する
        startMicroService();
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "MainActivity onDestroy");
        // BLEスキャン用のレシーバーを解除
        LocalBroadcastManager broadcastMgr = LocalBroadcastManager.getInstance(getApplicationContext());
        broadcastMgr.unregisterReceiver(bleScanReceiver);
//        // メインサービスのアンバインド
//        unbindService(mServiceConnection);
        // バックグラウンド動作判定
        Resources rsrc = getResources();
        boolean runInBackground = prefs.getBoolean("runin_background", rsrc.getBoolean(R.bool.default_runin_background));
        if (!runInBackground){
            // バックグラウンド動作しない場合、メインサービスを停止
            Intent serviceIntent = new Intent(this, ClosedBusterIaiService.class);
            stopService(serviceIntent);
        }
    }

    private void startMicroService(){
        // サービスの開始
        Intent serviceIntent = new Intent(this, ClosedBusterIaiService.class);
        serviceIntent.putExtra(BleScanTask.NAME, true);

        ComponentName service;
        if (Build.VERSION.SDK_INT >= 26) {
            // Android8以降の場合
            service = startForegroundService(serviceIntent);
        } else {
            // Android7以前の場合
            service = startService(serviceIntent);
        }
        if (service != null){
            // 既にサービスが起動済みの場合は、サービスにバインドする
            //bindService(serviceIntent, mServiceConnection, 0);
            Log.d(TAG, "The service has already been started.");
        }
    }

    // BLEスキャン用レシーバ
    private BroadcastReceiver bleScanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
//            // メインサービスにバインド
//            Intent serviceIntent = new Intent(FullscreenActivity.this, ClosedBusterIms.class);
//            bindService(serviceIntent, mServiceConnection, 0);
            // フラグメントを未取得or破棄からの再取得の場合、データ送信先フラグメントを取得する
            if (isDetachedFragment(sensorFragment)){
                sensorFragment = findFragment(ISensorFragment.class);
            }
            if (sensorFragment != null){
                Map<String, SensorData> sensorDataMap = (Map<String, SensorData>)intent.getSerializableExtra("sensorData");
                if (sensorDataMap != null){
                    // 未定義のセンサーデータ描画有無を取得
                    boolean drawUnknownSensor = intent.getBooleanExtra("drawUnknownSensor", false);
                    // 最終受信日時を取得
                    Date lastDataTime = new Date(intent.getLongExtra("lastDataTime", -1L));
                    // センサーデータを描画
                    sensorFragment.drawSensorData(sensorDataMap, lastDataTime, drawUnknownSensor);
                }
            }
        }
    };

    private boolean isDetachedFragment(ISensorFragment sensorFragment){
        if (sensorFragment == null){
            return true;
        }
        Fragment fragment = (Fragment)sensorFragment;
        return fragment.isDetached() || fragment.getActivity() == null;
    }


    private <FRG> FRG findFragment(Class<FRG> clazz){
        // NavHostFragmentから検索して取得する
        NavHostFragment navHost = (NavHostFragment) fragmentManager.findFragmentById(R.id.nav_host_fragment);
        if (navHost != null){
            for (Fragment fragment : navHost.getChildFragmentManager().getFragments()){
                if (clazz.isInstance(fragment)){
                    return (FRG)fragment;
                }
            }
        }
        return null;
    }

//    private final ServiceConnection mServiceConnection = new ServiceConnection() {
//        @Override
//        public void onServiceConnected(ComponentName componentname, IBinder binder) {
//            Log.d(TAG, "MainActivity onServiceConnected");
//            // サービス取得
//            ClosedBusterIms mainService = ((ClosedBusterIms.LocalBinder) binder).getService();
//            // BLEスキャンタスク取得
//            BleScanTask bleScanTask = mainService.getBleScanTask();
//            if (bleScanTask != null){
//                // Bluetooth有効化
//                bleScanTask.enableBluetooth(FullscreenActivity.this);
//                // BLEスキャン開始
//                bleScanTask.startScan();
//            }
//            // フラグメントにサービスを設定
//            NavHostFragment navHost = (NavHostFragment) fragmentManager.findFragmentById(R.id.nav_host_fragment);
//            if (navHost != null){
//                for (Fragment fragment : navHost.getChildFragmentManager().getFragments()){
//                    if (fragment instanceof IServiceFragment){
//                        ((IServiceFragment)fragment).setService(mainService);
//                    }
//                }
//            }
//        }
//
//        @Override
//        public void onServiceDisconnected(ComponentName componentname) {
//            Log.d(TAG, "MainActivity onServiceDisconnected");
//            // フラグメントのサービスをクリア
//            NavHostFragment navHost = (NavHostFragment) fragmentManager.findFragmentById(R.id.nav_host_fragment);
//            if (navHost != null){
//                for (Fragment fragment : navHost.getChildFragmentManager().getFragments()){
//                    if (fragment instanceof IServiceFragment){
//                        ((IServiceFragment)fragment).setService(null);
//                    }
//                }
//            }
//        }
//    };

    @TargetApi(Build.VERSION_CODES.M)
    private void requestPermission() {
        ArrayList<String> list = new ArrayList<>();

        for (String permission : permissions) {
            int check = ContextCompat.checkSelfPermission(getBaseContext(), permission);
            if (check != PackageManager.PERMISSION_GRANTED) {
                list.add(permission);
            }
        }
        if (!list.isEmpty()) {
            requestPermissions(list.toArray(new String[list.size()]), 1);
        }
    }

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 3000;
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        //mHideHandler.removeCallbacks(mShowPart2Runnable);
        //mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;


        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }

    @Override
    public void onStart() {
        super.onStart();
        //Log.d(TAG, "MainActivity onStart");
    }

    @Override
    public void onStop() {
        super.onStop();
        //Log.d(TAG, "MainActivity onStop");
    }

    private void showMessage(final String message){
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
