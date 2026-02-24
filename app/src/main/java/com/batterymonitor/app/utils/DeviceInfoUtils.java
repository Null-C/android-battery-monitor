package com.batterymonitor.app.utils;

import android.os.Build;

/**
 * 设备信息工具类
 */
public class DeviceInfoUtils {

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

    /**
     * 获取 SDK 版本号
     */
    public static int getSdkVersion() {
        return Build.VERSION.SDK_INT;
    }

    /**
     * 获取完整的设备名称（制造商 + 型号）
     */
    public static String getFullDeviceName() {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }
}