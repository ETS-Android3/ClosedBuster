package jp.iflink.closed_buster.util;

import java.util.List;

import jp.iflink.closed_buster.model.SensorData;

public class CalculateUtil {
    private static final String TAG = "CalculateUtil";

    // センサーデータを集計して代表値を取り出す
    public static SensorData getSensorData(List<SensorData> sensorDataList){

        // 代表センサーデータの生成
        SensorData calculated = new SensorData();
        if (!sensorDataList.isEmpty()){
            // BDアドレスを設定
            calculated.setBdAddress(sensorDataList.get(0).getBdAddress());
        }

        int co2 = 0;
        int temperature = 0;
        int humidity = 0;
        int motion = 0;
        int barometer = 0;
        long datetime = 0;

        // 取得したco2濃度リストを取り出す
        for(int idx= 0; idx < sensorDataList.size(); idx++) {
            // 代表センサー値の取得（現状は最新の値）
            SensorData sensorData = sensorDataList.get(sensorDataList.size()-1);
            // CO2濃度
            co2 = sensorData.getCo2concentration();
            // 気温
            temperature = sensorData.getTemperature();
            // 湿度
            humidity = sensorData.getHumidity();
            // 人感
            motion = sensorData.getMotion();
            // 気圧
            barometer = sensorData.getBarometer();
            // 日時
            datetime = sensorData.getDatetime();
            // 抜ける
            break;
        }
        // モデルにセット
        calculated.setCo2concentration(co2);
        calculated.setTemperature(temperature);
        calculated.setHumidity(humidity);
        calculated.setMotion(motion);
        calculated.setBarometer(barometer);
        calculated.setDatetime(datetime);

        return calculated;
    }

}
