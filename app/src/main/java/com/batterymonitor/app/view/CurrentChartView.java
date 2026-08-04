package com.batterymonitor.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;

/**
 * 电流趋势折线图
 * 展示最近 3 分钟（最多 180 个采样点）的电流变化<br/>
 * 新数据从右侧进入，旧数据自动左移滚动
 */
public class CurrentChartView extends View {
    /** 最大采样点数（3 分钟 × 60 秒） */
    private static final int MAX_POINTS = 180;
    /** 水平网格线数量 */
    private static final int GRID_LINES = 4;

    private final ArrayList<Integer> data = new ArrayList<>();

    private final Paint gridPaint;
    private final Paint linePaint;
    private final Paint fillPaint;
    private final Paint dotPaint;
    private final Paint textPaint;

    public CurrentChartView(Context context) {
        this(context, null);
    }

    public CurrentChartView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CurrentChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        float density = getResources().getDisplayMetrics().density;

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(0xFFE0E0E0);
        gridPaint.setStrokeWidth(1f * density);

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(0xFF2196F3);
        linePaint.setStrokeWidth(2f * density);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(0xFF2196F3);
        dotPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFF9E9E9E);
        textPaint.setTextSize(11f * density);
    }

    /** 追加一个采样点，超出 3 分钟窗口的最旧数据被移除 */
    public void addData(int value) {
        data.add(value);
        while (data.size() > MAX_POINTS) {
            data.remove(0);
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();
        if (width == 0 || height == 0) {
            return;
        }

        float density = getResources().getDisplayMetrics().density;
        float labelLeft = 44f * density;      // 左侧留出 Y 轴标签空间
        float labelBottom = 22f * density;    // 底部留出 X 轴标签空间
        float chartTop = 8f * density;
        float chartRight = width - 8f * density;
        float chartWidth = chartRight - labelLeft;
        float chartHeight = height - chartTop - labelBottom;

        // 计算 Y 轴范围（始终包含 0 基线）
        int min = 0, max = 0;
        for (int v : data) {
            if (v < min) min = v;
            if (v > max) max = v;
        }
        if (min == max) {
            min = -1;
            max = 1;
        }
        int range = max - min;
        float yMin = min - range * 0.1f;
        float yMax = max + range * 0.1f;

        // 绘制水平网格线与 Y 轴标签
        float gridStep = chartHeight / (GRID_LINES - 1);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i < GRID_LINES; i++) {
            float y = chartTop + gridStep * i;
            float value = yMax - (yMax - yMin) * (i / (float) (GRID_LINES - 1));
            canvas.drawLine(labelLeft, y, chartRight, y, gridPaint);
            canvas.drawText(String.valueOf(Math.round(value)), labelLeft - 8f * density,
                    y + textPaint.getTextSize() / 3f, textPaint);
        }

        // 绘制 X 轴标签：-3分 / -2分 / -1分 / 现在（等分 4 段，每段 60 秒）
        textPaint.setTextAlign(Paint.Align.LEFT);
        canvas.drawText("-3分", labelLeft, height - 4f * density, textPaint);
        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("-2分", labelLeft + chartWidth / 3f, height - 4f * density, textPaint);
        canvas.drawText("-1分", labelLeft + chartWidth * 2f / 3f, height - 4f * density, textPaint);
        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("现在", chartRight, height - 4f * density, textPaint);

        // 绘制折线
        if (data.size() > 1) {
            int offset = MAX_POINTS - data.size();
            float startX = labelLeft + offset * (chartWidth / (MAX_POINTS - 1));
            float endX = labelLeft + (MAX_POINTS - 1) * (chartWidth / (MAX_POINTS - 1));

            Path linePath = new Path();
            for (int i = 0; i < data.size(); i++) {
                float x = labelLeft + (offset + i) * (chartWidth / (MAX_POINTS - 1));
                float y = chartTop + (yMax - data.get(i)) / (yMax - yMin) * chartHeight;
                if (i == 0) {
                    linePath.moveTo(x, y);
                } else {
                    linePath.lineTo(x, y);
                }
            }

            // 折线下方渐变填充，增强可读性
            Path fillPath = new Path(linePath);
            fillPath.lineTo(endX, chartTop + chartHeight);
            fillPath.lineTo(startX, chartTop + chartHeight);
            fillPath.close();
            fillPaint.setShader(new LinearGradient(0, chartTop, 0, chartTop + chartHeight,
                    0x332196F3, 0x002196F3, Shader.TileMode.CLAMP));
            canvas.drawPath(fillPath, fillPaint);

            canvas.drawPath(linePath, linePaint);

            // 最新数据点高亮
            float lastY = chartTop + (yMax - data.get(data.size() - 1)) / (yMax - yMin) * chartHeight;
            canvas.drawCircle(endX, lastY, 4f * density, dotPaint);
        }
    }
}