package jp.iflink.closed_buster.model;

import jp.iflink.closed_buster.setting.Co2Rank;

public class SensorInfo extends XmlSensor {
    /** last dateTime */
    private long lastDateTime;
    /** last Co2Rank */
    private Co2Rank lastCo2Rank;
    /** last motionTime */
    private long lastMotionTime;
    /** last motion */
    private int lastMotion;

    public long getLastDateTime() {
        return lastDateTime;
    }

    public void setLastDateTime(long lastDateTime) {
        this.lastDateTime = lastDateTime;
    }

    public Co2Rank getLastCo2Rank() {
        return lastCo2Rank;
    }

    public void setLastCo2Rank(Co2Rank lastCo2Rank) {
        this.lastCo2Rank = lastCo2Rank;
    }

    public long getLastMotionTime() {
        return lastMotionTime;
    }

    public void setLastMotionTime(long lastMotionTime) {
        this.lastMotionTime = lastMotionTime;
    }

    public int getLastMotion() {
        return lastMotion;
    }

    public void setLastMotion(int lastMotion) {
        this.lastMotion = lastMotion;
    }
}
