package jp.iflink.closed_buster.model;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

public class SensorListItemViewHolder extends RecyclerView.ViewHolder {
    /** アイテム */
    public SensorListItem item;

    /**
     * コンストラクタ
     * @param itemView アイテムView
     */
    public SensorListItemViewHolder(View itemView) {
        super(itemView);
    };

    /**
     * コンストラクタ
     * @param itemView アイテムView
     * @param item アイテム
     */
    public SensorListItemViewHolder(View itemView, SensorListItem item) {
        super(itemView);
        this.item = item;
    }
}