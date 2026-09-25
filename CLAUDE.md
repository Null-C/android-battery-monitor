# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Android 电池电流监测应用（无 Root），纯 Java 实现，无第三方依赖。通过 `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW` 读取电流，通过 `ACTION_BATTERY_CHANGED` sticky 广播读取电量/温度/电压/健康度。本项目无测试目录。

## 构建命令

```bash
./gradlew assembleDebug                                  # 编译 Debug APK
./gradlew installDebug                                   # 安装到设备
./gradlew :app:compileDebugJavaWithJavac --offline       # 快速编译验证（改动后最常用）
./gradlew clean                                          # 清理构建
```

## 版本号管理

版本号集中维护在根目录 `gradle.properties`（`VERSION_CODE` 单调递增整数、`VERSION_NAME` 显示字符串），`app/build.gradle` 通过 `project.VERSION_*` 读取，**不要在构建脚本里写死版本号**。

发布流程：改 `gradle.properties` → 提交 → 在同一个 commit 上打 `v<版本名>` 附注 tag → 推送分支与 tag（`git push` 不推 tag）。详见 README「版本发布流程」。

**`versionCode` 必须单调递增**，否则已安装用户无法覆盖安装。tag 仅作发布锚点，不参与版本号计算（构建不依赖 git 状态，源码 tarball / CI 浅克隆下 `git describe` 会失效）。

## 架构

- `MainActivity` — UI 层。`Handler` 以 `UPDATE_INTERVAL = 1000` 毫秒驱动 `updateUI()`，每次通过 `BatteryMonitor.getBatteryInfo()` 取快照并刷新全部 TextView 与图表。`onResume`/`onPause` 控制监测启停。
- `BatteryMonitor` — 核心逻辑。每次调用 `getBatteryInfo()` 会：读取当前电流 → 更新 min/max → 写入 `currentHistory`（`ArrayDeque`，最多 180 点）→ 计算平均电流。
- `BatteryInfo` — 不可变数据模型，承载一次采样快照。
- `TrendChartView` — 趋势折线图**抽象基类**，`onDraw` 直接绘制网格、折线、渐变填充、时间轴刻度，无外部图表库。子类只实现配置钩子：`getLineColor()`（折线/圆点/填充基准色，必须不透明）、`formatAxisLabel()`（Y 轴标签文本）、`includeZeroBaseline()`（是否含 0 基线，故意定为 abstract 以防漏实现）、`getMinSpan()`（Y 轴最小跨度，默认 0 = 不限制）、`getEmptyTextResId()`（无数据提示文案，默认 0 = 不提示）。数据由图表自身持有（`addData`，最多 180 点滚动）。
- `CurrentChartView` — 电流图，蓝色 `0xFF2196F3`、整数标签、含 0 基线。
- `TemperatureChartView` — 温度图，橙色 `0xFFFF9800`、一位小数标签、Y 轴自适应、跳过 `temperature <= 0` 的采样点、无数据时显示提示。
- `DeviceInfoUtils` — 静态工具类，基于 `Build` 类取设备信息。

## 关键业务规则（修改时格外注意）

- **电流符号约定**：`getCurrentCurrent()` 对 `BATTERY_PROPERTY_CURRENT_NOW` 的微安值**取反**，使**正值=充电、负值=放电**。此约定与 AOSP 官方 javadoc 相反，但经实测设备驱动确认，取反是刻意的，**勿改动**。
- **不支持电流检测**：`getIntProperty` 在设备不支持时返回 `Integer.MIN_VALUE`，代码将其视为 0（无数据）。
- **平均电流**：窗口 = 最近 10 个采样点（每秒 1 个，故约 10 秒），**包含 0 值**，与界面文案"最近 10 秒采样值的均值"一致。
- **最低/最高电流**：忽略 0 值（视为传感器无数据）。
- **趋势图**：最多 180 个采样点（3 分钟），新数据从右侧进入，旧数据左移滚动。两张图共用 `MAX_POINTS = 180` 与 `UPDATE_INTERVAL = 1000`，各自持有历史数据（`BatteryMonitor` 不保存历史）。
- **温度趋势图 Y 轴自适应**：与电流图不同，温度图**不强制包含 0 基线**，只按温度数据的最小/最大值上下各留 10% 余量。原因是电池温度常年在 25~35 °C，含 0 会把曲线压成直线。`includeZeroBaseline()` 因此是按图区分的钩子，**不要统一成两图共用**。
- **温度图跳过无数据采样点**：`BatteryMonitor` 在 `EXTRA_TEMPERATURE` 缺失时把温度归零，`TemperatureChartView.addData()` 对 `value <= 0` 直接 return。注意真实 0.0 °C 也会被跳过，这是刻意的既有约定（与 `BatteryMonitor` 的 `tempValue > 0` 判断一致）。副作用：温度图时间轴含义是「最近 3 分钟的有效采样点」，故其卡片说明文案与电流图不同。
- **温度图最小跨度**：`MIN_SPAN = 0.5f`，保证 4 个 `%.1f` 网格标签不会重复。**仅温度图启用**（`getMinSpan()` 默认 0）；若给电流图加上最小跨度，小跨度电流数据的 Y 轴标签会全部改变。
- **电流图渲染不可变**：重构 `TrendChartView` 时须保持填充 Path 是折线 Path 的副本、绘制顺序为 填充 → 折线 → 圆点、Y 标签基线用 `getTextSize() / 3f`、`data.size() > 1` 守卫、`Math.round` 转整数标签。这些细节改动都会让既有电流图渲染变化。左侧留白 `labelLeft` 由标签实测宽度与 44dp 保底值取大，电流标签 ≤ 5 字符（即 |电流| ≤ 9999 mA）时保底值恒定生效。

## 界面文案与代码一致性

`strings.xml` 中的文案与代码逻辑有严格对应关系，用户对此关注度高，改文案必须同步改代码、反之亦然：

- "平均（近 10 秒）"、"平均电流为最近 10 秒采样值的均值" ↔ `AVG_WINDOW_SECONDS = 10`，含 0 值
- "正数表示充电，负数表示放电" ↔ `getCurrentCurrent()` 的取反
- "时间轴为最近 3 分钟，每秒一个采样点" ↔ `MAX_POINTS = 180`、`UPDATE_INTERVAL = 1000`
- "时间轴为最近 3 分钟；传感器无数据的时间点不绘制" ↔ 温度图跳过 `temperature <= 0` 的采样点（**与电流图的说明文案不同，勿混用**）
- "-3分 / -2分 / -1分 / 现在" ↔ `MAX_POINTS = 180`、`UPDATE_INTERVAL = 1000`（两张图共用 `strings.xml` 中的 `chart_axis_*` 四条）
- "暂无电池温度数据" ↔ `TemperatureChartView` 的 `getEmptyTextResId()`，在 `data.size() < 2` 时显示
- "部分设备可能不支持电流检测" ↔ `Integer.MIN_VALUE` → 0
- "部分设备可能不支持温度检测" ↔ `EXTRA_TEMPERATURE` 缺失 → 0

## 环境

- AGP 8.1.0，JDK 8+，minSdk 21 / targetSdk 34
- 可离线编译（`--offline`）
- git 写操作（commit/push 等）需用户确认（见 `.claude/settings.json` 权限配置）