package com.drkmuqeet.wifi;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

final class ApAdapter extends RecyclerView.Adapter<ApAdapter.Holder> {

    interface Listener {
        void onAccessPoint(AccessPoint accessPoint);
    }

    private final List<AccessPoint> items = new ArrayList<>();
    private final Listener listener;

    ApAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<AccessPoint> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ap, parent, false);
        return new Holder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        AccessPoint ap = items.get(position);
        holder.ssid.setText(ap.ssid);
        holder.detail.setText(ap.detail());
        holder.level.setText(ap.rssi == Integer.MIN_VALUE ? "—" : ap.rssi + " dBm");
        holder.bars.setQuality(ap.quality());
        holder.itemView.setOnClickListener(v -> listener.onAccessPoint(ap));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView ssid;
        final TextView detail;
        final TextView level;
        final SignalBarsView bars;

        Holder(@NonNull View itemView) {
            super(itemView);
            ssid = itemView.findViewById(R.id.ssid);
            detail = itemView.findViewById(R.id.detail);
            level = itemView.findViewById(R.id.level);
            bars = itemView.findViewById(R.id.bars);
        }
    }
}
