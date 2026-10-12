package com.drkmuqeet.wifi;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

final class SavedAdapter extends RecyclerView.Adapter<SavedAdapter.Holder> {

    interface Listener {
        void onForget(SavedNetwork network);
    }

    private final List<SavedNetwork> items = new ArrayList<>();
    private final Listener listener;

    SavedAdapter(Listener listener) {
        this.listener = listener;
    }

    void submit(List<SavedNetwork> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_saved, parent, false);
        return new Holder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        SavedNetwork network = items.get(position);
        holder.ssid.setText(network.ssid);
        holder.detail.setText("network id " + network.id);
        holder.forget.setOnClickListener(v -> listener.onForget(network));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView ssid;
        final TextView detail;
        final MaterialButton forget;

        Holder(@NonNull View itemView) {
            super(itemView);
            ssid = itemView.findViewById(R.id.ssid);
            detail = itemView.findViewById(R.id.detail);
            forget = itemView.findViewById(R.id.btn_forget);
        }
    }
}
