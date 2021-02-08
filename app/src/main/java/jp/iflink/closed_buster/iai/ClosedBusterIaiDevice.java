package jp.iflink.closed_buster.iai;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.os.Looper;
import android.os.Handler;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;
import android.widget.Toast;

import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;

import jp.co.toshiba.iflink.iai.IfLinkAppIf;
import jp.co.toshiba.iflink.imsif.SchemaXmlParser;
import jp.iflink.closed_buster.BuildConfig;
import jp.iflink.closed_buster.R;
import jp.iflink.closed_buster.task.BleScanTask;

public class ClosedBusterIaiDevice {
    /** ログ出力用タグ名. */
    private static final String TAG = "CLOSEDBUSTER-IAI-DEV";
    /** ログ出力切替フラグ. */
    private boolean bDBG = false;
    // サービス参照
    private ClosedBusterIaiService mService;
    // ifLinkデバイス情報
    private String mDeviceName;
    private String mDeviceSerial;
    private String mSchemaName;
    private String mSchema;
    private String mCookie;
    private String mAssetName;
    // Iflink App I/F
    private IfLinkAppIf iai;

//    // 最新のスキャンコールバック時刻
//    private Date lastScanCallbackTime;

    /**
     * コンストラクタ.
     *
     * @param service サービス
     */
    public ClosedBusterIaiDevice(final ClosedBusterIaiService service) {
        this.mService = service;
        // リソースを取得
        Resources rsrc = service.getResources();
        // デバイス情報を設定
        mDeviceName = BuildConfig.APPLICATION_ID+":"+rsrc.getString(R.string.ims_device_name_class)+":"+BuildConfig.VERSION_CODE;
        mDeviceSerial = rsrc.getString(R.string.ims_device_serial);
        mSchemaName = rsrc.getString(R.string.ims_schema_name);
        // スキーマ情報を設定
        mSchema = loadSchema();
        // クッキー情報を設定
        mCookie = IfLinkAppIf.generateCookie(mDeviceName);
        // アセット名を設定
        mAssetName= rsrc.getString(R.string.ims_asset_name);
    }

    public void init(IfLinkAppIf iai){
        this.iai = iai;
    }

    public void createDevice(){
        // デバイス登録
        Log.d(TAG, "createDevice");
        // 基本は、デバイスとの接続確立後、デバイスの対応したシリアル番号に更新してからデバイスを登録してください。
        iai.createDevice(true, 0, mDeviceName, mDeviceSerial, mAssetName, mCookie, mSchemaName, mSchema);
    }

    protected String loadSchema() {
        return getSchemaFromXml(false);
    }

    @NonNull
    private String getSchemaFromXml(boolean isPersonalPropertyOnly) {
        if (this.bDBG) {
            Log.d("DEV-CNCTR", "schema isPersonalOnly=" + isPersonalPropertyOnly);
        }

        XmlResourceParser xmlResourceParser = this.getResourceParser(this.mService.getResources());
        if (xmlResourceParser != null) {
            SchemaXmlParser xmlParser = new SchemaXmlParser(xmlResourceParser, isPersonalPropertyOnly);
            String parsedString = xmlParser.parse();
            if (this.bDBG) {
                Log.d("DEV-CNCTR", this.mDeviceName + ":" + this.mDeviceSerial + " schema=" + parsedString);
            }

            return parsedString;
        } else {
            Log.e("DEV-CNCTR", this.mDeviceName + ":" + this.mDeviceSerial + " xmlResourceParser is null.");
            return "";
        }
    }

//    public boolean onStartDevice() {
//        if (bDBG) Log.d(TAG, "onStartDevice");
//        // デバイスからのデータ送信開始処理
//        startSendDataTimer();
//        // 送信開始が別途完了通知を受ける場合には、falseを返してください。
//        return true;
//    }

//    public boolean onStopDevice() {
//        if (bDBG) Log.d(TAG, "onStopDevice");
//        // デバイスからのデータ送信停止処理
//        if (sendDataTimer != null) {
//            sendDataTimer.cancel();
//        }
//        // 送信停止が別途完了通知を受ける場合には、falseを返してください。
//        return true;
//    }

    /**
     * カウント値を送信する.
     * このメソッドをデバイスからデータを受信したタイミング等で呼び出せば.
     * ifLink Core側にデータが送信されます.
     *
     * @param unitId 個体識別ID.
     * @param bdAddress BDアドレス.
     * @param co2 CO2濃度.
     * @param temperature 気温.
     * @param humidity 湿度.
     * @param motion 人感(0/1).
     * @param barometer 気圧.
     */
    public void sendData(final String unitId, final String bdAddress, final int co2, final int temperature, final int humidity, final int motion, final int barometer) {
        Log.i(TAG, String.format("sendDeviceData unitId=%s,bdAddress=%s,co2=%d,temperature=%d,humidity=%d,motion=%d,barometer=%d" , unitId, bdAddress, co2, temperature, humidity, motion, barometer));
        // データの登録.
        HashMap<String, String> sensorData = new LinkedHashMap<String, String>();
        sensorData.put("unit_id", unitId);
        sensorData.put("bd_address", bdAddress);
        sensorData.put("co2", String.valueOf(co2));
        sensorData.put("temperature", String.valueOf(temperature));
        sensorData.put("humidity", String.valueOf(humidity));
        sensorData.put("motion", String.valueOf(motion));
        sensorData.put("barometer", String.valueOf(barometer));
        //ifLink Coreへデータを送信する.
        iai.sendSensor(new Date().getTime(), sensorData);
    }

    public void enableLogLocal(final boolean enabled) {
        bDBG = enabled;
    }

