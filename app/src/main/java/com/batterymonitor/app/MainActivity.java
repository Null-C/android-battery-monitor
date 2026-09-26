package com.batterymonitor.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.TextView;

import com.batterymonitor.app.model.BatteryInfo;
import com.batterymonitor.app.view.BatteryLevelChartView;
import com.batterymonitor.app.view.CurrentChartView;
import com.batterymonitor.app.view.TemperatureChartView;
import com.batterymonitor.app.view.TrendChartView;

/**
 * 主界面 - 电流监测器
 */
public class MainActivity extends Activity {
    private BatteryMonitor batteryMonitor;
    private Handler handler;
    private boolean isMonitoring = false;

    // UI 组件（顺序与布局中的排列一致）
    private TextView tvScreenOnCountdown;
    private TextView tvCurrentCurrent;
    private TextView tvCurrentMin;
    private TextView tvCurrentMax;
    private TextView tvCurrentAvg;
    private CurrentChartView chartCurrent;
    private TemperatureChartView chartTemperature;
    private BatteryLevelChartView chartBatteryLevel;
    private TextView tvBatteryLevel;
    private TextView tvBatteryHealth;
    private TextView tvBatteryTemp;
    private TextView tvBatteryVoltage;
    private TextView tvPhoneModel;
    private TextView tvManufacturer;
    private TextView tvAndroidVersion;

    // 更新间隔（毫秒）
    private static final int UPDATE_INTERVAL = 1000;

    /**
     * 前台强制屏幕常亮的时长（毫秒）。
     * 与图表时间轴同源（TrendChartView.MAX_POINTS × UPDATE_INTERVAL）：用户的目的就是看完一整条
     * 时间轴，所以两者不能各写一份「3 分钟」，否则以后调整窗口时长时必然漂移。
     * 到期后只摘掉常亮标记、交回系统超时策略，不是立刻锁屏。
     */
    private static final long SCREEN_ON_TIMEOUT_MS =
            (long) TrendChartView.MAX_POINTS * UPDATE_INTERVAL;

    /**
     * 常亮窗口的截止时刻（`SystemClock.uptimeMillis()` 时基，0 表示窗口未激活）。
     * 与 `Handler.postDelayed` 同一时基；倒计时由它推算而不是每秒递减，
     * 否则 1 Hz 刷新循环本身的抖动会累积成可见误差
     */
    private long screenOnDeadlineMs;

