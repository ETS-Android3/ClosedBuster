package jp.iflink.closed_buster.util;

import android.os.Environment;
import android.util.Log;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;

import jp.iflink.closed_buster.BuildConfig;

public class LogUseUtil {
    private static final String TAG = "LogUseUtil";

    // ログレベルの設定値
    public int settingLogLevel = Log.VERBOSE;

    //コンストラクタ（引数無し）
    public LogUseUtil(){
        this(Log.DEBUG);
    }

    //コンストラクタ（ログ出力レベル）
    public LogUseUtil(int logLevel){
        this.settingLogLevel = logLevel;
    }

    //パッケージ名の取得
    public static String getPackageName(){
        return BuildConfig.APPLICATION_ID;
    }

    //verbose出力
    public void verbose(String tag, String outputText){
        //verboseのコンソール出力
        Log.v(tag, outputText);

        //verboseレベルの、ファイルへの出力
        if(checkOutputLog(Log.VERBOSE)){
            OutputLog(outputText, null);
        }
    }

    //debug出力
    public void debug(String tag, String outputText){
        //debugのコンソール出力
        Log.d(tag, outputText);

        //debugレベルの、ファイルへの出力
        if(checkOutputLog(Log.DEBUG)){
            OutputLog(outputText, null);
        }
    }

    //info出力
    public void info(String tag, String outputText){
        //infoのコンソール出力
        Log.i(tag, outputText);

        //infoレベルの、ファイルへの出力
        if(checkOutputLog(Log.INFO)){
            OutputLog(outputText, null);
        }
    }

    //warn出力
    public void warn(String tag, String outputText){
        //warnのコンソール出力
        Log.w(tag, outputText);

        //warnレベルの、ファイルへの出力
        if(checkOutputLog(Log.WARN)){
            OutputLog(outputText, null);
        }
    }

    //error出力
    public void error(String tag, String outputText){
        //errorのコンソール出力
        Log.e(tag, outputText);

        //infoレベルの、ファイルへの出力
        if(checkOutputLog(Log.ERROR)){
            OutputLog(outputText, null);
        }
    }

    //レベル指定、ファイル指定の自由出力
    public void specific(String tag, String outputText, String outputFileName, int outputLogLevel){
        // outputLogLevelは、VERBOSE～ERRORに準ずる

        //指定したログレベルでのコンソール出力
        specificConsole(tag, outputText, outputLogLevel);

        //指定したログレベルでのファイル出力
        if(checkOutputLog(outputLogLevel)){
            OutputLog(outputText, outputFileName);
        }
    }

    //指定したログレベルでのコンソール出力
    public static void specificConsole(String tag, String outputText, int outputLogLevel){
        switch (outputLogLevel){
            case Log.VERBOSE:
                //verboseのコンソール出力
                Log.v(tag, outputText);
                break;
            case Log.DEBUG:
                //debugのコンソール出力
                Log.d(tag, outputText);
                break;
            case Log.INFO:
                //infoのコンソール出力
                Log.i(tag, outputText);
                break;
            case Log.WARN:
                //warnのコンソール出力
                Log.w(tag, outputText);
                break;
            case Log.ERROR:
                //errorのコンソール出力
                Log.e(tag, outputText);
                break;
            default:
                //想定外のコンソール出力
                String newOutputText = outputText + ". This outputLogLevel is not defined. Please check outputLogLevel.";
                Log.d(tag, newOutputText);
        }
    }

