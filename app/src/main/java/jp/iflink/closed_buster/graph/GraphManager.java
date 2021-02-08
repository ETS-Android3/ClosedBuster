package jp.iflink.closed_buster.graph;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jp.iflink.closed_buster.R;
import jp.iflink.closed_buster.model.SensorChartData;
import jp.iflink.closed_buster.model.SensorDataRecord;
import jp.iflink.closed_buster.util.DataStore;

public class GraphManager {
    private static final String TAG = "GraphManager";
    // デフォルトの最大CO2濃度
    public static final float DEFAULT_MAX_CO2 = 1500f;
    // CO2濃度の基準線
    public static final float CO2_LIMIT_LINE = 1000f;
    // 目盛線の数
    public static final int SCALE_MARKS = 3;
    // コンテキスト
    private Context mContext;
    // データストア
    private DataStore mDataStore;
    // アプリ共通設定
    private SharedPreferences prefs;
    // リソース
    private Resources rsrc;
    // 折れ線グラフのマップ
    private Map<Integer, LineChart> lineChartMap = new HashMap<>();
    // BDアドレスとチャートIDの対応マップ
    private Map<String, Integer> bdAddressMap = new HashMap<>();

    // 折れ線グラフを追加する
    public void addLineChart(int chartId, String bdAddress, LineChart pChart){
        lineChartMap.put(chartId, pChart);
        bdAddressMap.put(bdAddress, chartId);
    }

    // 折れ線グラフを削除する
    public void removeLineChart(int chartId){
        lineChartMap.remove(chartId);
        for (Iterator<Map.Entry<String,Integer>> it=bdAddressMap.entrySet().iterator(); it.hasNext();){
            Map.Entry<String,Integer> entry = it.next();
            if (entry.getValue().intValue() == chartId){
                it.remove();
            }
        }
    }

    // 折れ線グラフをすべて削除する
    public void removeAllLineChart(){
        lineChartMap.clear();
        bdAddressMap.clear();
    }

    // 折れ線グラフを取得する
    public LineChart getLineChart(int chartId){
        return lineChartMap.get(chartId);
    }

//    public GraphManager() {}
   public GraphManager(Context context, SharedPreferences prefs, Resources rsrc) {
       this.mContext = context;
       this.prefs = prefs;
       this.rsrc = rsrc;
   }

    public void init(DataStore dataStore){
        // データストアの設定
        this.mDataStore = dataStore;
        // 折れ線グラフのマップをクリア
        removeAllLineChart();
    }

    public Date loadData(String bdAddress, Date baseDate){
        Date prevRecordTime = null;
        try {
            // データの読み込み
            List<SensorDataRecord> recordList = mDataStore.readRecordList(bdAddress, baseDate);
            // データの反映
            prevRecordTime = mDataStore.update(bdAddress, recordList, baseDate);
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
            showMessage(R.string.data_load_fail);
        }
        return prevRecordTime;
    }

    private void update(LineChart pChart, SensorChartData chartData){
        // X軸設定
        String[] labels = createXLabelArray(chartData);
        setupXAxis(pChart, labels);
        // Y軸の最大値を算出
        float yMaxValue = calculateYAxisMax(chartData);
        // Y軸設定
        setupYAxis(pChart, yMaxValue);
        // description textを無効化
        pChart.getDescription().setEnabled(false);
        // データ更新
        List<Entry> values = createLineDataSetValues(chartData);
        applyLineDataSetValues(pChart, values);
        // グラフ描画実行
        pChart.animateX(0);
    }

    public Date updateGraph(String bdAddress) {
        // センサーデータをデータストアから取得
        SensorChartData chartData = mDataStore.getSensorData(bdAddress);
        if (chartData == null){
            return null;
        }
        // チャートIDを検索
        Integer chartId = bdAddressMap.get(chartData.bdAddress);
        if (chartId != null){
            // グラフ取得
            LineChart pChart = lineChartMap.get(chartId);
            // グラフデータ更新
            update(pChart, chartData);
        }
        // 時系列の基準日時を返却
        return chartData.baseDate;
    }

    // X軸のセットアップ
    private void setupXAxis(LineChart pChart, String[] labels){
        // X軸を取得
        XAxis xAxis = pChart.getXAxis();
        // X軸のラベルを設定
        IndexAxisValueFormatter indexAxisValueFormatter = new IndexAxisValueFormatter(labels);
        xAxis.setValueFormatter(indexAxisValueFormatter);
        // X軸ラベルを下部に表示
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        // X軸のGrid線を破線に設定
        //xAxis.enableGridDashedLine(10f, 10f, 0f);
        // X軸のGrid線を非表示
        xAxis.setDrawGridLines(false);
        // X軸のラベルを非表示
        xAxis.setDrawLabels(false);
        // X軸のテキストサイズを設定
        xAxis.setTextSize(10);
//        // X軸のラベル数を設定
//        xAxis.setLabelCount(12);
//        try {
//            ReflectionUtil.setFieldIntValue(AxisBase.class, "mLabelCount", xAxis, labels.length);
//        } catch (NoSuchFieldException | IllegalAccessException e) {
//            Log.e(TAG, e.getMessage(), e);
//        }
    }

