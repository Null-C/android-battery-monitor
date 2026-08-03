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

## 架构

- `MainActivity` — UI 层。`Handler` 以 `UPDATE_INTERVAL = 1000` 毫秒驱动 `updateUI()`，每次通过 `BatteryMonitor.getBatteryInfo()` 取快照并刷新全部 TextView 与图表。`onResume`/`onPause` 控制监测启停。
- `BatteryMonitor` — 核心逻辑。每次调用 `getBatteryInfo()` 会：读取当前电流 → 更新 min/max → 写入 `currentHistory`（`ArrayDeque`，最多 180 点）→ 计算平均电流。
- `BatteryInfo` — 不可变数据模型，承载一次采样快照。
- `CurrentChartView` — 自定义折线图 View，`onDraw` 直接绘制网格、折线、渐变填充，无外部图表库。
- `DeviceInfoUtils` — 静态工具类，基于 `Build` 类取设备信息。

## 关键业务规则（修改时格外注意）

- **电流符号约定**：`getCurrentCurrent()` 对 `BATTERY_PROPERTY_CURRENT_NOW` 的微安值**取反**，使**正值=充电、负值=放电**。此约定与 AOSP 官方 javadoc 相反，但经实测设备驱动确认，取反是刻意的，**勿改动**。
- **不支持电流检测**：`getIntProperty` 在设备不支持时返回 `Integer.MIN_VALUE`，代码将其视为 0（无数据）。
- **平均电流**：窗口 = 最近 10 个采样点（每秒 1 个，故约 10 秒），**包含 0 值**，与界面文案"最近 10 秒采样值的均值"一致。
- **最低/最高电流**：忽略 0 值（视为传感器无数据）。
- **趋势图**：最多 180 个采样点（3 分钟），新数据从右侧进入，旧数据左移滚动。

## 界面文案与代码一致性

`strings.xml` 中的文案与代码逻辑有严格对应关系，用户对此关注度高，改文案必须同步改代码、反之亦然：

- "平均（近 10 秒）"、"平均电流为最近 10 秒采样值的均值" ↔ `AVG_WINDOW_SECONDS = 10`，含 0 值
- "正数表示充电，负数表示放电" ↔ `getCurrentCurrent()` 的取反
- "时间轴为最近 3 分钟，每秒一个采样点" ↔ `MAX_POINTS = 180`、`UPDATE_INTERVAL = 1000`
- "部分设备可能不支持电流检测" ↔ `Integer.MIN_VALUE` → 0

## 环境

- AGP 8.1.0，JDK 8+，minSdk 21 / targetSdk 34
- 可离线编译（`--offline`）
- git 写操作（commit/push 等）需用户确认（见 `.claude/settings.json` 权限配置）