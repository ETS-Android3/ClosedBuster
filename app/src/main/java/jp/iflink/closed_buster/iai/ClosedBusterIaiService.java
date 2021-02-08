package jp.iflink.closed_buster.iai;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.WorkerThread;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceManager;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

import jp.co.toshiba.iflink.iai.IfLinkAppIf;
import jp.iflink.closed_buster.FullscreenActivity;
import jp.iflink.closed_buster.R;
import jp.iflink.closed_buster.model.CalculatedSensorData;
import jp.iflink.closed_buster.setting.AppLayoutType;
import jp.iflink.closed_buster.model.SensorData;
import jp.iflink.closed_buster.task.BleScanTask;

public class ClosedBusterIaiService extends IntentService implements IfLinkAppIf.ICallback {
    private static final String SERVICE_NAME = "ClosedBusterIaiService";
    private static final String CHANNEL_ID = "service";
    private static final String CONTENT_TEXT = "サービスを実行中です";
    /** 処理実行のハンドラ. */
    private Handler handler;
    // ブロードキャストマネージャ
    private LocalBroadcastManager broadcastMgr;
    // Iflink App I/F
    private IfLinkAppIf iai;
    // デバイスリスト
    protected List<ClosedBusterIaiDevice> mDeviceList;
    // デバイス
    protected ClosedBusterIaiDevice mDevice;
    // 描画更新間隔 [秒]. */
    private AtomicInteger screenUpdateInterval;
    /** データ送信間隔[秒]. */
    private AtomicInteger sendDataInterval;
    /** データ送信用タイマー. */
    private Timer sendDataTimer;

