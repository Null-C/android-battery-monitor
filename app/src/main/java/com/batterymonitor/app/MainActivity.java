package com.batterymonitor.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.batterymonitor.app.model.BatteryInfo;

/**
 * 主界面 - 电流监测器
 */
public class MainActivity extends Activity {
    private BatteryMonitor batteryMonitor;
    private Handler handler;
    private boolean isMonitoring = false;

    // UI 组件
    private TextView tvCurrentCurrent;
    private TextView tvCurrentStats;
    private TextView tvBatteryLevel;
    private TextView tvBatteryHealth;
    private TextView tvBatteryTemp;
    private TextView tvBatteryVoltage;
    private TextView tvPhoneModel;
    private TextView tvManufacturer;
    private TextView tvAndroidVersion;

    // 更新间隔（毫秒）
    private static final int UPDATE_INTERVAL = 1000;

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (isMonitoring) {
                updateUI();
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        batteryMonitor = new BatteryMonitor(this);
        handler = new Handler(Looper.getMainLooper());
    }

    private void initViews() {
        tvCurrentCurrent = findViewById(R.id.tvCurrentCurrent);
        tvCurrentStats = findViewById(R.id.tvCurrentStats);
        tvBatteryLevel = findViewById(R.id.tvBatteryLevel);
        tvBatteryHealth = findViewById(R.id.tvBatteryHealth);
        tvBatteryTemp = findViewById(R.id.tvBatteryTemp);
        tvBatteryVoltage = findViewById(R.id.tvBatteryVoltage);
        tvPhoneModel = findViewById(R.id.tvPhoneModel);
        tvManufacturer = findViewById(R.id.tvManufacturer);
        tvAndroidVersion = findViewById(R.id.tvAndroidVersion);
    }

    private void updateUI() {
        BatteryInfo info = batteryMonitor.getBatteryInfo();

        // 更新当前电流
        int current = info.getCurrentNow();
        String currentText = current > 0
                ? getString(R.string.current_positive_format, current)
                : getString(R.string.current_negative_format, current);
        tvCurrentCurrent.setText(currentText);

        // 更新电流统计
        String statsText = getString(R.string.current_stats_format,
                info.getMinCurrent(), info.getMaxCurrent());
        tvCurrentStats.setText(statsText);

        // 更新电池信息
        tvBatteryLevel.setText(getString(R.string.battery_level_format, info.getBatteryLevel()));
        tvBatteryHealth.setText(getHealthString(info.getBatteryHealth()));
        tvBatteryTemp.setText(getString(R.string.battery_temp_format, info.getTemperature()));
        tvBatteryVoltage.setText(getString(R.string.battery_voltage_format, info.getVoltage()));

        // 更新设备信息
        tvPhoneModel.setText(info.getPhoneModel());
        tvManufacturer.setText(info.getManufacturer());
        tvAndroidVersion.setText(info.getAndroidVersion());
    }

    /**
     * 将电池健康度数值转换为中文描述
     */
    private String getHealthString(int health) {
        String[] healthStrings = getResources().getStringArray(R.array.battery_health);
        int index = health - 1;
        if (index < 0 || index >= healthStrings.length) {
            return getString(R.string.battery_health_unknown);
        }
        return healthStrings[index];
    }

    @Override
    protected void onResume() {
        super.onResume();
        isMonitoring = true;
        updateUI(); // 立即更新一次
        handler.post(updateRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        isMonitoring = false;
        handler.removeCallbacks(updateRunnable);
    }
}