    /** 常亮到期：摘掉 KEEP_SCREEN_ON 标记，屏幕恢复正常超时 */
    private final Runnable screenOnTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            screenOnDeadlineMs = 0L;
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            updateScreenOnCountdown();
        }
    };

    private final Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            if (isMonitoring) {
                updateUI();
                handler.postDelayed(this, UPDATE_INTERVAL);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        batteryMonitor = new BatteryMonitor(this);
        handler = new Handler(Looper.getMainLooper());
    }

    private void initViews() {
        tvScreenOnCountdown = findViewById(R.id.tvScreenOnCountdown);
        tvCurrentCurrent = findViewById(R.id.tvCurrentCurrent);
        tvCurrentMin = findViewById(R.id.tvCurrentMin);
        tvCurrentMax = findViewById(R.id.tvCurrentMax);
        tvCurrentAvg = findViewById(R.id.tvCurrentAvg);
        chartCurrent = findViewById(R.id.chartCurrent);
        chartTemperature = findViewById(R.id.chartTemperature);
        chartBatteryLevel = findViewById(R.id.chartBatteryLevel);
        tvBatteryLevel = findViewById(R.id.tvBatteryLevel);
        tvBatteryHealth = findViewById(R.id.tvBatteryHealth);
        tvBatteryTemp = findViewById(R.id.tvBatteryTemp);
        tvBatteryVoltage = findViewById(R.id.tvBatteryVoltage);
        tvPhoneModel = findViewById(R.id.tvPhoneModel);
        tvManufacturer = findViewById(R.id.tvManufacturer);
        tvAndroidVersion = findViewById(R.id.tvAndroidVersion);
    }

    /**
     * 窗口内任何触摸都重新开始常亮计时。
     * 用 dispatchTouchEvent 而不是根布局的点击监听：ScrollView 与图表会消费触摸事件，
     * 根节点收不到 onClick，只有这里是窗口内所有触摸的必经之路。
     * 只认 ACTION_DOWN —— 一次手势触发一次，多指的第二根手指是 ACTION_POINTER_DOWN，不会重复触发。
     * `isMonitoring` 即 onResume → onPause 的标志：**只在 RESUMED 期间重新arm常亮窗口**。
     * 否则"可见但已暂停"时（例如上面压了一个不可聚焦的覆盖窗口，我们的窗口仍持有焦点）
     * 触摸会在暂停期间又把 KEEP_SCREEN_ON 加上，而倒计时只在触摸那一下刷新一次、之后因
     * 1 Hz 循环已停而冻结在 3:00。加了守卫后不变量很干净：常亮窗口只在 RESUMED 期间存在，
     * 存在时必有每秒刷新，倒计时不可能冻结
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (isMonitoring && ev.getActionMasked() == MotionEvent.ACTION_DOWN) {
            restartScreenOnWindow();
        }
        return super.dispatchTouchEvent(ev);
    }

    /** 重新开始一个常亮窗口：加上标记并重启到期计时（重复调用即重置） */
    private void restartScreenOnWindow() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        handler.removeCallbacks(screenOnTimeoutRunnable);
        handler.postDelayed(screenOnTimeoutRunnable, SCREEN_ON_TIMEOUT_MS);
        screenOnDeadlineMs = SystemClock.uptimeMillis() + SCREEN_ON_TIMEOUT_MS;
        // 立刻刷新：不然触摸后最多要等 1 秒才看到倒计时回到 3:00
        updateScreenOnCountdown();
    }

    /**
     * 刷新顶栏的常亮倒计时。
     * 秒数**向上取整**：窗口刚开始显示 3:00、最后一秒显示 0:01，不会出现看着像卡死的 0:00；
     * 剩余时间已耗尽时显示 `--:--`，表示当前没有在强制常亮（与其它占位符同一套语汇）
     */
    private void updateScreenOnCountdown() {
        long remainingMs = screenOnDeadlineMs == 0L
                ? 0L
                : screenOnDeadlineMs - SystemClock.uptimeMillis();
        if (remainingMs <= 0L) {
            tvScreenOnCountdown.setText(R.string.screen_on_countdown_idle);
            return;
        }
        int totalSeconds = (int) ((remainingMs + 999L) / 1000L);
        tvScreenOnCountdown.setText(getString(R.string.screen_on_countdown_format,
                totalSeconds / 60, totalSeconds % 60));
    }

    private void updateUI() {
        BatteryInfo info = batteryMonitor.getBatteryInfo();

        // 更新当前电流
        int current = info.getCurrentNow();
        String currentText = current > 0
                ? getString(R.string.current_positive_format, current)
                : getString(R.string.current_negative_format, current);
        tvCurrentCurrent.setText(currentText);

        // 更新电流统计（最低 / 最高 / 平均）
        tvCurrentMin.setText(getString(R.string.current_min_format, info.getMinCurrent()));
        tvCurrentMax.setText(getString(R.string.current_max_format, info.getMaxCurrent()));
        tvCurrentAvg.setText(getString(R.string.current_avg_format, info.getAvgCurrent()));

        // 更新趋势图（顺序与布局中的图表排列一致：电流 → 温度 → 电量）
        chartCurrent.addData(current);
        chartTemperature.addData(info.getTemperature());
        chartBatteryLevel.addData(info.getBatteryLevel());

        // 更新电池信息
        tvBatteryLevel.setText(getString(R.string.battery_level_format, info.getBatteryLevel()));
        tvBatteryHealth.setText(getHealthString(info.getBatteryHealth()));
        tvBatteryTemp.setText(getString(R.string.battery_temp_format, info.getTemperature()));
        tvBatteryVoltage.setText(getString(R.string.battery_voltage_format, info.getVoltage()));

        // 更新设备信息
        tvPhoneModel.setText(info.getPhoneModel());
        tvManufacturer.setText(info.getManufacturer());
        tvAndroidVersion.setText(info.getAndroidVersion());

        // 常亮倒计时：每秒随本循环刷新一次
        updateScreenOnCountdown();
    }

    /**
     * 将电池健康度数值转换为中文描述
     */
    private String getHealthString(int health) {
        String[] healthStrings = getResources().getStringArray(R.array.battery_health);
        int index = health - 1;
        if (index < 0 || index >= healthStrings.length) {
            return getString(R.string.battery_health_unknown);
        }
        return healthStrings[index];
    }

    @Override
    protected void onResume() {
        super.onResume();
        isMonitoring = true;
        updateUI(); // 立即更新一次
        handler.post(updateRunnable);
        // 每次回到前台重新给满一个常亮窗口
        restartScreenOnWindow();
    }

    @Override
    protected void onPause() {
        super.onPause();
        isMonitoring = false;
        handler.removeCallbacks(updateRunnable);
        // 离开前台立刻解除常亮，避免在后台白耗电
        // （窗口标记本身只在可见时生效，这里显式清掉是为了让计时状态与标记状态始终一致）
        handler.removeCallbacks(screenOnTimeoutRunnable);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        screenOnDeadlineMs = 0L;
        // 同步刷新显示：onPause 后界面可能仍可见（如最近任务预览），倒计时不该继续宣称在常亮
        updateScreenOnCountdown();
    }
}