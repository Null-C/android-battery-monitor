package com.batterymonitor.app.view;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.batterymonitor.app.R;

import java.util.ArrayList;

/**
 * 趋势折线图基类
 * 展示最近 3 分钟（最多 180 个采样点）的数值变化<br/>
 * 新数据从右侧进入，旧数据自动左移滚动<br/>
 * 子类只需提供颜色、Y 轴标签格式与范围策略（见下方各钩子）
 */
public abstract class TrendChartView extends View {
    /**
     * 最大采样点数（3 分钟 × 60 秒）。
     * 对 MainActivity 公开，用于推导前台常亮时长，使「时间轴长度」与「常亮时长」同源
     */
    public static final int MAX_POINTS = 180;
    /** 水平网格线数量 */
    private static final int GRID_LINES = 4;
    /** Y 轴范围上下各留的余量比例 */
    private static final float PADDING_RATIO = 0.1f;

    // 绘制区域内边距（单位：dp）
    // 左侧保底值是渲染稳定性的承重墙：它让 labelLeft 在数据范围变化时不跳变。
    // 保底值须 ≥ 最长 Y 标签字符数 × 等宽字前进量 × 轴字号 + LABEL_GAP，再留余量：
    // 5 字符（如 "-9999"）× 0.6em × 11dp + 8dp = 41dp。
    // 轴字号不要升到 12dp 以上，否则保底值会翻边，labelLeft 随标签位数在 44↔47dp 间跳变，
    // 整张图（网格、折线、时间轴）会横向抖动。
    private static final float LABEL_LEFT_MIN_DP = 44f;
    private static final float LABEL_GAP_DP = 8f;
    /** X 轴标签基线距底部的距离 */
    private static final float LABEL_BASELINE_DP = 4f;
    /** X 轴标签与绘图区之间的最小间隙 */
    private static final float LABEL_CLEARANCE_DP = 1f;
    private static final float CHART_TOP_DP = 6f;
    private static final float CHART_RIGHT_DP = 8f;
    /** 坐标轴标签字号（sp，随系统字体缩放） */
    private static final float AXIS_TEXT_SIZE_SP = 11f;

    // 渐变填充顶部透明度（20%）
    private static final int FILL_ALPHA_TOP = 0x33000000;

    private final ArrayList<Float> data = new ArrayList<>();
    /** Y 轴范围 [0]=yMin [1]=yMax，复用数组避免每帧分配 */
    private final float[] range = new float[2];
    private final String[] axisLabels = new String[GRID_LINES];
    private final String[] xLabels = new String[4];
    private final float density;

    private final Paint gridPaint;
    private final Paint linePaint;
    private final Paint fillPaint;
    private final Paint dotPaint;
    private final Paint textPaint;

    /** 已应用到 Paint 上的折线颜色，用于惰性更新（构造期不可调用抽象钩子） */
    private int appliedLineColor;
    private String emptyText;
    private boolean emptyTextResolved;

    protected TrendChartView(Context context) {
        this(context, null);
    }

