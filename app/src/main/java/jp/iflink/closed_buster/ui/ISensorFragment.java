package jp.iflink.closed_buster.ui;

import java.util.Date;
import java.util.Map;

import jp.iflink.closed_buster.model.SensorData;

public interface ISensorFragment {
    void drawSensorData(Map<String, SensorData> sensorData, Date lastDataTime, boolean drawUnknown);
    void reloadXmlSensorList();
}
