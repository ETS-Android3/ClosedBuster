package jp.iflink.closed_buster.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import jp.iflink.closed_buster.R;
import jp.iflink.closed_buster.model.SensorListItem;
import jp.iflink.closed_buster.model.SensorListViewModel;

public class DetectedSensorListAdapter extends ArrayAdapter<SensorListItem> {
    // リソースID
    private int mResourceId;
    // リストビューの要素
    private List<SensorListItem> mItems;
    // 追加済みBDアドレスリスト
    private List<String> mAddedBdAddressList;
    // View生成オブジェクト
    private LayoutInflater mInflater;
    // データ更新通知用
    private SensorListViewModel sensorListViewModel;

    /**
     * コンストラクタ
     * @param context コンテキスト
     * @param resourceId リソースID
     * @param items リストビューの要素
     */
    public DetectedSensorListAdapter(Context context, int resourceId, List<SensorListItem> items) {
        super(context, resourceId, items);

        // プロパティを設定
        this.mResourceId = resourceId;
        this.mItems = items;
        this.mAddedBdAddressList = new ArrayList<>();
        this.mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    public void setSensorListViewModel(SensorListViewModel sensorListViewModel){
        this.sensorListViewModel = sensorListViewModel;
    }

    public void update(List<SensorListItem> sensorList){
        clear();
        for (Iterator<SensorListItem> it=sensorList.iterator(); it.hasNext();){
            SensorListItem item = it.next();
            if (mAddedBdAddressList.contains(item.getBdAddress())){
                // 既に追加済みの場合は削除する
                it.remove();
            }
        }
        if (!sensorList.isEmpty()){
            addAll(sensorList);
        }
    }

    // 選択したBDアドレス
    private static String selectedBdAddress = null;

    @Override
    public View getView(final int position, View convertView, final ViewGroup parent) {
        View view;

        if (convertView != null) {
            view = convertView;
        } else {
            view = mInflater.inflate(mResourceId, null);
        }

        // 選択状態の初期化
        view.setBackgroundColor(view.getResources().getColor(R.color.white));
        view.setSelected(false);

        // リストビューに表示する要素を取得
        final SensorListItem item = mItems.get(position);
        if (item != null){
            // BDアドレスを設定
            TextView bdAddressView = view.findViewById(R.id.bdAddress);
            bdAddressView.setText(item.getBdAddress());
        }

        // 選択状態の初期化
        if(item.getBdAddress().equalsIgnoreCase(selectedBdAddress)) {
            // 選択したBDアドレスの行の背景色を設定
            view.setBackgroundColor(view.getResources().getColor(R.color.activeRow));
            view.setSelected(true);
        }else{
            // 選択したBDアドレスの行の背景色をクリア
            view.setBackgroundColor(view.getResources().getColor(R.color.white));
            view.setSelected(false);
        }

        // リソース取得
        final Resources rsrc = getContext().getResources();
        // リストビューをタップした時のイベントハンドラを定義
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                for (int idx=0; idx<parent.getChildCount(); idx++){
                    View child = parent.getChildAt(idx);
                    if (child == view){
                        continue;
                    }
                    // 選択した行以外の背景色をクリア
                    parent.getChildAt(idx).setBackgroundColor(view.getResources().getColor(R.color.white));
                    parent.getChildAt(idx).setSelected(false);
                }
                // 選択した行の背景色を設定
                view.setBackgroundColor(view.getResources().getColor(R.color.activeRow));
                view.setSelected(true);

                if (!item.getBdAddress().equalsIgnoreCase(selectedBdAddress)){
                    // 選択したBDアドレスを保持
                    selectedBdAddress = item.getBdAddress();
                    // 最初の行選択は、背景色を変えるだけ
                    return;
                }
                // ダイアログを準備
                View root = mInflater.inflate(R.layout.fragment_dialog_input, null);
                // ダイアログのテキスト入力欄をクリア
                final EditText roomTxt = root.findViewById(R.id.txt_room);
                roomTxt.setText("");
                // タイトルを作成
                final String title = rsrc.getString(R.string.input_room_name_add).replace("{0}", item.getBdAddress());
                // ルーム名の入力ダイアログを表示する
                new AlertDialog.Builder(root.getContext())
                        .setTitle(title)
                        //.setIcon(R.drawable.icon)
                        .setView(root)
                        .setPositiveButton(
                                "OK",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        // ルーム名を取得、設定
                                        String room = roomTxt.getText().toString();
                                        item.setRoom(room);
                                        // アイテムの追加を通知
                                        sensorListViewModel.getUserInput().postValue(item);
                                        // 追加済みBDアドレスリストに追加
                                        mAddedBdAddressList.add(item.getBdAddress());
                                        // リストビューから削除して変更を通知
                                        remove(item);
                                        notifyDataSetChanged();
                                        // 追加完了メッセージ表示
                                        showShortMessage(rsrc.getString(R.string.msg_added_sensor));
                                    }
                                })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });

        return view;
    }

    private void showShortMessage(final String message){
        showMessage(message, Toast.LENGTH_SHORT);
    }

    private void showLongMessage(final String message){
        showMessage(message, Toast.LENGTH_LONG);
    }

    private void showMessage(final String message, final int durationType){
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                Toast.makeText(getContext(), message, durationType).show();
            }
        });
    }
}