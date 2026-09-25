package com.batterymonitor.app.view;

import android.content.Context;
import android.util.AttributeSet;

import com.batterymonitor.app.R;

/**
 * 电流趋势折线图
 * 展示最近 3 分钟（最多 180 个采样点）的电流变化<br/>
 * Y 轴始终包含 0 基线，以便正负电流（充电 / 放电）对比
 */
public class CurrentChartView extends TrendChartView {
    /** 已解析的折线色，0 表示尚未解析 */
    private int lineColor;

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
        if (lineColor == 0) {
            lineColor = resolveColor(R.color.chart_line_current);
        }
        return lineColor;
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