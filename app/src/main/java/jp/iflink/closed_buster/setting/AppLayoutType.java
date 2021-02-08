package jp.iflink.closed_buster.setting;

public enum AppLayoutType {
    /** スマートフォン */
    SMARTPHONE("smp"),
    /** 8インチタブレット */
    TABLET_8("tablet8"),
    /** 10インチタブレット */
    TABLET_10("tablet10")
    ;
    private String type;

    AppLayoutType(String type){
        this.type = type;
    }

    public String type(){
        return this.type;
    }

    public static AppLayoutType judge(String type){
        if (SMARTPHONE.type.equals(type)){
            return SMARTPHONE;
        }
        if (TABLET_8.type.equals(type)){
            return TABLET_8;
        }
        return TABLET_10;
    }

    public static AppLayoutType of(String type){
        for (AppLayoutType value: AppLayoutType.values()){
            if (value.type().equals(type)){
                return value;
            }
        }
        return null;
    }
}
