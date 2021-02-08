package jp.iflink.closed_buster.model;

import java.io.Serializable;
import java.util.Date;

public class SensorData implements Serializable {

    private Date recordTime;

    public SensorData() {
    }

    public SensorData(SensorData sensorData, Date recordTime){
        setSensorData(sensorData, recordTime);
    }

    public void setSensorData(SensorData sensorData, Date recordTime){
        this.recordTime = recordTime;
        this.bdAddress = sensorData.bdAddress;
        this.co2concentration = sensorData.co2concentration;
        this.motion = sensorData.motion;
        this.temperature = sensorData.temperature;
        this.humidity = sensorData.humidity;
        this.barometer = sensorData.barometer;
        this.datetime = sensorData.datetime;
    }


    /** Sensor Data */

    /** time **/
    protected long datetime;

    /** id */
    protected int id;
    /** bd address */
    protected String bdAddress;
    /** co2 concentration **/
    protected int co2concentration;
    /** temperature **/
    protected int temperature;
    /** humidity **/
    protected int humidity;
    /** motion **/
    protected int motion;
    /** barometer **/
    protected int barometer;

    public Date getRecordTime() {
        return recordTime;
    }

    public void setRecordTime(Date recordTime) {
        this.recordTime = recordTime;
    }

    public long getDatetime() {
        return datetime;
    }

    public void setDatetime(long datetime) {
        this.datetime = datetime;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getBdAddress() {
        return bdAddress;
    }

    public void setBdAddress(String bdAddress) {
        this.bdAddress = bdAddress;
    }

    public int getCo2concentration() {
        return co2concentration;
    }

    public void setCo2concentration(int co2concentration) {
        this.co2concentration = co2concentration;
    }

    public int getTemperature() {
        return temperature;
    }

    public void setTemperature(int temperature) {
        this.temperature = temperature;
    }

    public int getHumidity() {
        return humidity;
    }

    public void setHumidity(int humidity) {
        this.humidity = humidity;
    }

    public int getMotion() {
        return motion;
    }

    public void setMotion(int motion) {
        this.motion = motion;
    }

    public int getBarometer() {
        return barometer;
    }

    public void setBarometer(int barometer) {
        this.barometer = barometer;
    }

}
