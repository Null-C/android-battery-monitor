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
    protected float getStepUnit() {
        // 标签一位小数，但网格值必须是 0.5 的整数倍：26.0 / 26.5 / 27.0，而不是 26.2 / 27.2。
        // unit 取 0.5 后档位是 0.5 × {1,2,5} × 10ⁿ = 0.5 / 1 / 2.5 / 5 / 10…，锚点也只能是
        // 0.5 的倍数，于是 4 条网格线的值全部落在半度上（本图对刻度的硬要求）。
        // 代价：最细一档就是 0.5 °C，跨度 < 1.0 °C 时窗口固定 1.5 °C，曲线比 0.1 档平
        return 0.5f;
    }

    @Override
    protected int getAnchorSlackUnits() {
        // unit 0.5 的栅格比温度的数据步进粗得多，锚点可行区间必须留满一格半度（见基类钩子注释）
        return 1;
    }

    @Override
    protected int getEmptyTextResId() {
        return R.string.chart_no_temperature_data;
    }
}