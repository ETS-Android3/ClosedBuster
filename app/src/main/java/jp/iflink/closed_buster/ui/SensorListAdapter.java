package jp.iflink.closed_buster.ui;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import java.util.ArrayList;
import java.util.List;

import jp.iflink.closed_buster.R;
import jp.iflink.closed_buster.model.SensorListItem;
import jp.iflink.closed_buster.model.SensorListItemViewHolder;

public class SensorListAdapter extends ListAdapter<SensorListItem, SensorListItemViewHolder> {
    // リソースID
    private int mResourceId;
//    // ソート用アイコン画像データ
//    private Bitmap sortIcon;
    // View生成オブジェクト
    private LayoutInflater mInflater;

    /**
     * コンストラクタ
     */
    public SensorListAdapter(int mResourceId, Resources resources) {
        // 要素の比較用メソッドの定義
        super(new DiffUtil.ItemCallback<SensorListItem>(){
            @Override
            public boolean areItemsTheSame(@NonNull SensorListItem oldItem, @NonNull SensorListItem newItem) {
                return oldItem.getBdAddress().equals(newItem.getBdAddress());
            }
            @Override
            public boolean areContentsTheSame(@NonNull SensorListItem oldItem, @NonNull SensorListItem newItem) {
                return oldItem.toString().equals(newItem.toString());
            }
        });
        // プロパティを設定
        this.mResourceId = mResourceId;
        //this.sortIcon = BitmapFactory.decodeResource(resources, android.R.drawable.ic_menu_sort_by_size);
    }

    @NonNull
    @Override
    public SensorListItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // View生成オブジェクトの取得
        this.mInflater = LayoutInflater.from(parent.getContext());
        // Viewの生成
        View itemView = mInflater.inflate(mResourceId, parent, false);
        // ViewHolderの生成
        SensorListItemViewHolder viewHolder = new SensorListItemViewHolder(itemView);

        itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ViewGroup parent = (ViewGroup) view.getParent();
                // 最初に行選択時、行の背景色を変更
                for (int idx = 0; idx < parent.getChildCount(); idx++) {
                    View child = parent.getChildAt(idx);
                    if (child == view){
                        continue;
                    }
                    // 選択した行以外の背景色をクリア
                    child.setBackgroundColor(view.getResources().getColor(R.color.white));
                    child.setSelected(false);
                }
                // 選択した行の背景色を設定
                view.setBackgroundColor(view.getResources().getColor(R.color.activeRow));

                if (!view.isSelected()) {
                    // 最初の行選択は、背景色を変えるだけ
                    view.setSelected(true);
                    return;
                }else{
                int count = -1;
                for (int idx = 0; idx < parent.getChildCount(); idx++) {
                    if(parent.getChildAt(idx).isSelected()) {
                        count = idx;
                        break;
                    }
                }
                if(count == -1) {
                    //想定外の選択のため、後処理をスキップ
                    return;
                }

                final int position = count;

                // リソース取得
                final Resources rsrc = view.getResources();

                List<SensorListItem> mDataSource = getCurrentList();
                final SensorListItem dataItem = mDataSource.get(position);

                // ダイアログを準備
                View root = mInflater.inflate(R.layout.fragment_dialog_input, null);
                // テキスト入力欄にルーム名を設定
                final EditText roomTxt = root.findViewById(R.id.txt_room);
                roomTxt.setText(dataItem.getRoom());

                final TextView roomTextView = view.findViewById(R.id.room);

                // タイトルを作成
                final String title = rsrc.getString(R.string.input_room_name).replace("{0}", dataItem.getBdAddress());
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
                                        // ルーム名を取得して設定
                                        String room = roomTxt.getText().toString();
                                        TextView roomView = roomTextView;
                                        roomView.setText(room);
                                        dataItem.setRoom(room);
                                        // ルーム名の変更を通知
                                        notifyItemChanged(position);
                                    }
                                })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
            }


        });



        // Viewの返却
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(@NonNull final SensorListItemViewHolder holder, final int position) {
        // リストビューに表示するデータ要素を取得
        List<SensorListItem> mDataSource = getCurrentList();
        final SensorListItem dataItem = mDataSource.get(position);
        if (dataItem != null){
            // データ要素を設定
            holder.item = dataItem;
            // センサーIDのViewに値を設定
            if (dataItem.getSensorId() != null){
                // 新規追加時は空欄
                TextView sensorIdView = holder.itemView.findViewById(R.id.sensorId);
                sensorIdView.setText(String.format("%02d", dataItem.getSensorId()));
            }
            // BDアドレスのViewに値を設定
            TextView bdAddressView = holder.itemView.findViewById(R.id.bdAddress);
            bdAddressView.setText(dataItem.getBdAddress());
            // ルーム名のViewに値を設定
            if (dataItem.getRoom() != null && !dataItem.getRoom().isEmpty()){
                // 新規追加時は空欄
                TextView roomView = holder.itemView.findViewById(R.id.room);
                roomView.setText(dataItem.getRoom());
            }
//            // ソート用アイコンを設定
//            ImageView sortView = holder.itemView.findViewById(R.id.sort);
//            sortView.setImageBitmap(sortIcon);
        }
        // リソース取得
//        final Resources rsrc = holder.itemView.getResources();

/*        // リストビューをタップした時のイベントハンドラを定義
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!view.isSelected()) {
                    // 最初に行選択時、行の背景色を変更
                    ViewGroup parent = (ViewGroup) view.getParent();
                    for (int idx = 0; idx < parent.getChildCount(); idx++) {
                        if (idx == position) {
                            // 選択した行の場合、背景色を設定
                            view.setBackgroundColor(view.getResources().getColor(R.color.activeRow));
                            view.setSelected(true);
                        } else {
                            // 選択した行以外の場合、背景色をクリア
                            parent.getChildAt(idx).setBackgroundColor(0);
                            parent.getChildAt(idx).setSelected(false);
                        }
                    }
                    // 最初の行選択は、背景色を変えるだけ
                    return;
                }
                // ダイアログを準備
                View root = mInflater.inflate(R.layout.fragment_dialog_input, null);
                // テキスト入力欄にルーム名を設定
                final EditText roomTxt = root.findViewById(R.id.txt_room);
                roomTxt.setText(dataItem.getRoom());
                // タイトルを作成
                final String title = rsrc.getString(R.string.input_room_name).replace("{0}", dataItem.getBdAddress());
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
                                        // ルーム名を取得して設定
                                        String room = roomTxt.getText().toString();
                                        TextView roomView = holder.itemView.findViewById(R.id.room);
                                        roomView.setText(room);
                                        dataItem.setRoom(room);
                                        // ルーム名の変更を通知
                                        notifyItemChanged(position);
                                    }
                                })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });*/
    }

}