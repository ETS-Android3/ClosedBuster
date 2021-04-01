package jp.iflink.closed_buster.task;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import jp.co.toshiba.iflink.ibi.IbiPacket;
import jp.iflink.closed_buster.R;
import jp.iflink.closed_buster.model.CalculatedSensorData;
import jp.iflink.closed_buster.model.SensorData;
import jp.iflink.closed_buster.model.SensorInfo;
import jp.iflink.closed_buster.setting.AppLayoutType;
import jp.iflink.closed_buster.util.CalculateUtil;
import jp.iflink.closed_buster.util.DataStore;
import jp.iflink.closed_buster.util.LogUseUtil;
import jp.iflink.closed_buster.util.XmlUtil;


public class BleScanTask implements Runnable {
    private static final String TAG = "BLE";
    public static final String ACTION_SCAN = TAG + ".SCAN";
    public static final String ACTION_UNDEFINED_BDADDRESS = TAG+".UNDEFINED_BDADDRESS";
    public static final String ACTION_CHANGE_CONFIG = TAG+".CHANGE_CONFIG";

    public static final String CONFIG_SCAN_MODE = TAG+".CONFIG.SCAN_MODE";
    public static final String CONFIG_APP_LAYOUT_TYPE = TAG+".CONFIG.APP_LAYOUT_TYPE";
    public static final String CONFIG_KEEP_DATA_MINUTES = TAG+".CONFIG.KEEP_DATA_MINUTES";
    public static final String CONFIG_LOGGING_BLE_SCAN = TAG+".CONFIG.LOGGING_BLE_SCAN";
    public static final String CONFIG_DRAW_UNKNOWN_SENSOR = TAG+".CONFIG.DRAW_UNKNOWN_SENSOR";
    public static final String CONFIG_SCREEN_UPDATE_INTERVAL = TAG+".CONFIG.SCREEN_UPDATE_INTERVAL";
    public static final String CONFIG_SEND_DATA_INTERVAL = TAG+".CONFIG.SEND_DATA_INTERVAL";
    public static final String CONFIG_IBI_MEMBER_ID = TAG+".CONFIG.IBI_MEMBER_ID";
    public static final String CONFIG_IBI_MODULE_ID = TAG+".CONFIG.IBI_MODULE_ID";
    public static final String EVENT_FIXED_CHANGE = TAG+".EVENT.FIXED_CHANGE";
    public static final String CONFIG_SENSOR_XML = TAG+".CONFIG.SENSOR_XML";

    public static final String NAME = "BleScan";
    private static final int REQUEST_ENABLE_BT = 1048;

    // コンテキスト
    private Context applicationContext;
    // ブロードキャストマネージャ
    private LocalBroadcastManager broadcastMgr;
    // アプリ共通設定
    private SharedPreferences prefs;
    private boolean loggingBleScan;
    private boolean drawUnknownSensor;
    // アプリレイアウト種別
    private AppLayoutType appLayoutType;
    // センサーデータ保持期間 [分]
    private int KEEP_DATA_MINUTES;

    private BluetoothManager mBtManager;
    private BluetoothAdapter mBtAdapter;
    private BluetoothLeScanner mBTLeScanner;
    private BluetoothGatt mBtGatt;
    private int mStatus;

    private ScanCallback mScanCallback;
    private Date lastScanCallbackTime;
    private Map<String, CalculatedSensorData> mCo2Map = new LinkedHashMap<>();
    private LogUseUtil loguseutil = new LogUseUtil();

    private ScanSettings scSettings;
    private List<ScanFilter> mScanFilters;

    private static final int SUMMARY_TIMER = 5;
    private ScheduledFuture<?> routine1mFuture;
    private ScheduledFuture<?> routine5mFuture;
    private ScheduledExecutorService routineScheduler;
    private Date prevRecordTime;
    // データストア
    private DataStore dataStore;
    // スキャンしたセンサーデータのキュー
    private BlockingQueue<SensorData> scanSensorDataQueue;
    // センサーのBDアドレスリスト
    private List<String> xmlSensorBdAddressList = new ArrayList<>();