    // Y軸のセットアップ
    private void setupYAxis(LineChart pChart, float yMaxValue){
        // Y軸を取得
        YAxis leftAxis = pChart.getAxisLeft();
        // Y軸の最大最小を設定
        leftAxis.setAxisMaximum(yMaxValue);
        leftAxis.setAxisMinimum(0f);
        // Y軸のGrid線を破線に設定
        leftAxis.enableGridDashedLine(0f, 0f, 0f);
        leftAxis.setDrawGridLines(false);
        leftAxis.setDrawZeroLine(true);
        // Limit Lineの設定
        LimitLine limitLine = createLimitLine(CO2_LIMIT_LINE);
        leftAxis.addLimitLine(limitLine);
        leftAxis.setLabelCount(SCALE_MARKS, false);
        // 目盛りのラベルの設定（左側に表示）
        pChart.getAxisLeft().setEnabled(true);
        pChart.getAxisRight().setEnabled(false);
    }

    // 基準線の作成
    private LimitLine createLimitLine(float fLimit){
        // Limit Lineを生成
        LimitLine limitLine = new LimitLine(fLimit, "");
        // 色を設定
        limitLine.setLineColor(Color.parseColor("#E27829"));
        // 幅を設定
        limitLine.setLineWidth(2.5f);
        // テキストサイズを設定
        //ll.setTextSize(10f);
        // Limit Lineを返却
        return limitLine;
    }

    // 時間ラベルのリストの取得
    private String[] createXLabelArray(SensorChartData chartData){
        // xLabel用の時間ラベル（分単位）
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        // 基準日時を取得
        Calendar dateCal = Calendar.getInstance();
        dateCal.setTime(chartData.baseDate);
        // グラフの表示期間を計算し、基準日時から表示期間分遡って開始日時を設定する
        int countMinutes = chartData.intervalMinutes * chartData.co2Data.length;
        dateCal.add(Calendar.MINUTE, -countMinutes);
        //　ラベルリスト
        List<String> labelList = new ArrayList<>();
        for(int co2: chartData.co2Data){
            // 日時を書式化してラベルを作成
            String labelValue = sdf.format(dateCal.getTime());
            // ラベルリストに追加
            labelList.add(labelValue);
            // 日時を加算
            dateCal.add(Calendar.MINUTE, chartData.intervalMinutes);
        }
        // ラベルリストを配列に変換して返却
        return labelList.toArray(new String[labelList.size()]);
    }

    private float calculateYAxisMax(SensorChartData chartData){
        // 最大CO2濃度を取得
        int maxCO2 = 0;
        for(int co2 : chartData.co2Data){
            if (maxCO2 < co2){
                maxCO2 = co2;
            }
        }
        // デフォルトの最大CO2濃度と比較し、大きい方を返却
        return Math.max(maxCO2, DEFAULT_MAX_CO2);
    }

    private List<Entry> createLineDataSetValues(SensorChartData chartData) {
        List<Entry> values = new ArrayList<>();
        for (int idx = 0; idx < chartData.co2Data.length; idx++) {
            float valueX = idx;
            float valueY = chartData.co2Data[idx];
            Entry entry = new Entry(valueX, valueY, null, null);
            values.add(entry);
        }
        return values;
    }

    private LineDataSet applyLineDataSetValues(LineChart pChart, List<Entry> values) {
        LineDataSet dataSet;

        if (pChart.getData() != null && pChart.getData().getDataSetCount() > 0) {
            // 2回目以降のデータ設定
            dataSet = (LineDataSet) pChart.getData().getDataSetByIndex(0);
            dataSet.setValues(values);
            // データ設定を通知
            pChart.getData().notifyDataChanged();
            pChart.notifyDataSetChanged();
        } else {
            // 初回のデータ設定
            // 凡例の定義
            String legendLabel = rsrc.getString(R.string.graph_min_text).replace("{0}", String.valueOf(DataStore.COUNT_MINUTES));
            dataSet = new LineDataSet(values, legendLabel);
            // グラフの色
            dataSet.setDrawIcons(false);
            dataSet.setColor(Color.parseColor("#50ADB5"));
            // グラフの太さ
            dataSet.setLineWidth(3f);
            // グラフの丸
            dataSet.setCircleColor(Color.parseColor("#50ADB5"));
            dataSet.setCircleRadius(1f);
            // グラフの塗り潰し
            dataSet.setDrawFilled(false);
            dataSet.setDrawCircleHole(false);
            dataSet.setValueTextSize(0f);
            dataSet.setFormLineWidth(1f);
            DashPathEffect dashPathEffect = new DashPathEffect(new float[]{10f, 5f}, 0f);
            dataSet.setFormLineDashEffect(dashPathEffect);
            // x軸下のラベル
            dataSet.setFormSize(0.f);
            dataSet.setFillColor(Color.BLUE);
            // データセットリストを作成
            List<ILineDataSet> dataSets = new ArrayList<ILineDataSet>();
            dataSets.add(dataSet);
            // LineDataを生成し、グラフに設定
            LineData lineData = new LineData(dataSets);
            pChart.setData(lineData);
        }
        return dataSet;
    }

    private int getIntFromString(SharedPreferences prefs, String key, int defaultValue){
        String value = prefs.getString(key, String.valueOf(defaultValue));
        return Integer.parseInt(value);
    }

    private void showMessage(final int message){
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
