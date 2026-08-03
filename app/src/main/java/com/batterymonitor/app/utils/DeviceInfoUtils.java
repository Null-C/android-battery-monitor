package com.batterymonitor.app.utils;

import android.os.Build;

/**
 * 设备信息工具类
 */
public class DeviceInfoUtils {

    private DeviceInfoUtils() {
        // 工具类不允许实例化
    }

    /**
     * 获取手机型号
     */
    public static String getPhoneModel() {
        return Build.MODEL;
    }

    /**
     * 获取制造商
     */
    public static String getManufacturer() {
        return Build.MANUFACTURER;
    }

    /**
     * 获取 Android 系统版本
     */
    public static String getAndroidVersion() {
        return "Android " + Build.VERSION.RELEASE;
    }
}