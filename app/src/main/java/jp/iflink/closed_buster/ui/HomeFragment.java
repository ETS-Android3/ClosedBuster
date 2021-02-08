package jp.iflink.closed_buster.ui;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.os.Bundle;

import androidx.annotation.ColorInt;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.PreferenceManager;

import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.GlideDrawableImageViewTarget;
import com.ekn.gruzer.gaugelibrary.Range;
import com.github.mikephil.charting.charts.LineChart;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import jp.iflink.closed_buster.R;
import jp.iflink.closed_buster.graph.CustomArcGauge;
import jp.iflink.closed_buster.graph.GraphManager;
import jp.iflink.closed_buster.iai.ClosedBusterIaiService;
import jp.iflink.closed_buster.model.SensorInfo;
import jp.iflink.closed_buster.setting.AppLayoutType;
import jp.iflink.closed_buster.setting.Co2Rank;
import jp.iflink.closed_buster.model.SensorData;
import jp.iflink.closed_buster.util.DataStore;
import jp.iflink.closed_buster.util.LogUseUtil;
import jp.iflink.closed_buster.util.XmlUtil;

public class HomeFragment extends Fragment implements ISensorFragment {
    private static final String TAG = "Home";
    /** ログ出力切替フラグ. */
    private boolean bDBG = false;
    // データストア
    private DataStore mDataStore;
    // グラフマネージャ
    private GraphManager mGraphManager;
    // 前回更新時刻テキスト表示
    private TextView mRecordTime;
    private LinearLayout mRecordTimeBlock;
    // 設定ボタン
    private FloatingActionButton mSettingButton;
    // ログ出力用
    private LogUseUtil loguseutil = new LogUseUtil();
    // 最新のセンサー情報
    private int lastMaxCo2 = 0;
    private Co2Rank lastMaxCo2Rank = null;

    // View
    private ImageView mMessage;
    private LinearLayout mImgCover;
    // Graph
    private CustomArcGauge mArc;
    private View progressView;
    // MaxValue
    private TextView mCo2SensorMaxValue;
    private TextView mCo2SensorMaxValueResult;
    private TextView mCo2SensorMaxValueResultLabel;

    // センサー1 CO2濃度
    private TextView mCo2SensorTitle1;
    private TextView mCo2SensorConcentration1;
    private TextView mCo2ConcentrationResult1;
    // センサー1 気温
    private TextView mCo2SensorTemperature1;
    // センサー1 湿度
    private TextView mCo2SensorHumidity1;
    // センサー1 気圧
    private TextView mCo2SensorBarometer1;
    // センサー1 人感
    private LinearLayout mCo2SensorMotion1;

    // センサー2 CO2濃度
    private TextView mCo2SensorTitle2;
    private TextView mCo2SensorConcentration2;
    private TextView mCo2ConcentrationResult2;
    // センサー2 気温
    private TextView mCo2SensorTemperature2;
    // センサー2 湿度
    private TextView mCo2SensorHumidity2;
    // センサー2 気圧
    private TextView mCo2SensorBarometer2;
    // センサー2 人感
    private LinearLayout mCo2SensorMotion2;
    // 換気扇アニメ描画ビュー
    private GlideDrawableImageViewTarget target;

    // データ集計用タイマー
    private Timer recordTimeTimer;
    // 共通ハンドラ
    private Handler mHandler;

    // アプリ共通設定
    private SharedPreferences prefs;
    // 人感OFF判定時間 [分]
    private int MOTION_OFF_MINUTES;
    // センサーOFF判定時間 [分]
    private int KEEP_DATA_MINUTES;
    // CO2濃度閾値 [ppm]
    private int CO2_HIGH_PPM;
    private int CO2_LOW_PPM;
    // アプリレイアウト種別
    private AppLayoutType appLayoutType;

    // センサーリスト
    private List<SensorInfo> mSensorList;
    // センサーのBDアドレスとセンサーIDの対応マップ
    private Map<String, Integer> mBdAddressIdMap;
    // ダミーのBDアドレス（未定義センサー用）
    private static final String DUMMY_BD_ADDRESS = "XX:XX:XX:XX:XX:XX";
    // CO2ゲージの範囲設定
    private static final double CO2_GAUGE_MAX = 2000;
    private static final double CO2_GAUGE_MIN = 400;

