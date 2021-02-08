package jp.iflink.closed_buster.model;

import java.io.Serializable;

public class XmlSensor implements Serializable {
    /** sensor id */
    private int id;
    /** bd address */
    private String bdAddress;
    /** room **/
    private String room;


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

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }
}