    // BLE scan off/onタイマ
    private int ScanRestartCount;
    private static final int SCAN_RESTART_TIMER = 10;
    // スキャン実行状態
    private boolean scanning;

    // IBI Setting Info
    private final int IBI_COMPANY_CODE_IFLINK;
    private int IBI_MEMBER_ID;
    private int IBI_MODULE_ID;
    // IBI Packet Manufacturer Data & Data Mask
    private byte[] IBI_MFRDATA;
    private byte[] IBI_MFRDATA_MASK;

    public BleScanTask(Resources rsrc, SharedPreferences prefs) {
        // IBIパケット設定の読込み
        IBI_COMPANY_CODE_IFLINK = rsrc.getInteger(R.integer.ibi_company_code_iflink);
        IBI_MEMBER_ID = getIntFromString(prefs, "ibi_member_id", rsrc.getInteger(R.integer.default_ibi_member_id));
        IBI_MODULE_ID = getIntFromString(prefs, "ibi_module_id", rsrc.getInteger(R.integer.default_ibi_module_id));
        // Manufacturerデータのフィルタ情報の作成
        byte[] ibiMfrDataMask = null;
        try {
            ibiMfrDataMask = Hex.decodeHex("FFFFFFFFFF".toCharArray());
        } catch (DecoderException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        IBI_MFRDATA_MASK = ibiMfrDataMask;
    }

    private byte[] createIbiManufacturerData(int ibiMemberId, int ibiModuleId){
        // Manufacturerデータのフィルタ情報の作成
        byte[] ibiMfrData = null;
        try {
            ibiMfrData = Hex.decodeHex(String.format("%04X%04X%02X",
                    ibiMemberId,
                    ibiModuleId,
                    IbiPacket.SEND_SENSOR).toCharArray());
        } catch (DecoderException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        return ibiMfrData;
    }

    @Override
    public void run() {
        if (routine1mFuture == null) {
            // データ更新用定期処理（1分間隔）
            int timerDelay = getDelayMillisToNextMinute();
            routine1mFuture = routineScheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "update timer");
                    // 10分でScan Stop/Start
                    ScanRestartCount++;
                    if (ScanRestartCount == SCAN_RESTART_TIMER) {
                        restartScan();
                        ScanRestartCount = 0;
                    }
                }
                //}, timerDelay, 1000*60);
            }, timerDelay, 1000 * 60, TimeUnit.MILLISECONDS);
        }

        if (routine5mFuture == null && appLayoutType != AppLayoutType.SMARTPHONE) {
            // データ集計用定期処理（30秒間隔でトリガーし、5分時で動作）
            // ※スマートフォン版の場合はグラフ書き込み無し
            //int timerDelay = getDelayMillisToNext5Minute();
            int timerDelay = 0;
            routine5mFuture = routineScheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    Date nowTime = new Date();
                    if (!isJustTimeMinuteOf(nowTime, SUMMARY_TIMER)) {
                        // 5分時のみ処理を実行する
                        return;
                    }
                    if (isSameRecordTime(nowTime, prevRecordTime, SUMMARY_TIMER)) {
                        // 同一記録時間帯の場合は、処理実行しない
                        return;
                    }
                    try {
                        // 集計を実施
                        Log.d(TAG, "record data");
                        Date recordTime = new Date();
                        Map<String, CalculatedSensorData> sensorData = calculateSensorData();
                        for (Map.Entry<String, CalculatedSensorData> entry : sensorData.entrySet()) {
                            try {
                                // グラフデータの書き込み
                                dataStore.writeRecord(entry.getKey(), entry.getValue(), recordTime);
                            } catch (IOException e) {
                                Log.e(TAG, e.getMessage(), e);
                            }
                        }
                        // 前回集計時刻を更新
                        prevRecordTime = recordTime;
                    } catch (Resources.NotFoundException e) {
                        Log.e(TAG, e.getMessage(), e);
                        //showMessage(applicationContext.getResources().getString(R.string.data_save_fail));
                    }
                }
                //}, timerDelay, 1000*60);
            }, timerDelay, 1000 * 30, TimeUnit.MILLISECONDS);
        }
    }

    private boolean isJustTimeMinuteOf(Date date, int minute) {
        // 時刻を1分単位の精度にして指定したminute毎かどうかを判定
        return (int) (date.getTime() / 60000) % minute == 0;
    }

    private boolean isSameRecordTime(Date nowTime, Date prevTime, int minute) {
        if (nowTime == null || prevTime == null) {
            return false;
        }
        // 時刻を1分単位＊指定したminute毎の精度にして、同一記録時間帯かどうか判定
        int nowTimeVal = (int) (nowTime.getTime() / (60000 * minute));
        int prevTimeVal = (int) (prevTime.getTime() / (60000 * minute));
        return nowTimeVal == prevTimeVal;
    }

    public boolean init(Context applicationContext, SharedPreferences prefs) {
        // アプリケーションコンテキストを設定
        this.applicationContext = applicationContext;
        // ブロードキャストマネージャを生成
        this.broadcastMgr = LocalBroadcastManager.getInstance(applicationContext);
        // アプリ共通設定を取得
        this.prefs = prefs;
        // BLEステータス更新
        this.mStatus = BluetoothProfile.STATE_DISCONNECTED;

        // Bluetoothマネージャの生成
        if (this.mBtManager == null) {
            this.mBtManager = (BluetoothManager) applicationContext.getSystemService(Context.BLUETOOTH_SERVICE);
            if (this.mBtManager == null) {
                return false;
            }
        }
        // Bluetoothアダプタの生成
        this.mBtAdapter = this.mBtManager.getAdapter();

        // 設定値の読み込み
        Resources rsrc = applicationContext.getResources();
        this.loggingBleScan = getBoolean(prefs, "logging_ble_scan", rsrc.getBoolean(R.bool.default_logging_ble_scan));
        this.appLayoutType = AppLayoutType.judge(prefs.getString("app_layout_type", rsrc.getString(R.string.app_layout_type)));
        this.KEEP_DATA_MINUTES = getIntFromString(prefs, "keep_data_minutes", rsrc.getInteger(R.integer.default_keep_data_minutes));

        // Xmlセンサーリストの読み込み
        reloadXmlSensorBdAddressList(applicationContext);

        // 未定義のセンサーデータ描画有無を取得
        drawUnknownSensor = getBoolean(prefs, "draw_unknown_sensor", rsrc.getBoolean(R.bool.default_draw_unknown_sensor));

        return this.mBtAdapter != null;
    }

    private void close() {
        if (this.mBtGatt == null) {
            return;
        }
        this.mBtGatt.close();
        this.mBtGatt = null;
    }

    public synchronized void reloadXmlSensorBdAddressList(Context context){
        // Xmlセンサーリストの読み込み
        List<SensorInfo> xmlSensorList = XmlUtil.readXml(context);
        // XmlセンサーBDアドレスリストの作成
        xmlSensorBdAddressList.clear();
        for (SensorInfo sensor : xmlSensorList){
            xmlSensorBdAddressList.add(sensor.getBdAddress());
        }
    }

    public void initScan() {
        // データストアの初期化
        this.dataStore = new DataStore(this.applicationContext, this.prefs);
        // タイマーの初期化
        this.routineScheduler = Executors.newScheduledThreadPool(2);
        // リソースの取得
        Resources rsrc = applicationContext.getResources();
        // スキャンモードの取得
        int scan_mode = Integer.parseInt(prefs.getString("scan_mode", rsrc.getString(R.string.default_scan_mode)));
        this.changeSettings(scan_mode);

        this.mScanFilters = new ArrayList<>();
        this.scanSensorDataQueue = new LinkedBlockingQueue<>();

        this.mScanCallback = new ScanCallback() {
            @RequiresApi(api = Build.VERSION_CODES.O)
            @Override
            public void onScanResult(int callbackType, final ScanResult result) {
                lastScanCallbackTime = new Date();
                // ScanResultからセンサー値を取得してキューに追加
                SensorData data = checkAndGetSensorData(result, lastScanCallbackTime);
                if (data != null) {
                    scanSensorDataQueue.add(data);
                }
            }
        };

        this.ScanRestartCount = 0;
    }

    public void stop() {
        Log.d(TAG, "stop()");
        close();
        // スキャンを停止
        stopScan();
        // タイマーを停止
        if (routineScheduler != null) {
            routineScheduler.shutdown();
        }
    }

    // Bluetooth有効化
    public static void enableBluetooth(Activity activity) {
        BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();
        if (btAdapter != null) {
            if (!btAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                activity.startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }
    }

    public BluetoothAdapter getBtAdapter() {
        return this.mBtAdapter;
    }

    public boolean changeSettings(int scan_mode) {
        if (scSettings == null || scSettings.getScanMode() != scan_mode) {
            ScanSettings.Builder scanSettings = new ScanSettings.Builder();
            // スキャンモードの設定
            scanSettings.setScanMode(scan_mode).build();
            scSettings = scanSettings.build();
            return true;
        }
        return false;
    }

    public synchronized void restartScan() {
        Log.d(TAG, "Scan stop and restart");
        // BLEスキャン一時停止
        if (this.scanning) {
            try {
                stopScan();
            } catch (Exception e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
        // BLEスキャンリトライの精度向上の為、一時停止⇒再開までの間に1秒空ける
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Log.e(TAG, e.getMessage(), e);
        }
        // BLEスキャン再開
        try {
            startScan();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    public boolean isScanning() {
        return scanning;
    }

    public synchronized void startScan() {
        if (this.scanning) {
            // 既に開始されている場合は何もしない
            return;
        }
        if (this.mBtAdapter != null) {
            this.mBTLeScanner = this.mBtAdapter.getBluetoothLeScanner();
            if (this.mBTLeScanner != null) {
                Log.d(TAG, "startScan()");
                mScanFilters.clear();
                // Manufacturerデータのフィルタ情報の作成
                IBI_MFRDATA = createIbiManufacturerData(IBI_MEMBER_ID, IBI_MODULE_ID);
                // CO2センサーのIBIパケットのみスキャンするフィルタを追加
                ScanFilter.Builder scanFilter = new ScanFilter.Builder();
                if (IBI_MFRDATA != null && IBI_MFRDATA_MASK != null) {
                    scanFilter.setManufacturerData(IBI_COMPANY_CODE_IFLINK, IBI_MFRDATA, IBI_MFRDATA_MASK);
                }
                mScanFilters.add(scanFilter.build());
                // スキャンを開始
                this.mBTLeScanner.startScan(mScanFilters, scSettings, mScanCallback);
                this.scanning = true;
            }
        } else {
            Log.d(TAG, "BluetoothAdapter :null");
        }
    }

    private synchronized void stopScan() {
        this.scanning = false;
        if (this.mBtAdapter != null) {
            if (this.mBTLeScanner != null) {
                Log.d(TAG, "stopScan()");
                this.mBTLeScanner.stopScan(mScanCallback);
            }
        } else {
            Log.d(TAG, "BluetoothAdapter :null");
        }
    }

    // 一定時間経過したセンサーデータの削除
    private int removeSensorData(Date dateTime, CalculatedSensorData calculatedValue) {
        // センサーデータリストの取り出し
        List<SensorData> sensorDataList = calculatedValue.getSensorDataList();
        String bdAddress = calculatedValue.getBdAddress();
        if (bdAddress == null && !sensorDataList.isEmpty()) {
            bdAddress = sensorDataList.get(0).getBdAddress();
        }
        // ログ差込
        //loguseutil.specific(TAG, "removeSensorData bdAddress="+bdAddress+" size="+sensorDataList.size(), "DEBUG", 3);

        for (Iterator<SensorData> it = sensorDataList.iterator(); it.hasNext(); ) {
            // センサーデータの取り出し
            SensorData sensorData = it.next();

            // 前回の送信時刻からデータ保持時間を過ぎたデータは削除
            if ((KEEP_DATA_MINUTES*1000*60) <= dateTime.getTime() - sensorData.getDatetime()) {
                it.remove();
                //Timestamp sensorTimestamp = new Timestamp(sensorData.getDatetime());
                //Timestamp dateTimestamp = new Timestamp(dateTime.getTime());
                //loguseutil.specific(TAG, "removeSensorData: sensor=" + sensorTimestamp + ",   now=" + dateTimestamp, "DEBUG", Log.DEBUG);
            }
        }

        return sensorDataList.size();
    }

    private SensorData checkAndGetSensorData(ScanResult result, Date scanCallbackTime) {
        // ログ差込
        // //loguseutil.specific(TAG, "BleScanTask checkScanResult：START", "DEBUG", 3);

        BluetoothDevice device = result.getDevice();
        if (device == null || device.getAddress() == null || device.getAddress().isEmpty()) {
            // アドレスが未設定のデータは対象外
            return null;
        }
        if (device.getName() != null && !device.getName().isEmpty()) {
            // デバイス名が設定されているデータは対象外
            return null;
        }
        SparseArray<byte[]> mnfrData = null;
        Integer advertiseFlags = null;
        if (result.getScanRecord() != null) {
            mnfrData = result.getScanRecord().getManufacturerSpecificData();
            advertiseFlags = result.getScanRecord().getAdvertiseFlags();
        } else {
            // スキャンレコードが無いデータは対象外
            return null;
        }
        IbiPacket ibiPacket = getCo2SensorPacket(mnfrData);
        if (ibiPacket != null) {
            // BDアドレスの取得
            String bdAddress = device.getAddress();
            //Log.d(TAG, "co2 sensor: device=" + bdAddress);

            // パラメーターから取得するために生成
            HashMap<String, Object> params = ibiPacket.getParams();

            // CO2濃度の取得
            Number param1 = (Number) params.get("param1");
            //Log.d(TAG, "sensor: param1=" + param1);

            // 気温
            Number param2 = (Number) params.get("param2");
            //Log.d(TAG, "sensor: param2=" + param2);

            // 湿度
            Number param3 = (Number) params.get("param3");
            //Log.d(TAG, "sensor: param3=" + param3);

            // 人感
            Number param4 = (Number) params.get("param4");
            //Log.d(TAG, "sensor: param4=" + param4);

            // 気圧
            Number param5 = (Number) params.get("param5");
            //Log.d(TAG, "sensor: param5=" + param5);

            // Modelを生成
            SensorData sensorData = new SensorData();
            // BDAddressをセット
            sensorData.setBdAddress(bdAddress);
            // 受信時刻をセット
            sensorData.setDatetime(scanCallbackTime.getTime());

            // ※ 以下、画面表示をさせないためにnullの場合は0をセットする（暫定対応）
            // CO2濃度をセット
            if (param1 != null) {
                sensorData.setCo2concentration(param1.intValue());
            } else {
                sensorData.setCo2concentration(0);
            }
            // 気温をセット
            if (param2 != null) {
                sensorData.setTemperature(param2.intValue());
            } else {
                sensorData.setTemperature(0);
            }
            // 湿度をセット
            if (param3 != null) {
                sensorData.setHumidity(param3.intValue());
            } else {
                sensorData.setHumidity(0);
            }
            // 人感をセット
            if (param4 != null) {
                sensorData.setMotion(param4.intValue());
            } else {
                sensorData.setMotion(-1);
            }
            // 気圧をセット
            if (param5 != null) {
                sensorData.setBarometer(param5.intValue());
            } else {
                sensorData.setBarometer(0);
            }

            if (this.loggingBleScan){
                // 取得センサー値のログ出力
                //System.out.println(String.format("sensor: BDAddress=%s CO2=%s Ta=%s RH=%s PIR=%s" , bdAddress, param1, param2, param3, param4, param5));
                String replacedBdAddress = bdAddress.replace(":", "");
                loguseutil.specific(TAG, String.format("sensor: BDAddress=%s CO2=%s Ta=%s RH=%s PIR=%s Pa=%s", bdAddress, param1, param2, param3, param4, param5), replacedBdAddress, Log.INFO);
            }

            return sensorData;
        }

        // ログ差込
        ////loguseutil.specific(TAG, "BleScanTask checkScanResult：END", "DEBUG", 3);

        return null;
    }

    private IbiPacket getCo2SensorPacket(SparseArray<byte[]> mnfrData) {
        byte[] data = mnfrData.get(IBI_COMPANY_CODE_IFLINK);
        if (data != null) {
            IbiPacket packet = new IbiPacket(data);
            if (packet.mMemberId == IBI_MEMBER_ID && packet.mModuleId == IBI_MODULE_ID) {
                return packet;
            }
        }
        return null;
    }

    public Date getLastScanCallbackTime(){
        return lastScanCallbackTime;
    }

    // センサー値の計算
    public synchronized Map<String, CalculatedSensorData> calculateSensorData(){
        Date nowTime = new Date();
        // ログ差込
        //loguseutil.specific(TAG, "getCalculatedValue：START", "DEBUG", 3);

        // スキャンしたセンサー値をキューから取り出して処理
        SensorData scanSensorData;
        while ((scanSensorData = scanSensorDataQueue.poll()) != null){
            // BDアドレスを取得
            String bdAddress = scanSensorData.getBdAddress();
            // 集計済センサーデータを取得
            CalculatedSensorData calculated = mCo2Map.get(bdAddress);
            if (calculated == null) {
                // 無い場合は生成して追加
                calculated = new CalculatedSensorData();
                mCo2Map.put(bdAddress, calculated);
            }
            // Listを取得
            List<SensorData> sensorDataList = calculated.getSensorDataList();
            // Listに取得したセンサーデータを追加
            sensorDataList.add(scanSensorData);
        }

        // 一定時間経過したセンサーデータの削除
        for (Iterator<Map.Entry<String, CalculatedSensorData>> it = mCo2Map.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, CalculatedSensorData> entry = it.next();
            int size = removeSensorData(nowTime, entry.getValue());
            if (size == 0) {
                it.remove();
                // ログ差込
                //loguseutil.specific(TAG, "removeAllSensorData bdAddress="+entry.getKey(), "DEBUG", 3);
            }
        }

        // 返却用インスタンス
        Map<String, CalculatedSensorData> result = new LinkedHashMap<>(mCo2Map.size());
        // 未定義のBDアドレスリスト
        List<String> sendBdAddressList = new ArrayList<>();

        for (Iterator<Map.Entry<String, CalculatedSensorData>> it = mCo2Map.entrySet().iterator(); it.hasNext(); ){
            Map.Entry<String, CalculatedSensorData> entry = it.next();
            String bdAddress = entry.getKey();
            CalculatedSensorData calculated = entry.getValue();
            List<SensorData> sensorDataList = calculated.getSensorDataList();
            if(sensorDataList != null && !sensorDataList.isEmpty()){
                // センサーデータから代表値を計算して取得
                SensorData sensorData = CalculateUtil.getSensorData(sensorDataList);
                // センサーデータに代表値を設定
                calculated.setCalculatedSensorData(sensorData, new Date());
            }
            if (!xmlSensorBdAddressList.contains(bdAddress)){
                // 未定義のBDアドレスのセンサーデータは削除
                it.remove();
                // 未定義のBDアドレスリストに追加
                sendBdAddressList.add(bdAddress);
                if (drawUnknownSensor){
                    // 未定義のセンサーデータを描画する場合のみ、返却対象に追加
                    result.put(bdAddress, calculated);
                }
            } else {
                // 返却対象に追加
                result.put(bdAddress, calculated);
            }
        }

        if (!sendBdAddressList.isEmpty()){
            // 未定義のBDアドレス配列を通知
            Intent intent = new Intent(ACTION_UNDEFINED_BDADDRESS);
            String[] sendBdAddresses = sendBdAddressList.toArray(new String[sendBdAddressList.size()]);
            intent.putExtra("bdAddresses", sendBdAddresses);
            broadcastMgr.sendBroadcast(intent);
        }

        // ログ差込
        //loguseutil.specific(TAG, "getCalculatedValue：END", "DEBUG", 3);

        // 計算後のセンサーデータを返却
        return result;
    }

    public void setAppLayoutType(AppLayoutType type){
        this.appLayoutType = type;
    }

    public void setKeepDataMinutes(int minutes){
        this.KEEP_DATA_MINUTES = minutes;
    }

    public void setLoggingBleScan(boolean check){
        this.loggingBleScan = check;
    }

    public void setDrawUnknownSensor(boolean check){
        this.drawUnknownSensor = check;
    }

    public boolean setIbiMemberId(int ibiMemberId){
        if (IBI_MEMBER_ID != ibiMemberId){
            IBI_MEMBER_ID = ibiMemberId;
            return true;
        }
        return false;
    }

    public boolean setIbiModuleId(int ibiModuleId){
        if (IBI_MODULE_ID != ibiModuleId){
            IBI_MODULE_ID = ibiModuleId;
            return true;
        }
        return false;
    }

    public String getUnitId(){
        if (prefs == null){
            return "";
        }
        return prefs.getString("unit_id", "");
    }

    private int getDelayMillisToNextMinute(){
        Calendar nextTime = Calendar.getInstance();
        nextTime.set(Calendar.SECOND, 0);
        nextTime.set(Calendar.MILLISECOND, 0);
        nextTime.add(Calendar.MINUTE, 1);
        return (int)(nextTime.getTime() .getTime() - new Date().getTime());
    }

    private int getDelayMillisToNext5Minute(){
        Calendar nextTime = Calendar.getInstance();
        final int minute = nextTime.get(Calendar.MINUTE);
        nextTime.set(Calendar.SECOND, 0);
        nextTime.set(Calendar.MILLISECOND, 0);
        nextTime.set(Calendar.MINUTE, ((minute/5)+1)*5);
        return (int)(nextTime.getTime().getTime() - new Date().getTime());
    }

    private void showMessage(final String message){
        if(this.applicationContext != null) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                public void run() {
                    Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private static int getCompanyFromSpArray(SparseArray<byte[]> data){
        if (data != null && data.size()>0){
            return data.keyAt(0);
        }
        return -1;
    }

    private static String spArrayToStr(SparseArray<byte[]> data){
        if (data == null){
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        if (data.size()>0){
            for (int idx=0; idx<data.size(); idx++){
                int key = data.keyAt(idx);
                byte[] array = data.valueAt(idx);
                sb.append(key).append("=0x").append(binToHexStr(array)).append(",");
            }
            sb.setLength(sb.length()-",".length());
        }
        sb.append("}");
        return sb.toString();
    }

    private static String binToHexStr(byte[] array){
        StringBuilder sb = new StringBuilder();
        for (byte d : array) {
            sb.append(String.format("%02X", d));
        }
        return sb.toString();
    }

    private int getIntFromString(SharedPreferences prefs, String key, int defaultValue){
        String value = prefs.getString(key, String.valueOf(defaultValue));
        return Integer.parseInt(value);
    }

    private boolean getBoolean(SharedPreferences prefs, String key, boolean defaultValue){
        boolean value;
        try {
            value = prefs.getBoolean(key, defaultValue);
        } catch (ClassCastException e){
            value = Boolean.valueOf(prefs.getString(key, String.valueOf(defaultValue)));
        }
        return value;
    }

    public boolean isDrawUnknownSensor(){
        return drawUnknownSensor;
    }

}
