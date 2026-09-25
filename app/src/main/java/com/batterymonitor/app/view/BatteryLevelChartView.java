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
    /**
     * Y 轴最小跨度（%）。
     * 电量是整数百分比且 3 分钟内常常一个采样点都不变，跨度退化成 0 时若不强制最小跨度，
     * 4 个整数标签必然重复。基类还会再留 10% 余量，故最终跨度 = 3 × 1.2 = 3.6，
     * 网格步进 1.2 > 1，相邻标签四舍五入后至少相差 1，必定互不相同
     */
    private static final float MIN_SPAN = 3f;

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
    protected float getMinSpan() {
        return MIN_SPAN;
    }

    @Override
    protected int getEmptyTextResId() {
        return R.string.chart_no_level_data;
    }
}