    @Nullable
    protected XmlResourceParser getResourceParser(final Resources resources) {
        if (resources != null) {
            return resources.getXml(R.xml.schema_closedbuster);
        } else {
            return null;
        }
    }

    public String getDeviceName() {
        return mDeviceName;
    }

    public String getDeviceSerial() {
        return mDeviceSerial;
    }

    public String getSchemaName() {
        return mSchemaName;
    }

    public String getSchema() {
        return mSchema;
    }

    public String getCookie() {
        return mCookie;
    }

    public String getAssetName() {
        return mAssetName;
    }

//    // デバイスとの接続経路が有効かをチェックする処理
//    public final boolean checkPathConnection() {
//        //if (bDBG) Log.d(TAG, "checkPathConnection");
//        ClosedBusterIaiService ims = (ClosedBusterIaiService) mService;
//        // BLEスキャンタスクが存在し、稼働中かどうかチェック
//        if (bDBG) Log.d(TAG, "checkPathConnection scanTask="+(ims.getBleScanTask()!=null)+" run="+ims.isRunning());
//        return (ims.getBleScanTask() != null && ims.isRunning());
//    }
//
//    // デバイスとの接続経路を有効にする処理
//    public final boolean reconnectPath() {
//        //if (bDBG) Log.d(TAG, "reconnectPath");
//        ClosedBusterIaiService ims = (ClosedBusterIaiService) mService;
//        if (bDBG) Log.d(TAG, "reconnectPath scanTask="+(ims.getBleScanTask()!=null)+" run="+ims.isRunning());
//        if (ims.getBleScanTask() != null && ims.isRunning()){
//            // BLEスキャンタスクが存在し、稼働中の場合は何もしない
//            return true;
//        }
//        // サービスの開始
//        Intent serviceIntent = new Intent(ims.getApplicationContext(), ClosedBusterIaiService.class);
//        serviceIntent.putExtra(BleScanTask.NAME, true);
//        if (Build.VERSION.SDK_INT >= 26) {
//            // Android8以降の場合);
//            ims.startForegroundService(serviceIntent);
//        } else {
//            // Android7以前の場合
//            ims.startService(serviceIntent);
//        }
//        return true;
//    }
//
//    // デバイスとの接続が維持されているかをチェックする処理
//    public final boolean checkDeviceConnection() {
//        //if (bDBG) Log.d(TAG, "checkDeviceConnection");
//        ClosedBusterIaiService ims = (ClosedBusterIaiService) mService;
//        if (bDBG) Log.d(TAG, "checkDeviceConnection1 scanTask="+(ims.getBleScanTask()!=null)+" run="+ims.isRunning());
//        // BLEスキャンタスクが存在し、稼働中かどうかチェック
//        if  (ims.getBleScanTask() == null || !ims.isRunning()){
//            return false;
//        }
//        // 前回のスキャンコールバック時刻を取得
//        Date beforeTime = this.lastScanCallbackTime;
//        if (beforeTime == null){
//            // 初回の場合、最新のスキャンコールバック時刻を取得
//            this.lastScanCallbackTime = ims.getBleScanTask().getLastScanCallbackTime();
//            if (this.lastScanCallbackTime != null){
//                // スキャンコールバック時刻が入っていた場合は、稼働中と判定
//                if (bDBG) Log.d(TAG, "checkDeviceConnection2 isAlive=true");
//                return true;
//            } else {
//                // そうでない場合は、現在日時を設定
//                beforeTime = new Date();
//            }
//        }
//        boolean isAlive = false;
//        // 最大5秒間チェック
//        for (int cnt=1; cnt<=5; cnt++){
//            try {
//                // 1秒待機
//                Thread.sleep(1000);
//                // BLEスキャンタスクが存在し、稼働中かどうかチェック
//                if  (ims.getBleScanTask() == null || !ims.isRunning()){
//                    // 稼働中でない場合は判定を終了
//                    break;
//                }
//                // 最新のスキャンコールバック時刻を取得
//                this.lastScanCallbackTime = ims.getBleScanTask().getLastScanCallbackTime();
//                // スキャンコールバック時刻が更新されているかどうかチェック
//                Date afterTime = lastScanCallbackTime;
//                if (afterTime != null && beforeTime.getTime() != afterTime.getTime()){
//                    // 更新されていた場合、判定を終了
//                    isAlive = true;
//                    break;
//                }
//            } catch (InterruptedException e) {
//                Log.e(TAG, e.getMessage(), e);
//            }
//        }
//        if (bDBG) Log.d(TAG, "checkDeviceConnection2 isAlive="+isAlive);
//        return isAlive;
//    }
//
//    // デバイスとの再接続処理
//    public final boolean reconnectDevice() {
//        //if (bDBG) Log.d(TAG, "reconnectDevice");
//        ClosedBusterIaiService ims = (ClosedBusterIaiService)mService;
//        if (bDBG) Log.d(TAG, "reconnectDevice scanTask="+(ims.getBleScanTask()!=null)+" run="+ims.isRunning());
//        // スキャンの再始動
//        if (ims.getBleScanTask() != null){
//            ims.getBleScanTask().restartScan();
//        }
//        // BLEスキャンタスクが存在し、稼働中かどうかチェック
//        return ims.getBleScanTask() != null && ims.isRunning();
//    }

//    private void showMessage(final String message){
//        final Context ctx = mService.getApplicationContext();
//        Handler handler = new Handler(Looper.getMainLooper());
//        handler.post(new Runnable() {
//            public void run() {
//                Toast.makeText(ctx, message, Toast.LENGTH_SHORT).show();
//            }
//        });
//    }

}
