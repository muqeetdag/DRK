package com.drkmuqeet.wifi;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The whole UI. Every command runs on a single background thread; the main thread only
 * ever paints. Nothing here blocks, so a slow su call never freezes the interface.
 */
public class MainActivity extends AppCompatActivity {

    private static final long AUTO_REFRESH_MS = 20000;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private SwipeRefreshLayout refresh;
    private TextView rootChip;
    private TextView ssidView;
    private TextView stateView;
    private TextView scanCaption;
    private TextView chartCaption;
    private TextView savedCaption;
    private TextView powerSaveHint;
    private LinearLayout metrics;
    private SignalBarsView bars;
    private ChannelChartView chart;
    private MaterialSwitch powerSaveSwitch;
    private MaterialButton toggleButton;
    private MaterialButton scanButton;

    private ApAdapter apAdapter;
    private SavedAdapter savedAdapter;

    private boolean rootGranted;
    private boolean busy;
    private boolean suppressSwitchCallback;
    private boolean radioOn;

    private final Runnable autoRefresh = new Runnable() {
        @Override
        public void run() {
            if (rootGranted && !busy) {
                loadStatus();
            }
            ui.postDelayed(this, AUTO_REFRESH_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        refresh = findViewById(R.id.refresh);
        rootChip = findViewById(R.id.root_chip);
        ssidView = findViewById(R.id.ssid);
        stateView = findViewById(R.id.state);
        scanCaption = findViewById(R.id.scan_caption);
        chartCaption = findViewById(R.id.chart_caption);
        savedCaption = findViewById(R.id.saved_caption);
        powerSaveHint = findViewById(R.id.power_save_hint);
        metrics = findViewById(R.id.metrics);
        bars = findViewById(R.id.bars);
        chart = findViewById(R.id.chart);
        powerSaveSwitch = findViewById(R.id.switch_power_save);
        toggleButton = findViewById(R.id.btn_toggle);
        scanButton = findViewById(R.id.btn_scan);

        apAdapter = new ApAdapter(this::copyAccessPoint);
        savedAdapter = new SavedAdapter(this::confirmForget);

        RecyclerView scanList = findViewById(R.id.scan_list);
        scanList.setLayoutManager(new LinearLayoutManager(this));
        scanList.setAdapter(apAdapter);

        RecyclerView savedList = findViewById(R.id.saved_list);
        savedList.setLayoutManager(new LinearLayoutManager(this));
        savedList.setAdapter(savedAdapter);

        refresh.setColorSchemeColors(ContextCompat.getColor(this, R.color.lime));
        refresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(this, R.color.panel));
        refresh.setOnRefreshListener(() -> {
            loadStatus();
            loadSaved();
        });

        toggleButton.setOnClickListener(v -> toggleRadio());
        scanButton.setOnClickListener(v -> runScan());

        powerSaveSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (suppressSwitchCallback || busy) {
                return;
            }
            applyPowerSave(checked);
        });

