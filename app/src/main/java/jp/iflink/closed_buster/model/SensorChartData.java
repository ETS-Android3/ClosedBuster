package jp.iflink.closed_buster.model;

import java.io.Serializable;
import java.util.Date;

public class SensorChartData implements Serializable {

    /** 時系列の基準日時 **/
    public Date baseDate;
    /** 時系列の間隔[分] */
    public int intervalMinutes;
    /** BDアドレス **/
    public String bdAddress;
    /** 時系列のCO2濃度 **/
    public int[] co2Data;

    public SensorChartData() {
    }

}