    //ログのファイル出力
    public static void OutputLog(String outputText, String selectedFileName) {
        // 出力テキストの作成
        String newOutputText = createOutputText(outputText);
        // 出力ディレクトリの生成
        File directory = createOutputDirectory("logs");
        // 出力ファイル名の作成
        String fileName;
        if (selectedFileName != null){
            // ファイル名指定時、指定したファイル名で作成する
            fileName = createOutputLogFileName(selectedFileName);
        } else {
            // 未指定時は”outputLog”で作成する
            fileName = createOutputLogFileName("outputLog");
        }

        // 出力するファイルの生成
        File outputFile = new File(directory, fileName);
        if(!outputFile.exists()){
            createFile(outputFile);
        }

        try(
                // ファイル出力ストリームを生成
                FileOutputStream fos = new FileOutputStream(outputFile, true);
                // テキスト出力ストリームを生成
                BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos, "UTF-8"));
         ) {
            // テキスト出力
            bw.write(newOutputText);
            // 改行の出力
            bw.newLine();
            bw.flush();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    // ログファイルの出力フォルダの作成
    private static File createOutputDirectory(String dirName){
        // 出力するディレクトリのフルパスを作成
        String externalStorageDirectoryPath = Environment.getExternalStorageDirectory().getPath();
        String fullDirPath = externalStorageDirectoryPath + "/Android/data/" + getPackageName() + "/" + dirName;

        // ディレクトリが無い場合は作成
        File directory = new File(fullDirPath);
        if(!directory.exists()){
            directory.mkdirs();
        }

        return directory;
    }

//    //ログの出力ファイル名の作成
//    public String createOutputLogFileName(){
//        String newOutputLogFileName = "";
//
//        //ファイル名
//        String logFileName = "outputLog";
//        //出力日
//        Timestamp outputTime = new Timestamp(System.currentTimeMillis());
//        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
//        String strOutputTime = sdf.format(outputTime);
//
//        //newOutputLogFileName = logFileName + "_" + strOutputTime + ".text";
//        newOutputLogFileName = logFileName + "_" + strOutputTime + ".log";
//
//        return newOutputLogFileName;
//    }

    // ログの出力ファイル名の作成（ファイル名の指定アリ）
    public static String createOutputLogFileName(String baseName){
        // 現在日時を作成
        Timestamp outputTime = new Timestamp(System.currentTimeMillis());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String strOutputTime = sdf.format(outputTime);
        // ベース名の末尾に現在日時を追加
        String fileName = baseName + "_" + strOutputTime + ".log";
        return fileName;
    }

    //ファイル出力用のテキスト作成
    public static String createOutputText(String outputText){
        // 現在日時の作成
        Timestamp outputTimestamp = new Timestamp(System.currentTimeMillis());
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
        String strOutputTime = sdf.format(outputTimestamp);
        // 出力内容の先頭に現在日時を追加
        String newOutputText = strOutputTime + " , " + outputText;
        return newOutputText;
    }

//    //ディレクトリの作成処理
//    public void createDirectory(String fullPath){
//        File newFile = new File(fullPath);
//        try{
//            if (newFile.mkdir()){
//                System.out.println(fullPath + ": create dir OK");
//            }else{
//                System.out.println(fullPath + ": create dir NG");
//            }
//        }catch(Exception e){
//            System.out.println(fullPath + ": create dir Error");
//            System.out.println(e);
//        }
//    }

    //ファイルの作成処理
    public static void createFile(File file){
        try{
            if (file.createNewFile()){
                System.out.println(file.getPath() + ": create file OK");
            }else{
                System.out.println(file.getPath() + ": create file NG");
            }
        }catch(IOException e){
            System.out.println(file.getPath() + ": create file Error");
            Log.e(TAG, e.getMessage(), e);
        }
    }

//    //ファイルフラグの確認（チェックしてあるかどうかの確認）
//    public boolean checkExistFileFlag(){
//        boolean flag = false;
//
//
//        return flag;
//    }

//    //ファイルの存在チェック
//    public boolean checkExistFile(String fullPath){
//        boolean flag = false;
//        //ファイル作成
//        File file = new File(fullPath);
//        if (file.exists()){
//            System.out.println(fullPath + ": exist OK");
//            flag = true;
//        }else{
//            System.out.println(fullPath + ": exist NG");
//        }
//        return flag;
//    }

    //出力チェック
    public boolean checkOutputLog(int logLevel) {
        //設定レベルと比較し、設定値（出力レベル）以上の場合は出力される
        return (this.settingLogLevel <= logLevel);
    }

}
