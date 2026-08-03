package com.batterymonitor.app.model;

/**
 * 电池信息数据模型
 */
public class BatteryInfo {
    private final int currentNow;        // 当前电流
    private final int minCurrent;        // 最低电流
    private final int maxCurrent;        // 最高电流
    private final int avgCurrent;        // 平均电流（最近 10 秒）
    private final int batteryLevel;      // 电量百分比
    private final double temperature;    // 温度（°C）
    private final double voltage;        // 电压（V）
    private final int batteryHealth;     // 电池健康度
    private final String phoneModel;     // 手机型号
    private final String manufacturer;   // 制造商
    private final String androidVersion; // 系统版本

    public BatteryInfo(int currentNow, int minCurrent, int maxCurrent, int avgCurrent,
                       int batteryLevel, double temperature, double voltage,
                       int batteryHealth, String phoneModel, String manufacturer, String androidVersion) {
        this.currentNow = currentNow;
        this.minCurrent = minCurrent;
        this.maxCurrent = maxCurrent;
        this.avgCurrent = avgCurrent;
        this.batteryLevel = batteryLevel;
        this.temperature = temperature;
        this.voltage = voltage;
        this.batteryHealth = batteryHealth;
        this.phoneModel = phoneModel;
        this.manufacturer = manufacturer;
        this.androidVersion = androidVersion;
    }

    public int getCurrentNow() {
        return currentNow;
    }

    public int getMinCurrent() {
        return minCurrent;
    }

    public int getMaxCurrent() {
        return maxCurrent;
    }

    public int getAvgCurrent() {
        return avgCurrent;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public double getTemperature() {
        return temperature;
    }

    public double getVoltage() {
        return voltage;
    }

    public int getBatteryHealth() {
        return batteryHealth;
    }

    public String getPhoneModel() {
        return phoneModel;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public String getAndroidVersion() {
        return androidVersion;
    }
}