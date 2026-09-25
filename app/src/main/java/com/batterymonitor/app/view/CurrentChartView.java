package com.batterymonitor.app.view;

import android.content.Context;
import android.util.AttributeSet;

/**
 * 电流趋势折线图
 * 展示最近 3 分钟（最多 180 个采样点）的电流变化<br/>
 * Y 轴始终包含 0 基线，以便正负电流（充电 / 放电）对比
 */
public class CurrentChartView extends TrendChartView {
    private static final int LINE_COLOR = 0xFF2196F3;

    public CurrentChartView(Context context) {
        super(context);
    }

    public CurrentChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CurrentChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected int getLineColor() {
        return LINE_COLOR;
    }

    @Override
    protected String formatAxisLabel(float value) {
        return String.valueOf(Math.round(value));
    }

    @Override
    protected boolean includeZeroBaseline() {
        return true;
    }
}