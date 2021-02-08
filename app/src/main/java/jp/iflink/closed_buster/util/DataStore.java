package jp.iflink.closed_buster.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;

import jp.iflink.closed_buster.model.SensorData;
import jp.iflink.closed_buster.model.SensorChartData;
import jp.iflink.closed_buster.model.SensorDataRecord;

public class DataStore {
    private static final String TAG = "DataStore";
    // グラフの表示期間
    public static final int COUNT_MINUTES = 60;
    // 各グラフの表示間隔
    public static final int INTERVAL_MINUTES = 5;
    // 全センサーデータマップ
    private Map<String, SensorChartData> sensorDataMap = new HashMap<>();
    // ファイルハンドラ
    private FileHandler fileHandler;
    // ベースファイル名
    private static final String BASE_FILE_NAME = "sensorData";
    // タイムスタンプの書式
    private static final String TIMESTAMP_PATTERN = "yyyy-MM-dd'T'HH:mm:ss";
    // アプリ共通設定
    private SharedPreferences prefs;
    // リソース
    private Resources rsrc;

    public DataStore(Context context, SharedPreferences prefs){
        this.fileHandler = new FileHandler(context);
        this.prefs = prefs;
        this.rsrc = context.getResources();
    }

    public void init() {
        // データの初期化
        initData();
    }

    // ファイル名の作成
    public String createFileName(String bdAddress, Date date){
        // BDアドレスから 「:」を除去する
        String replacedBDAddress = bdAddress.replace(":", "");
        // ベースファイル名＋BDアドレス＋日付（yyyyMMdd）でファイル名を生成
        String fileName = String.format("%s_%s_%tY%<tm%<td.txt", BASE_FILE_NAME, replacedBDAddress, date);
        return fileName;
    }

    public File writeRecord(String bdAddress, SensorData data, Date recordTime) throws IOException {
        List<SensorDataRecord> recordList;
        // データファイルの取得
        String fileName = createFileName(bdAddress, recordTime);
        File file = fileHandler.getFile(fileName);
        // データファイルの存在チェック
        if (file.exists() && file.canRead()){
            // 既にデータファイルが存在する場合はすべて読み込み
            recordList = load(file, bdAddress, recordTime);
        } else {
            recordList = new ArrayList<>();
        }
        // レコードを作成
        SensorDataRecord record = new SensorDataRecord(data, recordTime);
        //  リストに追加
        recordList.add(record);
        // リストを保存
        return save(file, recordList);
    }

    public List<SensorDataRecord> readRecordList(String bdAddress, Date date) throws IOException {
        // データファイルの取得
        String fileName = createFileName(bdAddress, date);
        File file = fileHandler.getFile(fileName);
        return load(file, bdAddress, date);
    }

    public List<SensorDataRecord> load(File file, String bdAddress, Date date) throws IOException {
        SimpleDateFormat dtFmt = new SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US);
        List<SensorDataRecord> recordList = new ArrayList<>();
        // データファイルの存在チェック
        if (file.exists() && file.canRead()){
            try(BufferedReader reader = new BufferedReader(new FileReader(file))) {
                // ヘッダ行を読み飛ばす
                String line = reader.readLine();
                //  データ行をすべて読み込む
                while((line = reader.readLine()) != null){
                    String[] columns = line.split(",");
                    if (columns.length != 6 && columns.length != 7){
                        continue;
                    }
                    try {
                        // レコードを作成
                        SensorDataRecord record = new SensorDataRecord();
                        record.recordTime  = dtFmt.parse(columns[0]);
                        record.bdAddress = columns[1];
                        record.co2concentration = Integer.parseInt(columns[2]);
                        record.temperature = Integer.parseInt(columns[3]);
                        record.humidity = Integer.parseInt(columns[4]);
                        record.motion = Integer.parseInt(columns[5]);
                        if (columns.length >= 7) {
                            record.barometer = Integer.parseInt(columns[6]);
                        } else {
                            record.barometer = 0;
                        }
                        //  リストに追加
                        recordList.add(record);
                    } catch (ParseException e){
                        Log.e(TAG, e.getMessage(), e);
                    }
                }
            } catch (FileNotFoundException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        }
        return recordList;
    }