    // IntentServiceから継承した内部クラス
    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            onHandleIntent((Intent)msg.obj);
            stopSelf(msg.arg1);
        }
    }

    // BLEスキャンタスク
    private BleScanTask bleScanTask;
    // バインダー
    private final IBinder binder = new LocalBinder();
    public class LocalBinder extends Binder {
        public ClosedBusterIaiService getService() {
            return ClosedBusterIaiService.this;
        }
    }

    // ステータス
    private boolean running;
    // 最終送信日時
    private Date lastSendDate;

    /** ログ出力用タグ名 */
    private static final String TAG = "CLOSEDBUSTER-IAI-SRV";
    /** ログ出力レベル：CustomDevice */
    private static final String LOG_LEVEL_CUSTOM_DEV = "CUSTOM-DEV";
    /** ログ出力レベル：CustomIms */
    public static final String LOG_LEVEL_CUSTOM_IMS = "CUSTOM-IMS";
    /** ログ出力切替フラグ */
    private boolean bDBG = false;

    /**
     * コンストラクタ.
     */
    public ClosedBusterIaiService() {
        super(SERVICE_NAME);
        // デバイスリストを初期化
        this.mDeviceList = new ArrayList<>();
    }

    public IfLinkAppIf getIfLinkAppIf(){
        return iai;
    }

    @WorkerThread
    protected void onHandleIntent(@Nullable Intent intent) {
        Log.d(TAG, "onHandleIntent");
        // 処理実行
        execute(intent);
    }

    public void execute(@Nullable Intent intent){
        Log.d(TAG, "service execute");
        // ハンドラー生成
        if (this.handler == null) this.handler = new Handler(Looper.getMainLooper());
        // ブロードキャストマネージャ生成
        if (this.broadcastMgr == null) this.broadcastMgr = LocalBroadcastManager.getInstance(getApplicationContext());

        // アプリ共通設定を取得
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        // リソースを取得
        Resources rsrc = getResources();
        // 設定値の読み込み
        this.screenUpdateInterval = new AtomicInteger(getIntFromString(prefs, "screen_update_interval", rsrc.getInteger(R.integer.default_screen_update_interval)));
        this.sendDataInterval = new AtomicInteger(getIntFromString(prefs, "send_data_interval", rsrc.getInteger(R.integer.default_send_data_interval)));
        // デバイス生成
        mDevice = new ClosedBusterIaiDevice(this);
        mDeviceList.add(mDevice);
        // IAI生成
        if (this.iai == null){
            this.iai = new IfLinkAppIf(getApplicationContext(), this, mDevice.getDeviceName(), mDevice.getDeviceSerial(), mDevice.getAssetName(), mDevice.getSchemaName(), mDevice.getSchema(), mDevice.getCookie());
        }
        mDevice.init(this.iai);
        // IAI開始
        iai.start();
        // デバイス登録開始
        mDevice.createDevice();
        // タスク開始
        startTask(intent, prefs, rsrc);
        // 処理継続ループ
        running = true;
        while (running && (bleScanTask != null)) {
            Thread.yield();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "service create");
        // 設定変更通知用のレシーバーを登録
        LocalBroadcastManager broadcastMgr = LocalBroadcastManager.getInstance(getApplicationContext());
        broadcastMgr.registerReceiver(changeConfigReceiver, new IntentFilter(BleScanTask.ACTION_CHANGE_CONFIG));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "service start");

        if(Build.VERSION.SDK_INT >= 26) {
            // Android8以降の場合、フォアグラウンド開始処理を実施
            startForeground();
        }
        super.onStartCommand(intent, flags, startId);
        // Serviceがkillされたときは再起動する
        // kill後に即起動させる為、START_STICKYを指定（intentはnullになる）
        return START_STICKY;
    }

    @RequiresApi(api = 26)
        private void startForeground(){
        Log.d(TAG, "service start foreground");

        Context context = getApplicationContext();
        Resources rsrc = context.getResources();
        // タイトルを取得
        final String TITLE = rsrc.getString(R.string.app_name);
        // 通知マネージャを生成
        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        // 通知チャンネルを生成
        NotificationChannel channel =
                new NotificationChannel(CHANNEL_ID, TITLE, NotificationManager.IMPORTANCE_DEFAULT);
        if(notificationManager != null) {
            // 通知バーをタップした時のIntentを作成
            Intent notifyIntent = new Intent(context, FullscreenActivity.class);
            notifyIntent.putExtra("fromNotification", true);
            PendingIntent intent = PendingIntent.getActivity(context, 0, notifyIntent, PendingIntent.FLAG_CANCEL_CURRENT);
            // サービス起動の通知を送信
            notificationManager.createNotificationChannel(channel);
            Notification notification = new Notification.Builder(context, CHANNEL_ID)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(TITLE)
                    .setContentText(CONTENT_TEXT)
                    .setContentIntent(intent)
                    .build();
            // フォアグラウンドで実行
            startForeground(1, notification);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "service done");
        // 設定変更通知用のレシーバーを解除
        LocalBroadcastManager broadcastMgr = LocalBroadcastManager.getInstance(getApplicationContext());
        broadcastMgr.unregisterReceiver(changeConfigReceiver);
        // タスクを停止
        stopTask();
        // IAI中断
        iai.stop();
        // 通知を除去
        if(Build.VERSION.SDK_INT >= 26) {
            stopForeground(true);
        }
    }

    public boolean isRunning(){
        return running;
    }

    protected void startTask(Intent intent, SharedPreferences prefs, Resources rsrc){
        // パラメータを取得
        boolean blescan_task;
        if (intent != null){
            blescan_task = intent.getBooleanExtra(BleScanTask.NAME, true);
        } else {
            blescan_task = true;
        }
        Log.d(TAG, "startTask: blescan="+blescan_task+", intent="+intent);
        // 前回のタスクが残っている場合は事前に停止
        stopTask();
        if (blescan_task){
            // BLEスキャンタスクを起動
            this.bleScanTask = new BleScanTask(rsrc);
            boolean success = bleScanTask.init(getApplicationContext(), prefs);
            if (success){
                // BLEスキャン初期化＆開始
                bleScanTask.initScan();
                bleScanTask.startScan();
                new Thread(bleScanTask).start();
            } else {
                bleScanTask = null;
            }
        }
        // デバイスからのデータ送信開始処理
        startSendDataTimer();
    }

    protected void stopTask(){
        running = false;
        try {
            // デバイスからのデータ送信停止処理
            if (sendDataTimer != null) {
                sendDataTimer.cancel();
            }
            // BLEスキャンタスクを停止
            if (bleScanTask != null) {
                this.bleScanTask.stop();
                this.bleScanTask = null;
            }
        } catch (Exception e){
            Log.e(TAG, e.getMessage(), e);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    public BleScanTask getBleScanTask() {
        return this.bleScanTask;
    }

//    protected final void updateLogLevelSettings(final Set<String> settings) {
//        if (bDBG) Log.d(TAG, "LogLevel settings=" + settings);
//
//        boolean isEnabledLog = false;
//        if (settings.contains(LOG_LEVEL_CUSTOM_IMS)) {
//            isEnabledLog = true;
//        }
//        bDBG = isEnabledLog;
//
//        isEnabledLog = settings.contains(LOG_LEVEL_CUSTOM_DEV);
//        for (ClosedBusterIaiDevice device : mDeviceList) {
//            device.enableLogLocal(isEnabledLog);
//        }
//    }

//    protected final String[] getPermissions() {
//        if (bDBG) Log.d(TAG, "getPermissions");
//        // AndroidManifest.xmlのパーミッション
//        List<String> permissions = new ArrayList<>(Arrays.asList(
//                Manifest.permission.BLUETOOTH,
//                Manifest.permission.BLUETOOTH_ADMIN,
//                Manifest.permission.WRITE_EXTERNAL_STORAGE,
//                Manifest.permission.READ_EXTERNAL_STORAGE,
//                Manifest.permission.ACCESS_COARSE_LOCATION
//        ));
//        if(Build.VERSION.SDK_INT >= 26) {
//            permissions.add(Manifest.permission.FOREGROUND_SERVICE);
//        }
//        return permissions.toArray(new String[permissions.size()]);
//    }

    private void startSendDataTimer(){
        // データ送信タイマーを停止
        stopSendDataTimer();
//        // 初回実行時刻（次分の00秒）を取得
//        Calendar startTime = Calendar.getInstance();
//        startTime.add(Calendar.MINUTE, 1);
//        startTime.set(Calendar.SECOND, 0);
//        startTime.set(Calendar.MILLISECOND, 0);
        // データ送信タイマーを再設定
        Calendar startTime = Calendar.getInstance();
        sendDataTimer = new Timer(true);
        // 画面描画更新間隔[秒]で定期実行する
        sendDataTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                handler.post(new Runnable() {
                    public void run() {
                        // BLEスキャンタスク取得
                        BleScanTask bleService = ClosedBusterIaiService.this.getBleScanTask();
                        if (bleService == null){
                            if (bDBG) Log.w(TAG, "bleService is null");
                            return;
                        }
                        // CO2センサー値を取得
                        Map<String, CalculatedSensorData> sensorDataMap = bleService.calculateSensorData();
                        if (!sensorDataMap.isEmpty()){
                            // 画面にデータ送信（broadcast）
                            Intent intent = createSendDataIntent(sensorDataMap, bleService);
                            broadcastMgr.sendBroadcast(intent);
                            // 現在日時の取得
                            Date nowTime = new Date();
                            // データ送信チェック
                            if (lastSendDate != null && nowTime.getTime() - lastSendDate.getTime() < sendDataInterval.get()*1000){
                                // 前回送信時刻からデータ送信間隔[秒]経過していない場合は、データ送信を行わない
                                return;
                            }
                            lastSendDate = nowTime;
                            // 端末識別用IDを取得
                            String unitId = bleService.getUnitId();
// FOR DEMO start --- MaxのCO2濃度のセンサーデータのみ送信する
//                            for (Map.Entry<String, CalculatedSensorData> entry : sensorData.entrySet()){
//                                // センサーデータ取得
//                                final CalculatedSensorData sensorData = entry.getValue();
//                                final String bdAddress = entry.getKey();
//                                int co2 = sensorData.getCo2concentration().intValue();
//                                int temperature = sensorData.getTemperature().intValue();
//                                int humidity = sensorData.getHumidity().intValue();
//                                int motion = sensorData.getMotion().intValue();
//                                // ifLinkにデータ送信
//                                mDevice.sendData(unitId, bdAddress, co2, temperature, humidity, motion);
//                            }
                            int maxCo2 = 0;
                            String bdAddress = null;
                            int temperature = 0;
                            int humidity = 0;
                            int motion = 0;
                            int barometer = 0;
                            for (Map.Entry<String, CalculatedSensorData> entry : sensorDataMap.entrySet()){
                                // センサーデータ取得
                                final CalculatedSensorData sensorData = entry.getValue();
                                // CO2濃度を取得
                                int co2 = sensorData.getCo2concentration();
                                if (co2 >= maxCo2){
                                    maxCo2 = co2;
                                    bdAddress = entry.getKey();
                                    temperature = sensorData.getTemperature();
                                    humidity = sensorData.getHumidity();
                                    motion = sensorData.getMotion();
                                    barometer = sensorData.getBarometer();
                                }
                            }
                            // ifLinkにデータ送信
                            mDevice.sendData(unitId, bdAddress, maxCo2, temperature, humidity, motion, barometer);
// FOR DEMO end --- MaxのCO2濃度のセンサーデータのみ送信する
                        }
                    }
                });
            }
        }, startTime.getTime(), screenUpdateInterval.get() * 1000);
    }

    private void stopSendDataTimer(){
        if (sendDataTimer != null) {
            sendDataTimer.cancel();
        }
    }

    private Intent createSendDataIntent(Map<String, CalculatedSensorData> sensorDataMap, BleScanTask bleService){
        // 最終受信日時
        long lastDataTime = -1L;
        // Intentに追加するセンサー情報を作成
        for (Map.Entry<String, CalculatedSensorData> entry : sensorDataMap.entrySet()){
            CalculatedSensorData sensorData = entry.getValue();
            // 最大の受信日時を取得し、最終受信日時として設定
            if (sensorData.getDatetime() >= lastDataTime){
                lastDataTime = sensorData.getDatetime();
            }
        }
        Intent intent = new Intent(BleScanTask.ACTION_SCAN);
        // センサー情報を追加
        // FIXME: Serializableでは無くParcelableの方が効率が良い
        intent.putExtra("sensorData", (Serializable)sensorDataMap);
        // 未定義のセンサーデータ描画有無を追加
        intent.putExtra("drawUnknownSensor", bleService.isDrawUnknownSensor());
        // 最終受信日時を追加
        intent.putExtra("lastDataTime", lastDataTime);
        // 返却
        return intent;
    }

    /**
     * createDevice結果受信イベントCallback
     * @param name デバイス名
     * @param serial シリアル番号
     * @param result 結果
     * <table summary="結果">
     *     <tr><td>{@code true}</td><td>成功</td></tr>
     *     <tr><td>{@code false}</td><td>失敗</td></tr>
     * </table>
     */
    @Override
    public void onActivationResult(String name, String serial, boolean result) {
        Log.d(TAG, "onActivationResult");
    }

    /**
     * センサ送信結果受信イベントCallback
     * @param name デバイス名
     * @param serial シリアル番号
     * @param result 結果
     * <table summary="結果">
     *     <tr><td>{@code true}</td><td>成功</td></tr>
     *     <tr><td>{@code false}</td><td>失敗</td></tr>
     * </table>
     * @param detail エラー詳細
     */
    @Override
    public void onSensorResult(String name, String serial, boolean result, String detail) {}

    /**
     * JOB受信イベントCallback
     * @param name デバイス名
     * @param serial シリアル番号
     * @param control JOB種別
     * @param params JOBパラメータ
     */
    @Override
    public void onJob(String name, String serial, String control, HashMap<String, String> params) {}

    // 設定変更通知用レシーバ
    private BroadcastReceiver changeConfigReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String config = intent.getStringExtra("CONFIG");
            if (bleScanTask != null && config != null){
                switch(config){
                    // スキャンモード
                    case BleScanTask.CONFIG_SCAN_MODE: {
                        int scan_mode = intent.getIntExtra("VALUE", 0);
                        // モードの変更
                        boolean changed = bleScanTask.changeSettings(scan_mode);
                        // スキャンの再始動
                        if (changed){
                            // スキャンモードが変更された場合は、スキャンを再始動
                            bleScanTask.restartScan();
                        }
                        break;
                    }
                    // アプリのレイアウト種別
                    case BleScanTask.CONFIG_APP_LAYOUT_TYPE: {
                        AppLayoutType app_layout_type = AppLayoutType.judge(intent.getStringExtra("VALUE"));
                        // モードの変更
                        bleScanTask.setAppLayoutType(app_layout_type);
                        break;
                    }
                    // センサーOFF判定時間
                    case BleScanTask.CONFIG_KEEP_DATA_MINUTES: {
                        int keep_data_minutes = intent.getIntExtra("VALUE", 0);
                        // 判定時間の変更
                        bleScanTask.setKeepDataMinutes(keep_data_minutes);
                        break;
                    }
                    // スキャンログを残す
                    case BleScanTask.CONFIG_LOGGING_BLE_SCAN: {
                        boolean logging_ble_scan = intent.getBooleanExtra("VALUE", false);
                        // モードの変更
                        bleScanTask.setLoggingBleScan(logging_ble_scan);
                        break;
                    }
                    // 未定義のセンサーも描画
                    case BleScanTask.CONFIG_DRAW_UNKNOWN_SENSOR: {
                        boolean draw_unknown_sensor = intent.getBooleanExtra("VALUE", false);
                        // モードの変更
                        bleScanTask.setDrawUnknownSensor(draw_unknown_sensor);
                        break;
                    }
                    // 描画更新間隔
                    case BleScanTask.CONFIG_SCREEN_UPDATE_INTERVAL: {
                        int screen_update_interval = intent.getIntExtra("VALUE", 0);
                        // 判定時間の変更
                        screenUpdateInterval.set(screen_update_interval);
                        // タイマーを再設定
                        startSendDataTimer();
                        break;
                    }
                    // データ送信間隔
                    case BleScanTask.CONFIG_SEND_DATA_INTERVAL: {
                        int send_data_interval = intent.getIntExtra("VALUE", 0);
                        // 判定時間の変更
                        sendDataInterval.set(send_data_interval);
                        // タイマーを再設定
                        startSendDataTimer();
                        break;
                    }
                }
            }
        }
    };

    /**
     * 設定受信イベントCallback
     * @param name デバイス名
     * @param serial シリアル番号
     * @param params 設定情報
     */
    @Override
    public void onUpdateConfig(String name, String serial, HashMap<String, String> params) {
        if (bDBG) Log.d(TAG, "onUpdateConfig");
        // リソースを取得
        Resources rsrc = this.getResources();
        // BLEスキャンタスク取得
        BleScanTask bleScanTask = this.getBleScanTask();
        // スキャンモード
        if (bleScanTask != null){
            String p_value = params.get("scan_mode");
            int scan_mode = Integer.parseInt((p_value!=null)? p_value: "0");
            // スキャンモードを変更
            boolean changed = bleScanTask.changeSettings(scan_mode);
            if (changed){
                // スキャンモードが変更された場合は、スキャンを再始動
                bleScanTask.restartScan();
            }
        }
        // アプリのレイアウト種別
        if (bleScanTask != null){
            String p_value = params.get("app_layout_type");
            AppLayoutType app_layout_type = AppLayoutType.judge(p_value);
            bleScanTask.setAppLayoutType(app_layout_type);
        }
        // センサーOFF判定時間
        if (bleScanTask != null){
            String p_value = params.get("keep_data_minutes");
            int value = (p_value!=null)? Integer.parseInt(p_value): rsrc.getInteger(R.integer.default_keep_data_minutes);
            bleScanTask.setKeepDataMinutes(value);
        }
        // アプリケーションのログを残す
        if (bleScanTask != null){
            String p_value = params.get("logging_ble_scan");
            boolean value = (p_value!=null)? Boolean.parseBoolean(p_value) : rsrc.getBoolean(R.bool.default_logging_ble_scan);
            bleScanTask.setLoggingBleScan(value);
        }
        // 未定義のセンサーも描画
        if (bleScanTask != null){
            String p_value = params.get("draw_unknown_sensor");
            boolean value = (p_value!=null)? Boolean.parseBoolean(p_value) : rsrc.getBoolean(R.bool.default_draw_unknown_sensor);
            bleScanTask.setDrawUnknownSensor(value);
        }
        // 描画更新間隔
        if (checkUpdateAndSet(this.screenUpdateInterval, params, "screen_update_interval", rsrc.getInteger(R.integer.default_screen_update_interval))){
            // 変更されていた場合は、タイマーを再設定する
            startSendDataTimer();
        }
        // データ送信間隔
        if (checkUpdateAndSet(this.sendDataInterval, params, "send_data_interval", rsrc.getInteger(R.integer.default_send_data_interval))){
            // 変更されていた場合は、タイマーを再設定する
            startSendDataTimer();
        }
    }

    private boolean checkUpdateAndSet(AtomicInteger current, HashMap<String, String> params, String key, int defaultValue) {
        String param = params.get(key);
        int value= (param != null)? Integer.parseInt(param): defaultValue;

        if (value != current.get()){
            // 更新されていた場合は設定
            current.set(value);
            if (bDBG) Log.d(TAG, "parameter[" + key + "] = " + value);
            return true;
        }
        return false;
    }

    private int getIntFromString(SharedPreferences prefs, String key, int defaultValue){
        String value = prefs.getString(key, String.valueOf(defaultValue));
        return Integer.parseInt(value);
    }
}
