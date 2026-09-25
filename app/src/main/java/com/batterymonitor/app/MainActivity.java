package com.batterymonitor.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import com.batterymonitor.app.model.BatteryInfo;
import com.batterymonitor.app.view.BatteryLevelChartView;
import com.batterymonitor.app.view.CurrentChartView;
import com.batterymonitor.app.view.TemperatureChartView;

/**
 * 主界面 - 电流监测器
 */
public class MainActivity extends Activity {
    private BatteryMonitor batteryMonitor;
    private Handler handler;
    private boolean isMonitoring = false;

    // UI 组件
    private TextView tvCurrentCurrent;
    private TextView tvCurrentMin;
    private TextView tvCurrentMax;
    private TextView tvCurrentAvg;
    private CurrentChartView chartCurrent;
    private TemperatureChartView chartTemperature;
    private BatteryLevelChartView chartBatteryLevel;
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
        tvCurrentMin = findViewById(R.id.tvCurrentMin);
        tvCurrentMax = findViewById(R.id.tvCurrentMax);
        tvCurrentAvg = findViewById(R.id.tvCurrentAvg);
        chartCurrent = findViewById(R.id.chartCurrent);
        chartTemperature = findViewById(R.id.chartTemperature);
        chartBatteryLevel = findViewById(R.id.chartBatteryLevel);
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

        // 更新电流统计（最低 / 最高 / 平均）
        tvCurrentMin.setText(getString(R.string.current_min_format, info.getMinCurrent()));
        tvCurrentMax.setText(getString(R.string.current_max_format, info.getMaxCurrent()));
        tvCurrentAvg.setText(getString(R.string.current_avg_format, info.getAvgCurrent()));

        // 更新趋势图（顺序与布局中的图表排列一致：电流 → 温度 → 电量）
        chartCurrent.addData(current);
        chartTemperature.addData(info.getTemperature());
        chartBatteryLevel.addData(info.getBatteryLevel());

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