    // 表示ラベルの定義
    private String LB_TEMPERATURE_TEXT;
    private String LB_HUMIDITY_TEXT;
    private String LB_BAROMETER_TEXT;
    private String LB_CO2RANK_HIGH;
    private String LB_CO2RANK_MIDDLE;
    private String LB_CO2RANK_LOW;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        this.loguseutil = new LogUseUtil(Log.DEBUG);
        // ログ差込
        ////loguseutil.specific(TAG, "onCreate：Start", "DEBUG", 3);

        // ハンドラの初期化
        this.mHandler = new Handler();
        // アプリ共通設定を取得
        this.prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        // データストアの初期化
        mDataStore = new DataStore(getContext(), prefs);
        mDataStore.init();
        // グラフマネージャの初期化
        mGraphManager = new GraphManager(getContext(), prefs, getResources());
        mGraphManager.init(mDataStore);

        // センサー定義Xmlを読み込み、センサーリストを作成
        this.mSensorList = reloadXmlSensorList(this.getContext(), this.mSensorList);

        // BdAddress と id のマップを生成
        this.mBdAddressIdMap = createBdAddressIdMap(mSensorList);

        // ログ差込
        //loguseutil.specific(TAG, "onCreate End", "DEBUG", 3);
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");
        // ログ差込
        //loguseutil.specific(TAG, "onCreateView：START", "DEBUG", 3);

        // アプリ共通設定
        Resources rsrc = getResources();
        // アプリのレイアウト種別を取得
        this.appLayoutType = AppLayoutType.judge(prefs.getString("app_layout_type", rsrc.getString(R.string.default_app_layout_type)));
        // CO2濃度閾値を取得
        this.CO2_HIGH_PPM = getIntFromString(prefs, "co2_high_ppm", rsrc.getInteger(R.integer.default_co2_high_ppm));
        this.CO2_LOW_PPM = getIntFromString(prefs, "co2_low_ppm", rsrc.getInteger(R.integer.default_co2_low_ppm));

