package com.batterymonitor.app;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;

import com.batterymonitor.app.model.BatteryInfo;
import com.batterymonitor.app.utils.DeviceInfoUtils;

import java.util.ArrayDeque;

/**
 * 电池监测核心类
 * 负责获取和统计电池相关信息
 */
public class BatteryMonitor {
    private final Context context;
    private int minCurrent = Integer.MAX_VALUE;
    private int maxCurrent = Integer.MIN_VALUE;

    // 电流历史记录（最多保留 3 分钟，每秒一个采样点）
    private static final int HISTORY_MAX_SIZE = 180;
    // 平均电流统计窗口（秒）
    private static final int AVG_WINDOW_SECONDS = 10;
    private final ArrayDeque<Integer> currentHistory = new ArrayDeque<>();

    public BatteryMonitor(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * 获取当前电流（单位：mA）
     * 负数表示放电，正数表示充电
     * 注意：对原始系统返回值取反，以符合常规理解
     */
    private int getCurrentCurrent() {
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager == null) {
            return 0;
        }

        // 从微安转换为毫安（四舍五入），并取反以符合常规理解（正数充电，负数放电）
        int currentNow = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        return -(int) Math.round(currentNow / 1000.0f);
    }

    /**
     * 更新最小和最大电流值
     */
    private void updateMinMax(int current) {
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
     * 将当前电流加入历史记录，超出 3 分钟窗口的最旧数据被移除
     */
    private void addCurrentToHistory(int current) {
        currentHistory.addLast(current);
        while (currentHistory.size() > HISTORY_MAX_SIZE) {
            currentHistory.removeFirst();
        }
    }

    /**
     * 计算最近 10 秒的平均电流（单位：mA）
     * 与最低/最高电流一致，忽略 0 值（表示传感器无数据）
     */
    private int getAverageCurrent() {
        int sum = 0;
        int count = 0;
        int startIndex = Math.max(0, currentHistory.size() - AVG_WINDOW_SECONDS);
        int index = 0;
        for (int value : currentHistory) {
            if (index >= startIndex && value != 0) {
                sum += value;
                count++;
            }
            index++;
        }
        return count == 0 ? 0 : (int) Math.round(sum / (float) count);
    }

    /**
     * 获取电池状态广播（sticky），失败时返回 null
     */
    private Intent getBatteryStatus() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return context.registerReceiver(null, filter, Context.RECEIVER_NOT_EXPORTED);
        }
        return context.registerReceiver(null, filter);
    }

    /**
     * 获取完整的电池信息
     */
    public BatteryInfo getBatteryInfo() {
        int currentNow = getCurrentCurrent();
        updateMinMax(currentNow);
        addCurrentToHistory(currentNow);
        int avgCurrent = getAverageCurrent();

        int level = 0;
        double temperature = 0;
        double voltage = 0;
        int health = 1;

        Intent batteryStatus = getBatteryStatus();
        if (batteryStatus != null) {
            // 电量百分比
            int levelValue = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (levelValue != -1 && scale != -1) {
                level = (levelValue * 100) / scale;
            }

            // 温度（0.1°C 转换为 °C，无数据时归零）
            int tempValue = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            if (tempValue > 0) {
                temperature = tempValue / 10.0;
            }

            // 电压（毫伏转换为伏特，无数据时归零）
            int voltageValue = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
            if (voltageValue > 0) {
                voltage = voltageValue / 1000.0;
            }

            // 健康度（默认未知）
            health = batteryStatus.getIntExtra(BatteryManager.EXTRA_HEALTH, 1);
        }

        return new BatteryInfo(
                currentNow,
                minCurrent == Integer.MAX_VALUE ? currentNow : minCurrent,
                maxCurrent == Integer.MIN_VALUE ? currentNow : maxCurrent,
                avgCurrent,
                level,
                temperature,
                voltage,
                health,
                DeviceInfoUtils.getPhoneModel(),
                DeviceInfoUtils.getManufacturer(),
                DeviceInfoUtils.getAndroidVersion()
        );
    }
}