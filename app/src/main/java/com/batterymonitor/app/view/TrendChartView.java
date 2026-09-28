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
    /** 水平网格线数量（4 条 = 3 格） */
    private static final int GRID_LINES = 4;
    /**
     * 数据最多占几格。
     * 默认 2.5：数据占 3 格轴的 40%~83%，既不贴边也不至于压成直线（温度图用这个值）。
     * 含 0 基线的图（电流）被强制为 2：只有 step ≥ span/2 才能保证 0 一定落在某条网格线上
     * （推导见 findAxisLow）。放宽到 2.5 时可行区间可能不含整数个 step，0 就钉不住了。
     * 电量图另有更硬的约束（整数标签必须一一对应到网格线），由子类覆写 getMaxDataCells()
     */
    private static final float MAX_DATA_CELLS = 2.5f;
    private static final float MAX_DATA_CELLS_WITH_ZERO = 2f;
    /**
     * 步进死区：数据跨度恰好压在档位边界（nice step 的换档点）时，把换档门槛放宽/收紧 10%。
     * 没有它的话，跨度在边界上抖动会让轴范围在相邻两档（相差 2~2.5 倍）之间反复翻
     */
    private static final float STEP_TOLERANCE = 1.1f;
    /** 对齐容差：吸收 34.0f / 0.1f 这类除法的浮点误差（0.001 格在屏幕上看不出来） */
    private static final float ALIGN_EPSILON = 1e-3f;

    // 绘制区域内边距（单位：dp）
    // 左侧保底值是渲染稳定性的承重墙：它让 labelLeft 在数据范围变化时不跳变。
    // 保底值须 ≥ 最长 Y 标签字符数 × 等宽字前进量 × 轴字号 + LABEL_GAP，再留余量：
    // 5 字符（如 "-9999"）× 0.6em × 11dp + 8dp = 41dp。
    // 注意：轴字号是 sp，标签宽度会随系统字体缩放增长，所以该值在构造函数里
    // 乘上 fontScale（只增不减）后才使用 —— 固定 44dp 在 fontScale ≥ 1.15 时会失效（见 labelLeftMin）。
    // 轴字号不要升到 12dp 以上，否则保底值会翻边（12dp 下 5 字符标签 + 8dp 间隙恰好 44dp），
    // labelLeft 随标签位数在 44↔47dp 间跳变，整张图（网格、折线、时间轴）会横向抖动。
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
    /** 最下方网格线的值（等于当前 range[0]）；axisValid 为 false 时无意义 */
    private float axisLow;
    /** 相邻网格线的值差（即当前轴的 nice step） */
    private float axisStep;
    /** 轴范围是否已算出（首帧为 false，之后一直为 true —— 数据只滚动不重置） */
    private boolean axisValid;
    private final String[] axisLabels = new String[GRID_LINES];
    private final String[] xLabels = new String[4];
    private final float density;
    /** 左侧保底留白（px）= LABEL_LEFT_MIN_DP × density × fontScale，见构造函数 */
    private final float labelLeftMin;

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
        // 左侧保底值必须随系统字体缩放一起放大：轴字号是 sp，5 字符标签的实测宽度
        // （5 × 字前进量 × 轴字号 + 8dp 间隙）会随 fontScale 增长，而固定 44dp 在 fontScale ≥ 1.15
        // 时就会被超过 —— 那一刻 labelLeft 改由「当前标签位数」决定，电流从 -999 变到 -1000
        // 就会让整张图横向跳动，正是保底值要防的事。
        // 只增不减（下限 1.0）：字体调小时标签也变小，保底值没必要跟着缩，否则本来宽松的留白
        // 又被压回临界点。因此 fontScale ≤ 1.0 时取值恒为 44dp，与改动前逐位一致。
        float fontScale = Math.max(1f, getResources().getConfiguration().fontScale);
        labelLeftMin = LABEL_LEFT_MIN_DP * density * fontScale;
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
     * Y 轴网格值的颗粒，即标签能**精确表达**的最小变化量：
     * 电流 1（整数 mA）、电量 1（整数 %）、温度 0.1（一位小数 °C）。
     * 网格线只会落在该值的整数倍上，这是"标签值 == 网格线的值"的前提
     */
    protected abstract float getStepUnit();

    /**
     * 数据最多占几格（网格线 4 条 = 3 格），即"留白多少"的取舍：值越小数据越贴边、分辨率越高。
     * 默认 2.5：数据占 3 格轴的 40%~83%，既不贴边也不至于压成直线。
     * 含 0 基线的图被强制为 2（见 MAX_DATA_CELLS_WITH_ZERO），此钩子对其无效
     */
    protected float getMaxDataCells() {
        return MAX_DATA_CELLS;
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
            float labelLeft = labelLeftMin;
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
        // 通常由保底值决定（三张图的标签均在 5 字符以内时恒定落在保底值内），
        // 仅在标签宽到会被左侧裁掉时才会扩展留白
        float labelLeft = Math.max(labelLeftMin, maxLabelWidth + LABEL_GAP_DP * density);
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

    /**
     * 计算 Y 轴范围。
     *
     * 网格线必须画在"标签能精确表达的数"上（整数 mA / 整数 % / 一位小数 °C）。
     * 旧实现把网格线画在未取整的浮点值上、只把标签四舍五入：标着 70 的那条线其实位于
     * 69.5~70.5 之间的某个值，而折线上代表 70 的点按真正的 70 定位，二者必然错开。
     * 电量图轴跨度只有 3.6（旧实现的 MIN_SPAN 3 × 1.2 余量），0.5 的取整误差就是
     * 0.5/3.6 × 图高 ≈ 10dp（约 0.4 格），肉眼可见，
     * 且错开的方向取决于当前 range 的相位（数据 min/max 一变就变），所以表现为"多数时候不对、
     * 偶尔恰好对"。误差与"标签精度 ÷ 轴跨度"成正比，故电流图（跨度几百 mA）几乎看不出来。
     *
     * 现在改为：先按数据跨度选一个 nice step（unit × {1,2,5} × 10ⁿ），再选一个对齐到 unit 的
     * 锚点 anchorLow，4 条网格线为 anchorLow + k × step（k = 0..3）。step 与 anchorLow 都是
     * unit 的整数倍，标签于是**严格等于**网格线的值 —— 误差恒为 0，不是"变小"。
     *
     * 另有两处稳定性处理：
     *  · 步进死区：跨度恰好压在档位边界时不让步进来回翻（否则轴整体跳 2~2.5 倍，非常刺眼）；
     *  · 锚点迟滞：步进不变且当前轴仍容得下全部数据时沿用旧锚点。锚点若跟着数据中位漂移，
     *    整条曲线（含历史点）会每帧上下跳一格，而一格就是 1/3 个图高。
     * 0 基线由图外的构造保证：锚点是 step 的整数倍、且构建时让 0 落在某条网格线上，
     * 之后迟滞沿用的永远是同一个轴，0 不可能掉出去。
     */
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

        int cells = GRID_LINES - 1;
        float unit = getStepUnit();
        float maxCells = includeZeroBaseline() ? MAX_DATA_CELLS_WITH_ZERO : getMaxDataCells();
        float span = max - min;

        float step = niceStep(span / maxCells, unit);
        if (axisValid) {
            // 跨度仍落在上一档的死区内就维持原步进，避免在档位边界反复翻档
            float upper = axisStep * maxCells * STEP_TOLERANCE;
            float lower = previousNiceStep(axisStep, unit) * maxCells / STEP_TOLERANCE;
            if (span <= upper && span >= lower) {
                step = axisStep;
            }
        }

        if (axisValid && step == axisStep
                && min >= axisLow && max <= axisLow + cells * step) {
            range[0] = axisLow;
            range[1] = axisLow + cells * step;
            return;
        }

        float low = findAxisLow(min, max, step);
        // 浮点对齐取不到解时升一档步进重试：步进每轮至少翻倍，必然收敛（见 findAxisLow 注释）
        while (Float.isNaN(low)) {
            step = nextNiceStep(step, unit);
            low = findAxisLow(min, max, step);
        }
        axisLow = low;
        axisStep = step;
        axisValid = true;
        range[0] = low;
        range[1] = low + cells * step;
    }

    /**
     * 选最下方那条网格线的值（锚点），4 条网格线为 anchorLow + k × step（k = 0..3）。
     * 约束：
     *  ① anchorLow ≤ min 且 anchorLow + 3 × step ≥ max —— 必须容下全部数据；
     *  ② 含 0 基线的图要求 0 落在网格线上 → anchorLow 只能取 -m × step；
     *     其余图要求 anchorLow 是 unit 的整数倍 —— 标签才严格等于网格线的值。
     * 解一般不唯一，取"数据上下留白最均衡"的那个。
     *
     * 存在性：含 0 基线时可行区间 [-min/step, 3 - max/step] 长度为 3 - span/step ≥ 1
     * （由 step ≥ span/2 保证）⇒ 必含整数 m；其余图可行区间长度为 3 - span/step ≥ 0.2 × span，
     * 个别边界情形（如跨度不足一个 unit）可能不含 unit 的整数倍，此时返回 NaN 让调用方升档。
     */
    private float findAxisLow(float min, float max, float step) {
        int cells = GRID_LINES - 1;
        if (includeZeroBaseline()) {
            int mMin = ceilInt(-min / step);
            int mMax = floorInt(cells - max / step);
            if (mMin > mMax) {
                return Float.NaN;
            }
            // 0 两侧的留白尽量均衡：取可行区间的中点
            return -clampInt(Math.round((mMin + mMax) / 2f), mMin, mMax) * step;
        }
        float unit = getStepUnit();
        int kMin = ceilInt((max - cells * step) / unit);
        int kMax = floorInt(min / unit);
        if (kMin > kMax) {
            return Float.NaN;
        }
        // 数据居中后再吸附到最近的 unit 整数倍，最后夹回可行区间
        float centeredLow = (min + max) / 2f - cells * step / 2f;
        return clampInt(Math.round(centeredLow / unit), kMin, kMax) * unit;
    }

    /** unit × {1,2,5} × 10ⁿ 序列中 ≥ required 的最小值（循环上限纯属防御，实际数据远达不到） */
    private static float niceStep(float required, float unit) {
        float decade = unit;
        for (int i = 0; i < 64; i++) {
            if (decade >= required) return decade;
            if (decade * 2f >= required) return decade * 2f;
            if (decade * 5f >= required) return decade * 5f;
            decade *= 10f;
        }
        return decade;
    }

    /** 比当前档更粗的下一档 */
    private static float nextNiceStep(float step, float unit) {
        return niceStep(step * 1.001f, unit);
    }

    /** 比当前档更细的上一档（已是最细的 unit 时返回 unit 本身，调用方据此无副作用） */
    private static float previousNiceStep(float step, float unit) {
        return niceStep(step / 2.6f, unit);
    }

    /** 返回 ≥ value 的最小整数，ALIGN_EPSILON 吸收除法误差 */
    private static int ceilInt(float value) {
        return (int) Math.ceil(value - ALIGN_EPSILON);
    }

    /** 返回 ≤ value 的最大整数 */
    private static int floorInt(float value) {
        return (int) Math.floor(value + ALIGN_EPSILON);
    }

    private static int clampInt(int value, int min, int max) {
        if (value < min) return min;
        return value > max ? max : value;
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