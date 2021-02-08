package jp.iflink.closed_buster.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import jp.iflink.closed_buster.R;
import jp.iflink.closed_buster.iai.ClosedBusterIaiService;
import jp.iflink.closed_buster.task.BleScanTask;

public class SettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = "Settings";
    /** ログ出力切替フラグ. */
    private boolean bDBG = false;
    // ブロードキャストマネージャ
    private LocalBroadcastManager broadcastMgr;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // ブロードキャストマネージャを生成
        this.broadcastMgr = LocalBroadcastManager.getInstance(getContext());
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Log.d(TAG, "onCreatePreferences");

        // 縦画面に固定しない
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);

        // SwitchPreferenceCompat設定項目値がString型になっていた場合、boolean型に変換する
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        Map<String,?> prefMap = prefs.getAll();
        Map<String,Boolean> newValueMap = new HashMap<>();
        // boolean型設定項目に対して処理
        for (String key : new String[]{"runin_background","logging_ble_scan","draw_unknown_sensor"}){
            Object value = prefMap.get(key);
            if (value instanceof String){
                newValueMap.put(key, Boolean.valueOf((String)value));
            }
        }
        if (!newValueMap.isEmpty()){
            SharedPreferences.Editor editor = prefs.edit();
            for (Map.Entry<String,Boolean> entry : newValueMap.entrySet()){
                editor.putBoolean(entry.getKey(), entry.getValue());
            }
            editor.apply();
            editor.commit();
        }
        // 設定ファイル読み込み
        setPreferencesFromResource(R.xml.preferences, rootKey);
        // listenerの登録
        prefs.registerOnSharedPreferenceChangeListener(this);
        // inputTypeを設定
        setupInputType("co2_high_ppm", InputType.TYPE_CLASS_NUMBER);
        setupInputType("co2_low_ppm", InputType.TYPE_CLASS_NUMBER);
        setupInputType("screen_update_interval", InputType.TYPE_CLASS_NUMBER);
        setupInputType("motion_off_minutes", InputType.TYPE_CLASS_NUMBER);
        setupInputType("keep_data_minutes", InputType.TYPE_CLASS_NUMBER);
        setupInputType("send_data_interval", InputType.TYPE_CLASS_NUMBER);
        // デバッグフラグの設定
        bDBG = getDebugFlag(prefs);
    }

    private void setupInputType(final String key, final int inputType){
        EditTextPreference pref = findPreference(key);
        if (pref != null) {
            pref.setOnBindEditTextListener(new EditTextPreference.OnBindEditTextListener() {
                @Override
                public void onBindEditText(@NonNull EditText editText) {
                    editText.setInputType(inputType);
                }
             });
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        Log.d(TAG, "onSharedPreferenceChanged: key="+key);
        // 設定の変更通知
        switch(key){
            // スキャンモード
            case "scan_mode": {
                int scan_mode = Integer.parseInt(prefs.getString(key, "0"));
                Intent intent = new Intent(BleScanTask.ACTION_CHANGE_CONFIG);
                intent.putExtra("CONFIG", BleScanTask.CONFIG_SCAN_MODE);
                intent.putExtra("VALUE", scan_mode);
                broadcastMgr.sendBroadcast(intent);
                break;
            }
            // アプリのレイアウト種別
            case "app_layout_type": {
                String app_layout_type = prefs.getString(key, "");
                Intent intent = new Intent(BleScanTask.ACTION_CHANGE_CONFIG);
                intent.putExtra("CONFIG", BleScanTask.CONFIG_APP_LAYOUT_TYPE);
                intent.putExtra("VALUE", app_layout_type);
                broadcastMgr.sendBroadcast(intent);
                break;
            }
            // センサーOFF判定時間
            case "keep_data_minutes": {
                int keep_data_minutes = Integer.parseInt(prefs.getString(key, "0"));
                Intent intent = new Intent(BleScanTask.ACTION_CHANGE_CONFIG);
                intent.putExtra("CONFIG", BleScanTask.CONFIG_KEEP_DATA_MINUTES);
                intent.putExtra("VALUE", keep_data_minutes);
                broadcastMgr.sendBroadcast(intent);
                break;
            }
            // スキャンログを残す
            case "logging_ble_scan": {
                boolean logging_ble_scan = prefs.getBoolean(key, false);
                Intent intent = new Intent(BleScanTask.ACTION_CHANGE_CONFIG);
                intent.putExtra("CONFIG", BleScanTask.CONFIG_LOGGING_BLE_SCAN);
                intent.putExtra("VALUE", logging_ble_scan);
                broadcastMgr.sendBroadcast(intent);
                break;
            }
            // 未定義のセンサーも描画
            case "draw_unknown_sensor": {
                boolean draw_unknown_sensor = prefs.getBoolean(key, false);
                Intent intent = new Intent(BleScanTask.ACTION_CHANGE_CONFIG);
                intent.putExtra("CONFIG", BleScanTask.CONFIG_DRAW_UNKNOWN_SENSOR);
                intent.putExtra("VALUE", draw_unknown_sensor);
                broadcastMgr.sendBroadcast(intent);
                break;
            }
            // 描画更新間隔
            case "screen_update_interval": {
                int screen_update_interval = Integer.parseInt(prefs.getString(key, "0"));
                Intent intent = new Intent(BleScanTask.ACTION_CHANGE_CONFIG);
                intent.putExtra("CONFIG", BleScanTask.CONFIG_SCREEN_UPDATE_INTERVAL);
                intent.putExtra("VALUE", screen_update_interval);
                broadcastMgr.sendBroadcast(intent);
                break;
            }
            // データ送信間隔
            case "send_data_interval": {
                int send_data_interval = Integer.parseInt(prefs.getString(key, "0"));
                Intent intent = new Intent(BleScanTask.ACTION_CHANGE_CONFIG);
                intent.putExtra("CONFIG", BleScanTask.CONFIG_SEND_DATA_INTERVAL);
                intent.putExtra("VALUE", send_data_interval);
                broadcastMgr.sendBroadcast(intent);
                break;
            }
            default:
                break;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    private int getIntFromString(SharedPreferences prefs, String key, int defaultValue){
        String value = prefs.getString(key, String.valueOf(defaultValue));
        return Integer.parseInt(value);
    }

    private boolean getDebugFlag(SharedPreferences prefs){
        Set<String> loglevelSet  = prefs.getStringSet("loglevel", null);
        return loglevelSet != null && loglevelSet.contains(ClosedBusterIaiService.LOG_LEVEL_CUSTOM_IMS);
    }
}