        // Inflate the layout for this fragment
        View root;
        if (appLayoutType == AppLayoutType.SMARTPHONE){
            // スマホ版の場合
            root = inflater.inflate(R.layout.fragment_smp, container, false);
            // 縦画面に固定
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        } else if (appLayoutType == AppLayoutType.TABLET_8){
            // ZenPad版の場合
            root = inflater.inflate(R.layout.fragment_tablet8, container, false);
            // 横画面に固定
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else {
            // それ以外（Vankyo等）の場合
            root = inflater.inflate(R.layout.fragment_home, container, false);
            // 横画面に固定
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        // CO2ランクの凡例テキストを設定
        TextView co2rankLow = root.findViewById(R.id.tv_co2rank_low);
        co2rankLow.setText(rsrc.getString(R.string.co2rank_text_low)
                .replace("{0}",String.valueOf(CO2_LOW_PPM-1)));
        TextView co2rankMiddle= root.findViewById(R.id.tv_co2rank_middle);
        co2rankMiddle.setText(rsrc.getString(R.string.co2rank_text_middle)
                .replace("{0}",String.valueOf(CO2_LOW_PPM))
                .replace("{1}",String.valueOf(CO2_HIGH_PPM-1))
        );
        TextView co2rankHigh = root.findViewById(R.id.tv_co2rank_high);
        co2rankHigh.setText(rsrc.getString(R.string.co2rank_text_high)
                .replace("{0}",String.valueOf(CO2_HIGH_PPM)));

        // Max
        mImgCover = root.findViewById(R.id.img_cover);
        mMessage = root.findViewById(R.id.img_message);
        mCo2SensorMaxValue = root.findViewById(R.id.tv_co2_max_value);
        mCo2SensorMaxValueResult = root.findViewById(R.id.tv_co2concentration_result_max);
        mCo2SensorMaxValueResultLabel = root.findViewById(R.id.tv_co2concentration_result_max_label);

        // センサー1 CO2濃度
        mCo2SensorTitle1 = root.findViewById(R.id.tv_sensortitle_1);
        // センサー1 ルーム名を設定
        if (mSensorList.size() >= 1){
            mCo2SensorTitle1.setText(mSensorList.get(0).getRoom());
        }
        mCo2ConcentrationResult1 = root.findViewById(R.id.tv_co2concentration_result_1);
        mCo2SensorConcentration1 = root.findViewById(R.id.tv_co2_concentration_1);
        // センサー1 気温
        mCo2SensorTemperature1 = root.findViewById(R.id.tv_co2_temperature_1);
        // センサー1 湿度
        mCo2SensorHumidity1 = root.findViewById(R.id.tv_co2_humidity_1);
        // センサー2 気圧
        mCo2SensorBarometer1 = root.findViewById(R.id.tv_co2_barometer_1);
        // センサー1 人感
        mCo2SensorMotion1 = root.findViewById(R.id.tv_co2_motion_1);

        // センサー2 CO2濃度
        mCo2SensorTitle2 = root.findViewById(R.id.tv_sensortitle_2);
        // センサー2 ルーム名を設定
        if (mSensorList.size() >= 2){
            mCo2SensorTitle2.setText(mSensorList.get(1).getRoom());
        }
        mCo2ConcentrationResult2 = root.findViewById(R.id.tv_co2concentration_result_2);
        mCo2SensorConcentration2 = root.findViewById(R.id.tv_co2_concentration_2);
        // センサー2 気温
        mCo2SensorTemperature2 = root.findViewById(R.id.tv_co2_temperature_2);
        // センサー2 湿度
        mCo2SensorHumidity2 = root.findViewById(R.id.tv_co2_humidity_2);
        // センサー2 気圧
        mCo2SensorBarometer2 = root.findViewById(R.id.tv_co2_barometer_2);
        // センサー2 人感
        mCo2SensorMotion2 = root.findViewById(R.id.tv_co2_motion_2);

        // 半円ゲージ
        mArc = root.findViewById(R.id.arcGauge);
        // メッセージ表示ビュー
        progressView = root;
        // 前回更新時刻
        mRecordTime = root.findViewById(R.id.tv_recordtime);
        mRecordTimeBlock = root.findViewById(R.id.tv_recordtime_block);
        // 設定ボタン
        mSettingButton = root.findViewById(R.id.btn_setting);
        //mSettingButton.setBackgroundTintMode(PorterDuff.Mode.ADD);
        mSettingButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(HomeFragment.this)
                        .navigate(R.id.action_HomeFragment_to_SettingsFragment);
            }
        });

        ImageView imageView = root.findViewById(R.id.gifImage);
        mMessage.setImageResource(R.drawable.message_low);
        imageView.setBackgroundColor(rsrc.getColor(R.color.corners_lowLabel));
        target = new GlideDrawableImageViewTarget(imageView);
        Glide.with(getActivity()).load(R.raw.splash).into(target);
        // 最大CO2濃度の表示
        mImgCover.setVisibility(mImgCover.VISIBLE);
        // 最新のセンサー情報をクリア
        lastMaxCo2 = 0;
        lastMaxCo2Rank = null;

        // グラフの追加
        if (mSensorList.size() >= 1) {
            mGraphManager.addLineChart(R.id.line_chart1, mSensorList.get(0).getBdAddress(), (LineChart) root.findViewById(R.id.line_chart1));
        }
        if (mSensorList.size() >= 2) {
            mGraphManager.addLineChart(R.id.line_chart2, mSensorList.get(1).getBdAddress(), (LineChart) root.findViewById(R.id.line_chart2));
        }

        // 表示ラベルの定義
        LB_TEMPERATURE_TEXT = getString(R.string.temperature_text);
        LB_HUMIDITY_TEXT = getString(R.string.humidity_text);
        LB_BAROMETER_TEXT = getString(R.string.barometer_text);
        LB_CO2RANK_HIGH = getString(R.string.co2rank_high);
        LB_CO2RANK_MIDDLE = getString(R.string.co2rank_middle);
        LB_CO2RANK_LOW = getString(R.string.co2rank_low);

        // ログ差込
        //loguseutil.specific(TAG, "onCreateView：END", "DEBUG", 3);

        return root;
    }

    private synchronized List<SensorInfo> reloadXmlSensorList(Context context, List<SensorInfo> oldSensorList){
        // Xmlセンサーリストの読み込み
        List<SensorInfo> xmlSensorList = XmlUtil.readXml(context);
        if (oldSensorList != null){
            // oldSensorListをマージする
            for (SensorInfo xmlSensor : xmlSensorList){
                for (SensorInfo oldSensor: oldSensorList){
                    if (xmlSensor.getBdAddress().equals(oldSensor.getBdAddress())){
                        // oldSensorのLastDateTimeを反映する
                        xmlSensor.setLastDateTime(oldSensor.getLastDateTime());
                        break;
                    }
                }
            }
        }
        // センサーリストを返却
        return xmlSensorList;
    }

    public Map<String, Integer> createBdAddressIdMap(List<SensorInfo> sensorList){

        // bdAddressIdMapの生成
        Map<String, Integer> bdAddressIdMap = new HashMap<>();

        // xmlのセンサー個数分処理
        for (SensorInfo sensor : sensorList) {
            // BDアドレスをキーに、XMLのidを登録
            bdAddressIdMap.put(sensor.getBdAddress(), sensor.getId());
        }

        // ダミーのBDアドレスのエントリを登録
        bdAddressIdMap.put(DUMMY_BD_ADDRESS, 1);

        return bdAddressIdMap;
    }

    // CO2濃度ゲージの設定
    public void setGauge(int co2maxValue){
        if (mArc.getValue() != co2maxValue){
            mArc.setValue(co2maxValue);
        }
    }

    // CO2濃度ゲージの設定
    public void setGauge(int co2maxValue, @ColorInt int colorId){

        Range range = new Range();
        range.setColor(colorId);
        range.setFrom(0.0);
        range.setTo(50.0);
        mArc.addRange(range);

        mArc.setMinValue(CO2_GAUGE_MIN);
        mArc.setMaxValue(CO2_GAUGE_MAX);
        mArc.setValue(co2maxValue);
    }

    public synchronized void drawEachSensorData(SensorData calculatedModel) {
        // ログ差込
        //loguseutil.specific(TAG, "drawEachSensorData：START", "DEBUG", 3);

        // リソース取得
        Resources rsrc = getResources();

        // CO2濃度の取得
        Number co2Value = calculatedModel.getCo2concentration();
        int co2 = (co2Value != null)? co2Value.intValue() : 0;
        String co2concentrationText = (co2 != 0)? co2Value.toString():"0000";

        // 気温の取得
        Number temperatureValue = calculatedModel.getTemperature();
        int temperature = (temperatureValue != null)? temperatureValue.intValue() : 0;
        String temperatureText = (temperature != 0)? LB_TEMPERATURE_TEXT.replace("{0}",temperatureValue.toString()):"";

        // 湿度の取得
        Number humidityValue = calculatedModel.getHumidity();
        int humidity = (humidityValue != null)? humidityValue.intValue() : 0;
        String humidityText = (humidity != 0)? LB_HUMIDITY_TEXT.replace("{0}",humidityValue.toString()):"";

        // 気圧の取得
        Number barometerValue = calculatedModel.getBarometer();
        int barometer = (barometerValue != null)? barometerValue.intValue() : 0;
        String barometerText = (barometer != 0)? LB_BAROMETER_TEXT.replace("{0}",barometerValue.toString()):"";

        // BDアドレスの取得
        String bdAddressValue = calculatedModel.getBdAddress();

        // 人感の取得
        Number motionValue = calculatedModel.getMotion();
        int motion = (motionValue != null)? motionValue.intValue() : 0;

        // 画面表示の箱の分岐
        // センサーのid
        int xmlCo2Id = 0;

        // センサーのidを取得
        if (mBdAddressIdMap.containsKey(bdAddressValue)) {
            xmlCo2Id = mBdAddressIdMap.get(bdAddressValue);
        }
        // データ保存
        Log.d(TAG, "drawEachSensorData (id="+xmlCo2Id+")");

        // CO2濃度のランク判定
        Co2Rank co2Rank = Co2Rank.judge(co2, CO2_HIGH_PPM, CO2_LOW_PPM);

        TextView mCo2SensorConcentration = null;
        TextView mCo2SensorTemperature = null;
        TextView mCo2SensorHumidity = null;
        TextView mCo2SensorBarometer = null;
        TextView mCo2ConcentrationResult = null;
        LinearLayout mCo2SensorMotion = null;

        // 各種判定フラグ
        boolean hasSensorData = false;
        boolean changeCo2Rank = false;
        boolean motionON = false;

        if (xmlCo2Id == 1 && co2 >= 0) {
            // 1個目の箱の場合
            hasSensorData = true;
            mCo2SensorConcentration = mCo2SensorConcentration1;
            mCo2SensorTemperature = mCo2SensorTemperature1;
            mCo2SensorHumidity = mCo2SensorHumidity1;
            mCo2SensorBarometer = mCo2SensorBarometer1;
            mCo2ConcentrationResult = mCo2ConcentrationResult1;
            mCo2SensorMotion = mCo2SensorMotion1;

        } else if(xmlCo2Id == 2  && co2 >= 0) {
            // 2個目の箱の場合
            hasSensorData = true;
            mCo2SensorConcentration = mCo2SensorConcentration2;
            mCo2SensorTemperature = mCo2SensorTemperature2;
            mCo2SensorHumidity = mCo2SensorHumidity2;
            mCo2SensorBarometer = mCo2SensorBarometer2;
            mCo2ConcentrationResult = mCo2ConcentrationResult2;
            mCo2SensorMotion = mCo2SensorMotion2;
        }

        if (xmlCo2Id != 0 && co2 >= 0) {
            // センサー情報の取得
            SensorInfo sensor = findSensor(xmlCo2Id);
            if (sensor != null){
                // CO2濃度によって表示切り替え
                if (co2Rank != sensor.getLastCo2Rank() || co2 == 0) {
                    changeCo2Rank = true;
                    sensor.setLastCo2Rank(co2Rank);
                }

                // 人感センサーの判定
                if (sensor.getLastMotion() != motion) {
                    // 人感が変わった時、切り替わった時刻を記録
                    sensor.setLastMotionTime(calculatedModel.getDatetime());
                    if (motion == 0 || motion == 1) {
                        // 人感はONにする（OFF⇒ON、ON⇒OFFいずれも直後はONである為）
                        motionON = true;
                    } else {
                        // データ無しの場合は人感をOFFにする
                        motionON = false;
                    }
                } else {
                    // 人感が変わらない時
                    if (motion == 0) {
                        // 人感がOFFの場合、前回切り替わった時刻からの経過時間を取得
                        long durationTime = calculatedModel.getDatetime() - sensor.getLastMotionTime();
                        if (durationTime >= (MOTION_OFF_MINUTES * 1000 * 60)) {
                            // １分以上経過している場合は、人感をOFFにする
                            motionON = false;
                        } else {
                            // 1分未満の場合はONにする
                            motionON = true;
                        }
                    } else if (motion == 1) {
                        // 人感がONの場合はONにする
                        motionON = true;
                    }
                }
                sensor.setLastMotion(motion);
                sensor.setLastDateTime(calculatedModel.getDatetime());
            }
        }

        if (hasSensorData){
            // センサー値を表示
            mCo2SensorConcentration.setText(co2concentrationText);
            mCo2SensorTemperature.setText(temperatureText);
            mCo2SensorHumidity.setText(humidityText);
            mCo2SensorBarometer.setText(barometerText);

            // CO2濃度によって表示切り替え
            if (changeCo2Rank) {
                if (co2Rank == Co2Rank.LOW) {
                    // 文言の変更
                    mCo2ConcentrationResult.setText(LB_CO2RANK_LOW.substring(0,1));
                    mCo2ConcentrationResult.setTextColor(rsrc.getColor(R.color.corners_lowLabel));

                } else if (co2Rank == Co2Rank.MIDDLE) {
                    // 文言の変更
                    mCo2ConcentrationResult.setText(LB_CO2RANK_MIDDLE.substring(0,1));
                    mCo2ConcentrationResult.setTextColor(rsrc.getColor(R.color.corners_middleLabel));

                } else if (co2Rank == Co2Rank.HIGH) {
                    // 文言の変更
                    mCo2ConcentrationResult.setText(LB_CO2RANK_HIGH.substring(0,1));
                    mCo2ConcentrationResult.setTextColor(rsrc.getColor(R.color.corners_highLabel));

                } else {
                    mCo2ConcentrationResult.setText("");
                }
            }
            // 人感の切り替え
            if (motionON) {
                // バーの色の変更
                if (co2Rank == Co2Rank.LOW) {
                    mCo2SensorMotion.setBackgroundResource(R.drawable.shape_rounded_corners_top_low);
                } else if (co2Rank == Co2Rank.MIDDLE) {
                    mCo2SensorMotion.setBackgroundResource(R.drawable.shape_rounded_corners_top_middle);
                } else if (co2Rank == Co2Rank.HIGH) {
                    mCo2SensorMotion.setBackgroundResource(R.drawable.shape_rounded_corners_top_high);
                }
            } else {
                mCo2SensorMotion.setBackgroundResource(R.drawable.shape_rounded_corners_top_nomotion);
            }
        }

        // ログ差込
        //loguseutil.specific(TAG, "drawEachSensorData：END", "DEBUG", 3);
    }

    private SensorInfo findSensor(int xmlCo2Id){
        for (SensorInfo sensor : mSensorList){
            if (sensor.getId() == xmlCo2Id){
                return sensor;
            }
        }
        return null;
    }

    private int getDelayMillisToNext10Minute(){
        Calendar nextTime = Calendar.getInstance();
        final int minute = nextTime.get(Calendar.MINUTE);
        nextTime.set(Calendar.SECOND, 0);
        nextTime.set(Calendar.MILLISECOND, 0);
        nextTime.set(Calendar.MINUTE, ((minute/10)+1)*10);
        return (int)(nextTime.getTime().getTime() - new Date().getTime());
    }


    private boolean isSameRecordTime(Date nowTime, Date prevTime, int minute){
        if (nowTime== null || prevTime == null){
            return false;
        }
        // 時刻を1分単位＊指定したminute毎の精度にして、同一記録時間帯かどうか判定
        int nowTimeVal = (int)(nowTime.getTime()/(60000 * minute));
        int prevTimeVal = (int)(prevTime.getTime()/(60000 * minute));
        return nowTimeVal == prevTimeVal;
    }

    @Override
    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
        // ログ差込
        //loguseutil.specific(TAG, "onStart：START", "DEBUG", 3);

        // デバッグフラグの設定
        this.bDBG = getDebugFlag(prefs);
        // 各種設定の取得
        Resources rsrc = getResources();
        this.MOTION_OFF_MINUTES = getIntFromString(prefs, "motion_off_minutes", rsrc.getInteger(R.integer.default_motion_off_minutes));
        this.KEEP_DATA_MINUTES = getIntFromString(prefs, "keep_data_minutes", rsrc.getInteger(R.integer.default_keep_data_minutes));

        if (recordTimeTimer == null) {
            // 前回集計時刻、グラフ描画用定期処理（1分間隔）
            recordTimeTimer = new Timer(true);
            // 初期実行待ち時間（5秒後）
            int timerDelay = 5000;
            recordTimeTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            boolean clearSensorData = false;
                            for (SensorInfo sensor : mSensorList) {
                                if (sensor.getLastDateTime() <= 0) {
                                    continue;
                                }
                                if ((KEEP_DATA_MINUTES * 1000 * 60) <= System.currentTimeMillis() - sensor.getLastDateTime()) {
                                    // 前回の送信時刻からセンサーOFF判定時間を過ぎたセンサーは描画をクリア
                                    clearSensorData = true;
                                    SensorData zeroData = new SensorData();
                                    zeroData.setBdAddress(sensor.getBdAddress());
                                    zeroData.setMotion(-1);
                                    sensor.setLastDateTime(0L);
                                    drawEachSensorData(zeroData);
                                }
                            }
                            if (clearSensorData){
                                // 個々のセンサーをクリアした場合は、メインのセンサーも再描画
                                drawMainSensorData();
                            }

                            if (appLayoutType != AppLayoutType.SMARTPHONE){
                                // グラフの描画
                                // ※スマートフォン版の場合はグラフ描画無し
                                for (SensorInfo sensor : mSensorList) {
                                    // グラフデータの読込
                                    mGraphManager.loadData(sensor.getBdAddress(), new Date());
                                    // グラフの描画
                                    mGraphManager.updateGraph(sensor.getBdAddress());
                                }
                            }
                        }
                    });
                }
            }, timerDelay, 1000 * 60);
        }

        // ログ差込
        //loguseutil.specific(TAG, "onStart：END", "DEBUG", 3);
    }

    private void drawMainSensorData(){
        // リソース取得
        Resources rsrc = getResources();

        // 各センサーのCO2濃度を取得
        int sensor1_co2 = 0;
        try {
            sensor1_co2 = Integer.parseInt(mCo2SensorConcentration1.getText().toString());
        } catch (NumberFormatException e){}
        int sensor2_co2 = 0;
        try {
            sensor2_co2 = Integer.parseInt(mCo2SensorConcentration2.getText().toString());
        } catch (NumberFormatException e){}

        // CO2濃度の最大値を比較して表示
        int maxCo2SensorValue = Math.max(sensor1_co2, sensor2_co2);
        if (maxCo2SensorValue > 0){
            mCo2SensorMaxValue.setText(String.valueOf(maxCo2SensorValue));
        } else {
            mCo2SensorMaxValue.setText("0000");
        }

        // 最大CO2濃度の表示
        lastMaxCo2 = maxCo2SensorValue;
        Co2Rank co2Rank = Co2Rank.judge(lastMaxCo2, CO2_HIGH_PPM, CO2_LOW_PPM);
        if (co2Rank == null || lastMaxCo2 <= 0){
            setGauge(0);
            // CO2ランクをクリア
            if (!mCo2SensorMaxValueResult.getText().equals("")){
                mCo2SensorMaxValueResult.setText("");
            }
            // メッセージを消去
            if (mImgCover.getVisibility() != ImageView.VISIBLE){
                mImgCover.setVisibility(ImageView.VISIBLE);
                ImageView imageView = progressView.findViewById(R.id.gifImage);
                imageView.setVisibility(ImageView.INVISIBLE);
            }
        }else if (co2Rank == Co2Rank.LOW && co2Rank != lastMaxCo2Rank) {
            // CO2濃度が「低」かつ 前回が「低」でない場合
            mImgCover.setVisibility(ImageView.INVISIBLE);
            ImageView imageView = progressView.findViewById(R.id.gifImage);
            mMessage.setImageResource(R.drawable.message_low);
            imageView.setBackgroundColor(rsrc.getColor(R.color.corners_lowLabel));
            imageView.setVisibility(ImageView.VISIBLE);

            if(target == null){
                target = new GlideDrawableImageViewTarget(imageView);
                Glide.with(getActivity()).load(R.raw.splash).into(target);
            }

            // 文言の変更
            mCo2SensorMaxValueResult.setText(LB_CO2RANK_LOW);
            mCo2SensorMaxValueResult.setTextColor(rsrc.getColor(R.color.corners_lowLabel));
            mCo2SensorMaxValueResultLabel.setTextColor(rsrc.getColor(R.color.corners_lowLabel));
            setGauge(lastMaxCo2, rsrc.getColor(R.color.corners_lowLabel));

        } else if (co2Rank == Co2Rank.MIDDLE && co2Rank != lastMaxCo2Rank) {
            // CO2濃度が「中」かつ 前回が「中」でない場合
            mImgCover.setVisibility(ImageView.INVISIBLE);
            ImageView imageView = progressView.findViewById(R.id.gifImage);
            mMessage.setImageResource(R.drawable.message_middle);
            imageView.setImageBitmap(null);
            imageView.setBackgroundColor(rsrc.getColor(R.color.corners_middleLabel));
            imageView.setVisibility(ImageView.INVISIBLE);

            // 文言の変更
            mCo2SensorMaxValueResult.setText(LB_CO2RANK_MIDDLE);
            mCo2SensorMaxValueResult.setTextColor(rsrc.getColor(R.color.corners_middleLabel));
            mCo2SensorMaxValueResultLabel.setTextColor(rsrc.getColor(R.color.corners_middleLabel));
            setGauge(lastMaxCo2, rsrc.getColor(R.color.corners_middleLabel));
            target = null;

        } else if (co2Rank == Co2Rank.HIGH && co2Rank != lastMaxCo2Rank) {
            // CO2濃度が「高」かつ 前回が「高」でない場合
            mImgCover.setVisibility(ImageView.INVISIBLE);
            ImageView imageView = progressView.findViewById(R.id.gifImage);
            mMessage.setImageResource(R.drawable.message_high);
            imageView.setImageBitmap(null);
            imageView.setBackgroundColor(rsrc.getColor(R.color.corners_highLabel));
            imageView.setVisibility(ImageView.INVISIBLE);
            // 文言の変更
            mCo2SensorMaxValueResult.setText(LB_CO2RANK_HIGH);
            mCo2SensorMaxValueResult.setTextColor(rsrc.getColor(R.color.corners_highLabel));
            mCo2SensorMaxValueResultLabel.setTextColor(rsrc.getColor(R.color.corners_highLabel));
            setGauge(lastMaxCo2, rsrc.getColor(R.color.corners_highLabel));
            target = null;
        } else {
            setGauge(lastMaxCo2);
        }
        lastMaxCo2Rank = co2Rank;
    }

    @Override
    public void drawSensorData(Map<String, SensorData> sensorDataMap, Date lastDataTime, boolean drawUnknown){
        // 画面描画処理の開始ｍ
        // 定義されているBDAddress分を処理
        for (SensorInfo sensor : mSensorList) {
            // センサーデータの取得
            SensorData sensorData = sensorDataMap.get(sensor.getBdAddress());
            if (sensorData != null){
                // 個々のセンサーデータの描画
                drawEachSensorData(sensorData);
            }
        }
        if (drawUnknown){
            // 未定義のBDアドレスのセンサーデータの処理（IBI Testerを想定）
            for (Map.Entry<String, SensorData> entry : sensorDataMap.entrySet()){
                if (!mBdAddressIdMap.containsKey(entry.getKey())){
                    // 未定義のBDアドレスのセンサデータの場合、1番目に描画
                    SensorData sensorData = entry.getValue();
                    sensorData.setBdAddress(DUMMY_BD_ADDRESS);
                    drawEachSensorData(entry.getValue());
                }
            }
        }

         // 画面上部のメインのセンサーデータの描画
         drawMainSensorData();
         // 前回更新時刻を更新
         mRecordTime.setText(getDateTimeText(lastDataTime));
         mRecordTimeBlock.setVisibility(View.VISIBLE);

    }

    @Override
    public void onStop() {
        super.onStop();
        //Log.d(TAG, "onStop");
    }

    @Override
    public void onResume(){
        super.onResume();
        //Log.d(TAG, "onResume");
    }

    @Override
    public void onPause(){
        super.onPause();
        //Log.d(TAG, "onPause");
    }

    @Override
    public void onDestroy(){
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (recordTimeTimer != null){
            recordTimeTimer.cancel();
            recordTimeTimer = null;
        }
        mGraphManager.removeAllLineChart();
    }

    private int getDelayMillisToNextMinute(){
        Calendar nextMinute = Calendar.getInstance();
        nextMinute.set(Calendar.SECOND, 0);
        nextMinute.set(Calendar.MILLISECOND, 0);
        nextMinute.add(Calendar.MINUTE, 1);
        return (int)(nextMinute.getTime() .getTime() - new Date().getTime());
    }

    private boolean isJustTimeMinuteOf(int minute){
        // 時刻を1分単位の精度にして指定したminute毎かどうかを判定
        return (int)(new Date().getTime()/60000) % minute == 0;
    }

    private boolean isJustTimeMinuteOf(Date date, int minute){
        // 時刻を1分単位の精度にして指定したminute毎かどうかを判定
        return (int)(date.getTime()/60000) % minute == 0;
    }

    private String getDateTimeText(Date date) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        return sdf.format(date);
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

    private boolean getDebugFlag(SharedPreferences prefs){
        Set<String> loglevelSet  = prefs.getStringSet("loglevel", null);
        return loglevelSet != null && loglevelSet.contains(ClosedBusterIaiService.LOG_LEVEL_CUSTOM_IMS);
    }

    private void showMessage(final String message){
        Activity activity = getActivity();
        if (activity == null){
            return;
        }
        activity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
