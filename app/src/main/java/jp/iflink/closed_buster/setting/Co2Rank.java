package jp.iflink.closed_buster.setting;

public enum Co2Rank {
    /** 低*/
    LOW("low"),
    /** 中 */
    MIDDLE("middle"),
    /** 高 */
    HIGH("high")
    ;
    private String type;

    Co2Rank(String type){
        this.type = type;
    }

    public String type(){
        return this.type;
    }

    public boolean isLow(){
        return this == LOW;
    }

    public boolean isMiddle(){
        return this == MIDDLE;
    }

    public boolean isHigh(){
        return this == HIGH;
    }

    public static Co2Rank judge(int co2, int high, int low){
        if (co2 <= 0){
            return null;
        }
        if (co2 >= high){
            return HIGH;
        }
        if (co2 >= low){
            return MIDDLE;
        }
        return LOW;
    }

    public static Co2Rank of(String type){
        for (Co2Rank value: Co2Rank.values()){
            if (value.type().equals(type)){
                return value;
            }
        }
        return null;
    }
}