    protected TrendChartView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    protected TrendChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        density = getResources().getDisplayMetrics().density;
        // 轴标签按 sp 折算，跟随系统字体缩放（fontScale 1.0 时等于 dp）。
        // 不用 DisplayMetrics.scaledDensity：它自 API 34 起废弃，且不处理非线性字体缩放。
        float axisTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                AXIS_TEXT_SIZE_SP, getResources().getDisplayMetrics());

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(resolveColor(R.color.chart_grid));
        gridPaint.setStrokeWidth(1f * density);

        // 折线与圆点的颜色由 getLineColor() 在绘制期应用
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setStrokeWidth(2f * density);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setStyle(Paint.Style.FILL);

        // 坐标轴文字用等宽字体，使相邻采样的数值宽度稳定（比例字体下读数每秒会横向抖动）
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(resolveColor(R.color.chart_axis_text));
        textPaint.setTextSize(axisTextSize);
        textPaint.setTypeface(Typeface.MONOSPACE);
    }

    /**
     * 读取颜色资源。
     * Resources.getColor(int) 自 API 23 起被标记废弃，但本项目 minSdk 21 且不需要按主题解析
     * （带 theme 的重载是 API 23，会被 lintVital 的 NewApi 拦住），故沿用旧签名。
     */
    @SuppressWarnings("deprecation")
    protected int resolveColor(int resId) {
        return getResources().getColor(resId);
    }

    // ---------------------------------------------------------------- 子类钩子
    // 以下钩子必须是纯函数或返回编译期常量：基类可能在任意时机调用它们

    /** 折线、圆点与渐变填充的基准色，必须是不透明色（alpha = 0xFF） */
    protected abstract int getLineColor();

    /** Y 轴标签文本 */
    protected abstract String formatAxisLabel(float value);

    /** Y 轴范围是否始终包含 0 基线 */
    protected abstract boolean includeZeroBaseline();

    /**
     * Y 轴最小跨度，避免标签因跨度过小而重复；0 表示不限制。
     * 该约束对"采样值全部相等"（跨度恰好为 0）的退化情形同样生效
     */
    protected float getMinSpan() {
        return 0f;
    }

    /** 无有效数据时的提示文案资源 id，0 表示不绘制提示 */
    protected int getEmptyTextResId() {
        return 0;
    }

    // ---------------------------------------------------------------- 数据

    /** 追加一个采样点，超出 3 分钟窗口的最旧数据被移除 */
    public void addData(float value) {
        data.add(value);
        while (data.size() > MAX_POINTS) {
            data.remove(0);
        }
        invalidate();
    }

    /** 追加一个采样点（double 便捷入口，内部转为 float） */
    public void addData(double value) {
        addData((float) value);
    }

    // ---------------------------------------------------------------- 绘制

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) {
            return;
        }

        float chartTop = CHART_TOP_DP * density;
        float chartRight = width - CHART_RIGHT_DP * density;
        // 底部标签带按实测文字高度推导：X 轴标签基线在 height - LABEL_BASELINE_DP，
        // 文字上沿 = 基线 + ascent，再留一点间隙，避免网格底边压到标签上。
        // 字号随系统缩放到多大都不会重叠（原固定 22dp 里有约 8dp 是纯浪费）。
        float labelBand = LABEL_BASELINE_DP * density - textPaint.ascent()
                + LABEL_CLEARANCE_DP * density;
        float chartHeight = height - chartTop - labelBand;

        // 画不出折线时（无数据或仅一个点），配置了提示文案的图表只显示提示
        String emptyHint = getEmptyText();
        if (emptyHint != null && data.size() < 2) {
            float labelLeft = LABEL_LEFT_MIN_DP * density;
            textPaint.setTextAlign(Paint.Align.CENTER);
            float baseline = chartTop + chartHeight / 2f
                    - (textPaint.ascent() + textPaint.descent()) / 2f;
            canvas.drawText(emptyHint, (labelLeft + chartRight) / 2f, baseline, textPaint);
            return;
        }

        applyLineColorIfNeeded();

        // 计算 Y 轴范围与标签文本（左侧留白取决于标签实测宽度）
        computeRange();
        float yMin = range[0];
        float yMax = range[1];
        for (int i = 0; i < GRID_LINES; i++) {
            float value = yMax - (yMax - yMin) * (i / (float) (GRID_LINES - 1));
            axisLabels[i] = formatAxisLabel(value);
        }
        float maxLabelWidth = 0f;
        for (String label : axisLabels) {
            maxLabelWidth = Math.max(maxLabelWidth, textPaint.measureText(label));
        }
        // 通常由保底值决定（电流图标签 ≤ 5 字符、温度图 ≤ 5 字符时均落在 44dp），
        // 仅在标签宽到会被左侧裁掉时才会扩展留白
        float labelLeft = Math.max(LABEL_LEFT_MIN_DP * density,
                maxLabelWidth + LABEL_GAP_DP * density);
        float chartWidth = chartRight - labelLeft;

        // 绘制水平网格线与 Y 轴标签
        float gridStep = chartHeight / (GRID_LINES - 1);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i < GRID_LINES; i++) {
            float y = chartTop + gridStep * i;
            canvas.drawLine(labelLeft, y, chartRight, y, gridPaint);
            canvas.drawText(axisLabels[i], labelLeft - LABEL_GAP_DP * density,
                    y + textPaint.getTextSize() / 3f, textPaint);
        }

        // 绘制 X 轴标签：-3分 / -2分 / -1分 / 现在（等分 4 段，每段 60 秒）
        ensureXLabels();
        float xLabelY = height - LABEL_BASELINE_DP * density;
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText(xLabels[0], labelLeft, xLabelY, textPaint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(xLabels[1], labelLeft + chartWidth / 3f, xLabelY, textPaint);
        canvas.drawText(xLabels[2], labelLeft + chartWidth * 2f / 3f, xLabelY, textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(xLabels[3], chartRight, xLabelY, textPaint);

        if (data.size() <= 1) {
            return;
        }

        int offset = MAX_POINTS - data.size();
        float step = chartWidth / (MAX_POINTS - 1);
        float startX = labelLeft + offset * step;
        float endX = labelLeft + (MAX_POINTS - 1) * step;

        Path linePath = new Path();
        for (int i = 0; i < data.size(); i++) {
            float x = labelLeft + (offset + i) * step;
            float y = chartTop + (yMax - data.get(i)) / (yMax - yMin) * chartHeight;
            if (i == 0) {
                linePath.moveTo(x, y);
            } else {
                linePath.lineTo(x, y);
            }
        }

        // 折线下方渐变填充，增强可读性（必须是折线路径的副本，否则闭合边会被描边）
        Path fillPath = new Path(linePath);
        fillPath.lineTo(endX, chartTop + chartHeight);
        fillPath.lineTo(startX, chartTop + chartHeight);
        fillPath.close();
        int lineColor = getLineColor();
        fillPaint.setShader(new LinearGradient(0, chartTop, 0, chartTop + chartHeight,
                (lineColor & 0x00FFFFFF) | FILL_ALPHA_TOP, lineColor & 0x00FFFFFF,
                Shader.TileMode.CLAMP));
        canvas.drawPath(fillPath, fillPaint);

        // 绘制顺序：填充 → 折线 → 圆点
        canvas.drawPath(linePath, linePaint);

        // 最新数据点高亮
        float lastY = chartTop + (yMax - data.get(data.size() - 1)) / (yMax - yMin) * chartHeight;
        canvas.drawCircle(endX, lastY, 4f * density, dotPaint);
    }

    /** 折线与圆点的颜色只在绘制期应用，避免在构造函数中调用抽象钩子 */
    private void applyLineColorIfNeeded() {
        int lineColor = getLineColor();
        if (appliedLineColor != lineColor) {
            appliedLineColor = lineColor;
            linePaint.setColor(lineColor);
            dotPaint.setColor(lineColor);
        }
    }

    /** 计算 Y 轴范围：先按需纳入 0 基线，再补偿小跨度与退化，最后留出余量 */
    private void computeRange() {
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        for (float value : data) {
            if (value < min) min = value;
            if (value > max) max = value;
        }
        if (data.isEmpty()) {
            min = 0f;
            max = 0f;
        }
        if (includeZeroBaseline()) {
            min = Math.min(min, 0f);
            max = Math.max(max, 0f);
        }

        float span = max - min;
        // 最小跨度必须在"采样值全部相等"（span == 0）时也生效。电量是整数百分比，
        // 3 分钟内常常一个点都不变，若让退化情形先走 ±1 分支，跨度就只有 2，
        // 网格步进 0.733 → 4 个整数标签必然重复（如 86 / 85 / 85 / 84）。
        if (span < getMinSpan()) {
            float center = (min + max) / 2f;
            min = center - getMinSpan() / 2f;
            max = center + getMinSpan() / 2f;
        }
        // 退化保护：MIN_SPAN 为 0（电流图）且采样值全相同 —— 如设备不支持电流检测时恒为 0。
        // 此时维持原有行为（向两侧各扩 1），电流图在该情形下的渲染逐位不变。
        if (max - min <= 0f) {
            min -= 1f;
            max += 1f;
        }

        float fullSpan = max - min;
        range[0] = min - fullSpan * PADDING_RATIO;
        range[1] = max + fullSpan * PADDING_RATIO;
    }

    /** 惰性解析 X 轴文案（两图共用的时间轴语义） */
    private void ensureXLabels() {
        if (xLabels[0] != null) {
            return;
        }
        Resources res = getResources();
        xLabels[0] = res.getString(R.string.chart_axis_3min_ago);
        xLabels[1] = res.getString(R.string.chart_axis_2min_ago);
        xLabels[2] = res.getString(R.string.chart_axis_1min_ago);
        xLabels[3] = res.getString(R.string.chart_axis_now);
    }

    /** 惰性解析无数据提示文案，避免在每帧的 onDraw 中读取资源 */
    private String getEmptyText() {
        if (!emptyTextResolved) {
            emptyTextResolved = true;
            int resId = getEmptyTextResId();
            emptyText = resId == 0 ? null : getResources().getString(resId);
        }
        return emptyText;
    }
}