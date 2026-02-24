package com.batterymonitor.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;

import com.batterymonitor.app.model.BatteryInfo;
import com.batterymonitor.app.utils.DeviceInfoUtils;

/**
 * 电池监测核心类
 * 负责获取和统计电池相关信息
 */
public class BatteryMonitor {
    private final Context context;
    private int minCurrent = Integer.MAX_VALUE;
    private int maxCurrent = Integer.MIN_VALUE;

    public BatteryMonitor(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 获取当前电流（单位：mA）
     * 负数表示放电，正数表示充电
     * 注意：对原始系统返回值取反，以符合常规理解
     */
    public int getCurrentCurrent() {
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager == null) {
            return 0;
        }

        // API 21+ 支持获取电流
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            // 从微安转换为毫安，并取反以符合常规理解（正数充电，负数放电）
            return -(currentNow / 1000);
        }
        return 0;
    }

    /**
     * 更新最小和最大电流值
     */
    public void updateMinMax(int current) {
        if (current != 0) {
            if (current < minCurrent) {
                minCurrent = current;
            }
            if (current > maxCurrent) {
                maxCurrent = current;
            }
        }
    }

    /**
     * 获取电量百分比
     */
    public int getBatteryLevel() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);

        if (batteryStatus == null) {
            return 0;
        }

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        if (level == -1 || scale == -1) {
            return 0;
        }

        return (level * 100) / scale;
    }

    /**
     * 获取电池温度（单位：°C）
     */
    public double getBatteryTemperature() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);

        if (batteryStatus == null) {
            return 0;
        }

        int temperature = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        // 从 0.1°C 转换为 °C
        return temperature / 10.0;
    }

    /**
     * 获取电池电压（单位：V）
     */
    public double getBatteryVoltage() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);

        if (batteryStatus == null) {
            return 0;
        }

        int voltage = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        // 从毫伏转换为伏特
        return voltage / 1000.0;
    }

    /**
     * 获取电池健康度
     * 返回值：
     * 1 = 未知
     * 2 = 良好
     * 3 = 过热
     * 4 = 损坏
     * 5 = 过压
     * 6 = 故障
     * 7 = 低温
     */
    public int getBatteryHealth() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);

        if (batteryStatus == null) {
            return 1; // 未知
        }

        return batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, 1);
    }

    /**
     * 获取完整的电池信息
     */
    public BatteryInfo getBatteryInfo() {
        int currentNow = getCurrentCurrent();
        updateMinMax(currentNow);

        return new BatteryInfo(
                currentNow,
                minCurrent == Integer.MAX_VALUE ? currentNow : minCurrent,
                maxCurrent == Integer.MIN_VALUE ? currentNow : maxCurrent,
                getBatteryLevel(),
                getBatteryTemperature(),
                getBatteryVoltage(),
                getBatteryHealth(),
                DeviceInfoUtils.getPhoneModel(),
                DeviceInfoUtils.getManufacturer(),
                DeviceInfoUtils.getAndroidVersion()
        );
    }

    /**
     * 重置统计信息
     */
    public void resetStats() {
        minCurrent = Integer.MAX_VALUE;
        maxCurrent = Integer.MIN_VALUE;
    }

    /**
     * 获取最低电流
     */
    public int getMinCurrent() {
        return minCurrent == Integer.MAX_VALUE ? 0 : minCurrent;
    }

    /**
     * 获取最高电流
     */
    public int getMaxCurrent() {
        return maxCurrent == Integer.MIN_VALUE ? 0 : maxCurrent;
    }
}