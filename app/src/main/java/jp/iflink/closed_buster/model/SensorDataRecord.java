package jp.iflink.closed_buster.model;

import java.util.Date;

public class SensorDataRecord {

    public SensorDataRecord(){}

    public SensorDataRecord(SensorData data, Date recordTime){
        this.recordTime = recordTime;
        this.bdAddress = data.bdAddress;
        this.co2concentration = data.co2concentration;
        this.temperature = data.temperature;
        this.humidity = data.humidity;
        this.motion = data.motion;
        this.barometer = data.barometer;
    }

    // 記録時刻
    public Date recordTime;
    /** BDアドレス */
    public String bdAddress;
    /** CO2濃度 **/
    public int co2concentration;
    /** 気温 **/
    public int temperature;
    /** 湿度 **/
    public int humidity;
    /** 人感 **/
    public int motion;
    /** 気圧 **/
    public int barometer;
}
