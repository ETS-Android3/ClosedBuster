package jp.iflink.closed_buster.model;

public class SensorListItem {
    /* センサーID */
    private Integer sensorId;
    /* BDアドレス */
    private String bdAddress;
    /* ルーム名 */
    private String room;

    /**
     * コンストラクタ
     * @param sensorId センサーID
     * @param bdAddress BDアドレス
     * @param room ルーム名
     */
    public  SensorListItem(Integer sensorId, String bdAddress, String room) {
        this.sensorId = sensorId;
        this.bdAddress = bdAddress;
        this.room = room;
    }

    /**
     * 空のコンストラクタ
     */
    public SensorListItem() {};

    @Override
    public String toString(){
        return (sensorId!=null?sensorId.toString():"-") +"\t"+ bdAddress +"\t"+ (room!=null?room.toString():"");
    }

    public Integer getSensorId() {
        return sensorId;
    }

    public void setSensorId(Integer sensorId) {
        this.sensorId = sensorId;
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