        setControlsEnabled(false);
        probeRoot();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.postDelayed(autoRefresh, AUTO_REFRESH_MS);
        if (rootGranted) {
            loadStatus();
        }
    }

    @Override
    protected void onPause() {
        ui.removeCallbacks(autoRefresh);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** Posts to the main thread only while this activity is still alive. */
    private void onUi(Runnable action) {
        ui.post(() -> {
            if (!isFinishing() && !isDestroyed()) {
                action.run();
            }
        });
    }

    private void toast(String message) {
        Snackbar.make(refresh, message, Snackbar.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------ root

    private void probeRoot() {
        rootChip.setText("Requesting root…");
        io.execute(() -> {
            boolean granted = WifiRepo.hasRoot();
            onUi(() -> {
                rootGranted = granted;
                if (granted) {
                    rootChip.setText("ROOT GRANTED");
                    rootChip.setTextColor(ContextCompat.getColor(this, R.color.lime));
                    setControlsEnabled(true);
                    loadStatus();
                    loadSaved();
                } else {
                    rootChip.setText("ROOT NOT GRANTED");
                    rootChip.setTextColor(ContextCompat.getColor(this, R.color.red));
                    ssidView.setText("Root required");
                    stateView.setText("Grant the su prompt, then pull down to retry.");
                    refresh.setRefreshing(false);
                }
            });
        });
    }

    private void setControlsEnabled(boolean enabled) {
        toggleButton.setEnabled(enabled);
        scanButton.setEnabled(enabled);
        powerSaveSwitch.setEnabled(enabled);
    }

    // ------------------------------------------------------------------ status

    private void loadStatus() {
        io.execute(() -> {
            WifiStatus status = WifiRepo.status();
            onUi(() -> {
                paint(status);
                refresh.setRefreshing(false);
            });
        });
    }

    private void paint(WifiStatus status) {
        radioOn = status.radioOn;

        if (status.connected()) {
            ssidView.setText(status.ssid);
        } else if (status.radioOn) {
            ssidView.setText("Not connected");
        } else {
            ssidView.setText("Wi-Fi off");
        }

        String state = status.state.isEmpty() ? "—" : status.state;
        int quality = status.quality();
        stateView.setText((status.radioOn ? "Radio on" : "Radio off") + " · " + state
                + (quality < 0 ? "" : " · " + quality + "%"));
        bars.setQuality(quality);

        metrics.removeAllViews();
        addMetric("IP", status.ip);
        addMetric("Gateway", status.gateway);
        addMetric("DNS", status.dns);
        addMetric("Band", WifiMath.bandName(status.frequency));
        addMetric("Channel", status.channel > 0 ? String.valueOf(status.channel) : "");
        addMetric("Link speed", status.linkSpeedMbps > 0 ? status.linkSpeedMbps + " Mbps" : "");
        addMetric("Signal", status.rssi == Integer.MIN_VALUE ? "" : status.rssi + " dBm");
        addMetric("Security", status.security);
        addMetric("BSSID", status.bssid);
        addMetric("MAC", status.mac);

        toggleButton.setText(status.radioOn ? "Turn Wi-Fi off" : "Turn Wi-Fi on");

        suppressSwitchCallback = true;
        powerSaveSwitch.setEnabled(rootGranted && status.powerSaveKnown);
        powerSaveSwitch.setChecked(status.powerSaveKnown && status.powerSave);
        suppressSwitchCallback = false;
        powerSaveHint.setText(status.powerSaveKnown
                ? "Off favours latency and throughput, on favours battery."
                : "This kernel does not expose iw power_save control.");
    }

    private void addMetric(String label, String value) {
        View row = getLayoutInflater().inflate(R.layout.row_metric, metrics, false);
        ((TextView) row.findViewById(R.id.k)).setText(label);
        ((TextView) row.findViewById(R.id.v)).setText(value == null || value.isEmpty() ? "—" : value);
        metrics.addView(row);
    }

    // ------------------------------------------------------------------ actions

    private void toggleRadio() {
        if (busy) {
            return;
        }
        busy = true;
        setControlsEnabled(false);
        boolean target = !radioOn;
        io.execute(() -> {
            RootShell.Result result = WifiRepo.setRadio(target);
            WifiStatus status = WifiRepo.status();
            onUi(() -> {
                busy = false;
                setControlsEnabled(true);
                paint(status);
                if (status.radioOn == target) {
                    toast(target ? "Radio on" : "Radio off");
                } else {
                    toast(result.err.isEmpty() ? "The radio did not change state" : result.err.trim());
                }
            });
        });
    }

    private void applyPowerSave(boolean enabled) {
        busy = true;
        io.execute(() -> {
            RootShell.Result result = WifiRepo.setPowerSave(enabled);
            WifiStatus status = WifiRepo.status();
            onUi(() -> {
                busy = false;
                paint(status);
                if (!status.powerSaveKnown || status.powerSave != enabled) {
                    toast(result.err.isEmpty() ? "Power save did not change" : result.err.trim());
                } else {
                    toast(enabled ? "Power save on" : "Power save off");
                }
            });
        });
    }

    private void runScan() {
        if (busy) {
            return;
        }
        busy = true;
        setControlsEnabled(false);
        scanCaption.setText("Scanning…");
        io.execute(() -> {
            List<AccessPoint> aps = WifiRepo.scan();
            int[] load = WifiRepo.channelLoad(aps);
            int on5 = WifiRepo.countOn5Ghz(aps);
            onUi(() -> {
                busy = false;
                setControlsEnabled(true);
                apAdapter.submit(aps);
                chart.setCounts(load);
                if (aps.isEmpty()) {
                    scanCaption.setText("No results. The radio must be on, and some ROMs withhold "
                            + "scan lists unless location is enabled.");
                    chartCaption.setText("Nothing to chart yet.");
                } else {
                    scanCaption.setText(aps.size() + " networks · " + on5
                            + " on 5 GHz · tap a row to copy its BSSID");
                    chartCaption.setText("Bars count access points per 2.4 GHz channel. "
                            + "Channels 1, 6 and 11 do not overlap.");
                }
            });
        });
    }

    private void loadSaved() {
        io.execute(() -> {
            List<SavedNetwork> networks = WifiRepo.saved();
            onUi(() -> {
                savedAdapter.submit(networks);
                savedCaption.setText(networks.isEmpty()
                        ? "No saved networks reported by this ROM."
                        : networks.size() + " networks remembered");
            });
        });
    }

    private void confirmForget(SavedNetwork network) {
        new AlertDialog.Builder(this)
                .setTitle("Forget network")
                .setMessage("Remove \"" + network.ssid + "\" from this device?")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Forget", (dialog, which) -> forget(network))
                .show();
    }

    private void forget(SavedNetwork network) {
        io.execute(() -> {
            RootShell.Result result = WifiRepo.forget(network.id);
            onUi(() -> {
                toast(result.ok() ? "Forgot " + network.ssid : "Could not forget that network");
                loadSaved();
            });
        });
    }

    private void copyAccessPoint(AccessPoint ap) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("BSSID", ap.bssid));
        toast(ap.ssid + " · " + ap.bssid + " copied");
    }
}
