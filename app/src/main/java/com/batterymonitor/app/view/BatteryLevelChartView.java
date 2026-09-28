package com.batterymonitor.app.view;

import android.content.Context;
import android.util.AttributeSet;

import com.batterymonitor.app.R;

/**
 * 电池电量趋势折线图
 * 展示最近 3 分钟（最多 180 个采样点）的电池电量变化<br/>
 * 与电流图不同：Y 轴自适应电量数据范围（不含 0 基线），否则常年在高位的电量会被压成直线
 */
public class BatteryLevelChartView extends TrendChartView {
    /** 已解析的折线色，0 表示尚未解析 */
    private int lineColor;

    public BatteryLevelChartView(Context context) {
        super(context);
    }

    public BatteryLevelChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BatteryLevelChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * 广播里没有 EXTRA_LEVEL / EXTRA_SCALE 时 BatteryMonitor 会把电量留为 0，此类采样点不入图。
     * 副作用：真实 0% 电量同样会被跳过（此时设备即将关机），与温度图跳过 0 的既有约定一致
     */
    @Override
    public void addData(float value) {
        if (value <= 0f) {
            return;
        }
        super.addData(value);
    }

    @Override
    protected int getLineColor() {
        if (lineColor == 0) {
            lineColor = resolveColor(R.color.chart_line_battery_level);
        }
        return lineColor;
    }

    @Override
    protected String formatAxisLabel(float value) {
        return String.valueOf(Math.round(value));
    }

    @Override
    protected boolean includeZeroBaseline() {
        return false;
    }

    @Override
    protected float getStepUnit() {
        return 1f; // 电量是整数百分比，网格线只能落在整数 % 上（折线因此精确落在线上的 70/69/…）
    }

    /**
     * 宁可贴边也不留白：数据允许占满 3 格，从而在窗口跨度 ≤ 3%（3 分钟内电量变化不超过 3 个点，
     * 正常使用必然如此）时**恒用步进 1**。步进 1 时 4 条网格线是连续的 4 个整数，窗口内出现过的
     * 每个整数值都有一条与自己同名的线，折线正好落在线上 —— 这是用户唯一能直接验证的直觉。
     * 若按默认的 2.5 留白，跨度落在 2.5%~3% 时步进会被抬到 2，网格线变成 71/69/67…，
     * 70% 就会落在 69 与 71 的正中间，又回到"没落在线上"的观感
     */
    @Override
    protected float getMaxDataCells() {
        return 3f;
    }

    @Override
    protected int getEmptyTextResId() {
        return R.string.chart_no_level_data;
    }
}