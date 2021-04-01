package jp.iflink.closed_buster.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.util.Xml;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

import jp.iflink.closed_buster.R;
import jp.iflink.closed_buster.model.SensorInfo;
import jp.iflink.closed_buster.model.SensorListItem;
import jp.iflink.closed_buster.model.SensorListViewModel;
import jp.iflink.closed_buster.task.BleScanTask;
import jp.iflink.closed_buster.util.LogUseUtil;
import jp.iflink.closed_buster.util.XmlUtil;

/**
 * センサー管理画面
 * A simple {@link Fragment} subclass.
 * Use the {@link SensorSettingsFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class SensorSettingsFragment extends Fragment {
    private static final String TAG = "SensorSettings";
    // 最大センサー数
    private int maxSensors;
    // ボタン
    private Button detectButton;
    private Button registerButton;
    // リストビュー
    private ListView detectedSensorsView;
    // ブロードキャストマネージャ
    private LocalBroadcastManager broadcastMgr;
    // アダプタ
    private SensorListAdapter adapter;
    private DetectedSensorListAdapter detectedAdapter;
    // データ更新通知用
    private SensorListViewModel sensorListViewModel;
    // 検出したセンサーのBDアドレスセット
    private static Set<String> detectedSensorBdAddressSet = Collections.synchronizedSet(new LinkedHashSet<String>());

    public SensorSettingsFragment() {
        // 空コンストラクタが必要
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param maxSensors 最大センサー数
     * @return A new instance of fragment SensorSettingsFragment.
     */
    public static SensorSettingsFragment newInstance(int maxSensors) {
        SensorSettingsFragment fragment = new SensorSettingsFragment();
        Bundle args = new Bundle();
        args.putInt("maxSensors", maxSensors);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        if (args != null) {
            maxSensors = args.getInt("maxSensors");
        }
        // ブロードキャストマネージャを生成
        this.broadcastMgr = LocalBroadcastManager.getInstance(getContext());
    }

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView");

        // 縦画面に固定
        //getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        // root　viewを取得
        View root = inflater.inflate(R.layout.fragment_sensor_settings, container, false);

        // CO2センサー定義Xmlの読込
        List<SensorInfo> xmlSensorList = XmlUtil.readXml(this.getContext());


        // 設定済みセンサービューに表示する要素を生成
        final List<SensorListItem> dataSource = new ArrayList<>(xmlSensorList.size());
        for (SensorInfo sensor : xmlSensorList) {
            SensorListItem item = new SensorListItem(sensor.getId(), sensor.getBdAddress(), sensor.getRoom());
            dataSource.add(item);
        }
        // アダプタを生成し、設定済みセンサービューに設定
        this.adapter = new SensorListAdapter(R.layout.sensorlist_item, getResources());
        // 設定済みセンサービューを取得
        RecyclerView sensorsView = root.findViewById(R.id.rv_sensors);
        sensorsView.setAdapter(adapter);
        sensorsView.setHasFixedSize(false);
        sensorsView.setLayoutManager(new LinearLayoutManager(getActivity()));
        // 設定済みセンサービューのデータソースを設定
        adapter.submitList(dataSource);

        // 設定済みセンサービューのアイテムのUI操作を定義
        ItemTouchHelper itemDecor = new ItemTouchHelper(new ItemTouch(adapter, dataSource));
        itemDecor.attachToRecyclerView(sensorsView);

        // データ更新通知用オブジェクトを生成
        sensorListViewModel = new ViewModelProvider(this).get(SensorListViewModel.class);
        sensorListViewModel.getUserInput().observe(getViewLifecycleOwner(), new Observer<SensorListItem>() {
            @Override
            public void onChanged(@Nullable final SensorListItem userInput) {
                //List<SensorListItem> list = new ArrayList<>(adapter.getCurrentList());
                // センサーID採番
                userInput.setSensorId(dataSource.size()+1);
                // 設定済みセンサービューに追加して通知
                //list.add(userInput);
                dataSource.add(userInput);
                adapter.submitList(dataSource);
                adapter.notifyItemInserted(dataSource.size()-1);
            }
        });

        // ボタンのイベントハンドラ定義
        this.detectButton = setupDetectButton(root);
        this.registerButton = setupRegisterButton(root);

        // 検出されたセンサーリストを取得
        List<SensorListItem> detectedSensorList = findDetectedSensorList();

        // 検出されたセンサービューを取得
        this.detectedSensorsView = root.findViewById(R.id.lv_detected_sensors);
        // アダプタを生成し、検出されたセンサービューに設定
        this.detectedAdapter = new DetectedSensorListAdapter(getActivity(), R.layout.detected_sensorlist_item, detectedSensorList);
        detectedAdapter.setSensorListViewModel(sensorListViewModel);
        detectedSensorsView.setAdapter(detectedAdapter);

        if (!detectedSensorList.isEmpty()){
            // 1件目に検出されたセンサーを対象に、ルーム名入力のダイアログを表示
            View sensor1 = detectedAdapter.getView(0, null, detectedSensorsView);
            sensor1.callOnClick();
        }

        // root viewを返却
        return root;
    }

    public void addDetectedSensorBdAddressList(final List<String> addressList){
        // 検出したセンサーのBDアドレスセットにすべて追加
        detectedSensorBdAddressSet.addAll(addressList);
    }

    private List<SensorListItem> findDetectedSensorList(){
        List<SensorListItem> sensorList = new ArrayList<>();
        for (String bdAddress : detectedSensorBdAddressSet){
            SensorListItem item = new SensorListItem(null, bdAddress, null);
            sensorList.add(item);
        }
        return sensorList;
    }

    private Button setupDetectButton(View root){
        Button button = root.findViewById(R.id.btn_detect);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // 検出されたセンサーリストを取得
                List<SensorListItem> sensorList = findDetectedSensorList();
                // センサーリストを画面に追加
                detectedAdapter.update(sensorList);
                // 検知完了メッセージ表示
                if (!sensorList.isEmpty()){
                    showLongMessage(getResources().getString(R.string.msg_detected_sensors)
                            .replace("{0}", String.valueOf(sensorList.size())));
                } else {
                    showShortMessage(getResources().getString(R.string.msg_no_sensors_detected));
                }
            }
        });
        return button;
    }

    private Button setupRegisterButton(final View root){
        Button button = root.findViewById(R.id.btn_register);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // データソースを取得
                final List<SensorListItem> dataSource = adapter.getCurrentList();
//                if (dataSource.isEmpty()){
//                    // 登録対象無しメッセージ表示
//                    showShortMessage(getResources().getString(R.string.msg_no_sensors_registered));
//                    return;
//                }
                // 登録確認ダイアログ表示
                new AlertDialog.Builder(root.getContext())
                        .setTitle(R.string.title_confirm_register)
                        .setMessage(R.string.msg_confirm_register)
                        .setIcon(android.R.drawable.ic_dialog_info)
                        .setPositiveButton(
                                "OK",
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        // センサーIDを振り直し
                                        int sensorId = 1;
                                        for (SensorListItem item : dataSource){
                                            item.setSensorId(sensorId);
                                            sensorId++;
                                        }
                                        // XMLファイルに書き込み ※データがない場合はファイルを削除
                                        if (dataSource.isEmpty()) {
                                            XmlUtil.deleteXml(getContext());
                                        } else {
                                            XmlUtil.writeXml(getContext(), dataSource);
                                        }

                                        // センサー定義XMLファイルの設定変更を通知
                                        Intent intent = new Intent(BleScanTask.ACTION_CHANGE_CONFIG);
                                        intent.putExtra("CONFIG", BleScanTask.CONFIG_SENSOR_XML);
                                        broadcastMgr.sendBroadcast(intent);
                                        // 登録されたBDアドレスを、検出したセンサーのBDアドレスセットから削除
                                        for (SensorListItem item : dataSource){
                                            detectedSensorBdAddressSet.remove(item.getBdAddress());
                                        }
                                        // 登録完了メッセージ表示
                                        showLongMessage(getResources().getString(R.string.msg_registered_sensors));
                                    }
                                })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        return button;
    }

    private class ItemTouch extends ItemTouchHelper.SimpleCallback {
        private final SensorListAdapter adapter;
        private final List<SensorListItem> dataSource;
        private int dragFrom = -1;
        private int dragTo = -1;

        public ItemTouch(SensorListAdapter adapter, List<SensorListItem> dataSource) {
            super(0, 0);
            this.adapter = adapter;
            this.dataSource = dataSource;
        }

        @Override
        public int getSwipeDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            // 選択状態でないとスワイプさせないようにする
            return viewHolder.itemView.isSelected()? ItemTouchHelper.RIGHT:0;
        }

        @Override
        public int getDragDirs(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
            // 選択状態でないとドラッグ開始させないようにする
            return viewHolder.itemView.isSelected()? ItemTouchHelper.UP | ItemTouchHelper.DOWN:0;
        }

        @Override
        public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
            // ドラッグ開始した位置を取得
            final int fromPos = viewHolder.getAdapterPosition();
            if(dragFrom == -1) {
                dragFrom =  fromPos;
            }
            // ドラッグ中の位置を取得
            final int toPos = target.getAdapterPosition();
            dragTo = toPos;

            // アダプタに移動を通知
            adapter.notifyItemMoved(fromPos, toPos);

            return true;
        }

        @Override
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
            super.clearView(recyclerView, viewHolder);
            if(dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
                // ドラッグ終了位置が確定した為、入れ替え処理を呼び出す
                doItemMove(dragFrom, dragTo);

                //選択状態の解除
                viewHolder.itemView.setBackgroundColor(0);
                viewHolder.itemView.setSelected(false);
            }
            // ドラッグ位置の初期化
            dragFrom = -1;
            dragTo = -1;
        }

        public boolean doItemMove(int dragFrom, int dragTo) {
            // データソースの要素を入れ替え
            dataSource.add(dragTo, dataSource.remove(dragFrom));
            // センサーIDを振り直し
            int sensorId = 1;
            for (SensorListItem item : dataSource){
                item.setSensorId(sensorId);
                sensorId++;
            }
            // アダプタにデータ変更を通知
            adapter.notifyDataSetChanged();
            return true;
        }

        @Override
        public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
            // スワイプした位置を取得
            final int fromPos = viewHolder.getAdapterPosition();
            if (dataSource.size() > fromPos){
                // データソースから要素を削除
                dataSource.remove(fromPos);
            }
            // センサーIDを振り直し
            int sensorId = 1;
            for (SensorListItem item : dataSource){
                item.setSensorId(sensorId);
                sensorId++;
            }
            // アダプタにデータ変更を通知
            adapter.notifyItemRemoved(fromPos);
            adapter.notifyDataSetChanged();
            // 削除完了メッセージ表示
            showLongMessage(getResources().getString(R.string.msg_deleted_sensor));
        }
    }

    private void showShortMessage(final String message){
        showMessage(message, Toast.LENGTH_SHORT);
    }

    private void showLongMessage(final String message){
        showMessage(message, Toast.LENGTH_LONG);
    }

    private void showMessage(final String message, final int durationType){
        getActivity().runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(getContext(), message, durationType).show();
            }
        });
    }
}
