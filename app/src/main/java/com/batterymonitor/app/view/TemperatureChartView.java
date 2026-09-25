package com.batterymonitor.app.view;

import android.content.Context;
import android.util.AttributeSet;

import com.batterymonitor.app.R;

import java.util.Locale;

/**
 * 电池温度趋势折线图
 * 展示最近 3 分钟（最多 180 个采样点）的电池温度变化<br/>
 * 与电流图不同：Y 轴自适应温度数据范围（不含 0 基线），否则 25~35°C 的波动会被压成直线
 */
public class TemperatureChartView extends TrendChartView {
    /** Y 轴最小跨度（°C），保证 4 个网格标签保留一位小数后不会重复 */
    private static final float MIN_SPAN = 0.5f;

    /** 已解析的折线色，0 表示尚未解析 */
    private int lineColor;

    public TemperatureChartView(Context context) {
        super(context);
    }

    public TemperatureChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TemperatureChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * 传感器无数据时 BatteryMonitor 会把温度归零，此类采样点不入图
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
            lineColor = resolveColor(R.color.chart_line_temperature);
        }
        return lineColor;
    }

    @Override
    protected String formatAxisLabel(float value) {
        if (Math.abs(value) < 0.05f) {
            value = 0f; // 避免输出 "-0.0"
        }
        return String.format(Locale.getDefault(), "%.1f", value);
    }

    @Override
    protected boolean includeZeroBaseline() {
        return false;
    }

    @Override
    protected float getMinSpan() {
        return MIN_SPAN;
    }

    @Override
    protected int getEmptyTextResId() {
        return R.string.chart_no_temperature_data;
    }
}