    protected File save(File file, List<SensorDataRecord> recordList) throws IOException {
        SimpleDateFormat dtFmt = new SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US);
        // データファイルの存在チェック
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            // ヘッダ行を出力
            writer.write("recordTime,bdAddress,co2,temperature,humidity,motion,barometer");
            writer.newLine();
            // データ行をすべて出力
            for (SensorDataRecord record : recordList){
                writer.write(dtFmt.format(record.recordTime) + ",");
                writer.write(record.bdAddress+ ",");
                writer.write(record.co2concentration + ",");
                writer.write(record.temperature + ",");
                writer.write(record.humidity + ",");
                writer.write(record.motion + ",");
                writer.write(record.barometer + "");
                writer.newLine();
            }
        }
        return file;
    }

    public Date update(String bdAddress, List<SensorDataRecord> recordList, Date baseDate){
        // 読込終了時刻（5分刻みの時刻）を設定
        Date toDate = getFiveMinutesDate(baseDate);
        // 読込終了時刻からグラフの表示期間遡り、読込開始時刻を設定
        Date fromDate = getFromDateByMinutes(toDate, COUNT_MINUTES);
        // 最終更新時刻
        Date prevRecordTime = null;
        // 表示データリスト
        List<SensorData> viewDataList = new ArrayList<>();
        // 折れ線グラフの点の数
        int numOfPoints = COUNT_MINUTES / INTERVAL_MINUTES;
        // 時系列のCO2濃度を初期化
        int[] co2Data = new int[numOfPoints];
        for (int idx=0; idx<co2Data.length; idx++){
            co2Data[idx] = -1;
        }
        // 読込開始時刻から読込終了時刻まで各グラフの表示間隔毎にループする
        for (int idx=0; idx<co2Data.length; idx++){
            // 次の読込開始時刻を設定
            Calendar nextDateCal = Calendar.getInstance();
            nextDateCal.setTime(fromDate);
            nextDateCal.add(Calendar.MINUTE, INTERVAL_MINUTES);
            // リストを後ろから逆順にループ
            for (ListIterator<SensorDataRecord> it = recordList.listIterator(recordList.size()); it.hasPrevious();){
                // 記録データを取得
                SensorDataRecord record = it.previous();
                if (record.recordTime == null){
                    // 記録時刻が無いデータはスキップ
                    continue;
                }
                //　読込開始時刻～次の読込開始時刻の範囲内にデータを検索
                if (!record.recordTime.before(fromDate) && record.recordTime.before(nextDateCal.getTime())){
                    // 最終更新時刻を取得
                    prevRecordTime = record.recordTime;
                    // CO2濃度を設定
                    co2Data[idx] = record.co2concentration;
                    break;
                }
            }
            if (co2Data[idx] == -1){
                // データが無い場合は、１つ前の値と同じにする
                co2Data[idx] = (idx > 0)? co2Data[idx-1] : 0;
            }
            // 読込開始時刻を更新
            fromDate = nextDateCal.getTime();
        }
        // センサーグラフデータを作成
        SensorChartData chartData = new SensorChartData();
        chartData.baseDate = toDate;
        chartData.intervalMinutes = INTERVAL_MINUTES;
        chartData.bdAddress = bdAddress;
        chartData.co2Data = co2Data;
        // 全センサーデータマップを更新
        sensorDataMap.put(bdAddress, chartData);
        // 最終更新時刻を返却
        return prevRecordTime;
    }

    private void initData(){
        // グラフデータの初期化
        sensorDataMap.clear();
    }

    public SensorChartData getSensorData(String bdAddress) {
        return sensorDataMap.get(bdAddress);
    }

    private static Date getFiveMinutesDate(Date baseDate){
        Calendar cal = Calendar.getInstance();
        cal.setTime(baseDate);
        // 5分単位の時刻に調整する
        int tenMinutes = (cal.get(Calendar.MINUTE)/5)*5;
        cal.set(Calendar.MINUTE, tenMinutes);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private static Date getFromDateByMinutes(Date toDate, int minutes){
        Calendar cal = Calendar.getInstance();
        cal.setTime(toDate);
        // 秒[SECOND]以下をクリア
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        // 指定した分[MINUTE]数を減算
        cal.add(Calendar.MINUTE, -minutes);
        return cal.getTime();
    }

    private int getIntFromString(SharedPreferences prefs, String key, int defaultValue){
        String value = prefs.getString(key, String.valueOf(defaultValue));
        return Integer.parseInt(value);
    }
}
