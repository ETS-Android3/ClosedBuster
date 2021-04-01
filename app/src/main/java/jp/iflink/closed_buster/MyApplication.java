package jp.iflink.closed_buster;

import android.app.Application;
import android.content.Context;

/**
 * context を任意の場所から取得するためのクラス
 */
public class MyApplication extends Application {
    private static Context context;

    public void onCreate() {
        super.onCreate();
        MyApplication.context = getApplicationContext();
    }

    // このメソッドを使ってcontextを取得する
    public static Context getAppContext(){
        return MyApplication.context;
    }

}
