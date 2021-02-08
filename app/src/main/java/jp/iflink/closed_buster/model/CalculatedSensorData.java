package jp.iflink.closed_buster.model;

import java.io.Serializable;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class CalculatedSensorData extends SensorData implements Serializable {

    private Date calculatedTime;

    // センサーデータリスト
    //private List<SensorData> sensorDataList = Collections.synchronizedList(new LinkedList<SensorData>());
    private List<SensorData> sensorDataList = new LinkedList<SensorData>();

    public CalculatedSensorData() {
    }

    public CalculatedSensorData(SensorData sensorData, Date calculatedTime){
        super(sensorData, calculatedTime);
        this.calculatedTime = calculatedTime;
    }

    public void setCalculatedSensorData(SensorData sensorData, Date calculatedTime){
        setSensorData(sensorData, calculatedTime);
        this.calculatedTime = calculatedTime;
    }

    public Date getCalculatedTime() {
        return calculatedTime;
    }

    public void setCalculatedTime(Date calculatedTime) {
        this.calculatedTime = calculatedTime;
    }

    public List<SensorData> getSensorDataList() {
        return sensorDataList;
    }

    public void setSensorDataList(List<SensorData> sensorDataList) {
        this.sensorDataList = sensorDataList;
    }
}
