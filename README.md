# 电流监测器 (Battery Monitor)

一个无需 Root 权限的 Android 应用，用于实时监测手机电池充电和放电电流。

> **注意**：本项目完全由 AI 生成，仅供学习和参考使用。

## 功能特性

- ✅ **实时电流监测** - 显示当前充电/放电电流
- ✅ **无 Root 要求** - 使用标准 BatteryManager API
- ✅ **电池信息** - 电量、电压、温度、健康度
- ✅ **电流统计** - 记录最低、最高和平均电流值（平均为最近 10 秒采样值的均值）
- ✅ **电流趋势图** - 折线图展示最近 3 分钟电流变化
- ✅ **温度趋势图** - 折线图展示最近 3 分钟电池温度变化
- ✅ **电量趋势图** - 折线图展示最近 3 分钟电池电量变化
- ✅ **前台防熄屏** - 前台常亮最长 3 分钟（与时间轴等长），触摸任意位置重新计时，顶栏右侧显示剩余时间
- ✅ **设备信息** - 显示手机型号、制造商和系统版本
- ✅ **紧凑工程风界面** - 等宽字体、1dp 细描边、主流机型一屏内呈现
- ✅ **自动刷新** - 每秒自动更新数据

## 技术实现

### 核心功能

#### 1. 获取电流（无需 Root）

使用 `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW` API：

```java
BatteryManager batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);
int currentNow = batteryManager.getIntProperty(
    BatteryManager.BATTERY_PROPERTY_CURRENT_NOW
);
```

- **支持版本**: Android 5.0+ (API 21+)
- **返回单位**: 微安（μA），需转换为毫安（mA）
- **正负值说明**: 应用统一处理后，正值表示充电，负值表示放电
- **不支持检测**: 部分设备不支持电流检测，此时 `getIntProperty` 返回 `Integer.MIN_VALUE`，应用会将其视为无数据（0）

#### 2. 获取电池信息

通过 `ACTION_BATTERY_CHANGED` sticky 广播获取（直接取最后一条广播，不常驻监听、不申请权限）：

- 电量百分比 (`EXTRA_LEVEL`, `EXTRA_SCALE`)
- 电压（毫伏）(`EXTRA_VOLTAGE`)
- 温度（0.1°C）(`EXTRA_TEMPERATURE`)
- 健康度 (`EXTRA_HEALTH`)

任一 extra 缺失时对应字段归零 / 保持默认（电量 `0`、温度 `0`、电压 `0`、健康度 `1`「未知」），不抛异常也不报错。温度与电量的 `0` 值正好是「传感器无数据」的哨兵值，对应趋势图会跳过该采样点。

#### 3. 获取设备信息

使用 Android SDK 的 `Build` 类：

- `Build.MANUFACTURER` - 制造商
- `Build.MODEL` - 手机型号
- `Build.VERSION.RELEASE` - Android 版本

#### 4. 前台防熄屏

应用在前台时用窗口标记 `WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON` 保持屏幕点亮，最长 3 分钟 —— 时长与图表时间轴等长，目的是让用户能连续看完一整条时间轴。**触摸界面任意位置都会重新计时**，3 分钟无操作则摘掉标记、屏幕交回系统超时策略；从后台回到前台重新给满 3 分钟，离开前台立即解除。

顶栏右侧有一行不显眼的倒计时（如 `常亮 2:47`），实时显示当前常亮窗口还剩多久；窗口失效时显示占位符 `--:--`（此时屏幕已交回系统超时策略，任何触摸都会立刻重新开始计时）。

实现要点：

- 触摸用 `Activity.dispatchTouchEvent()` 捕获，而不是根布局的点击监听 —— `ScrollView` 与图表会消费触摸事件，根节点收不到点击
- **不使用 `WakeLock`、不需要 `WAKE_LOCK` 权限**，`AndroidManifest.xml` 无需改动

### 应用架构

```
app/src/main/java/com/batterymonitor/app/
├── MainActivity.java            # 主界面（含前台防熄屏计时）
├── BatteryMonitor.java          # 电池监测核心类
├── model/
│   └── BatteryInfo.java         # 电池信息数据模型
├── view/
│   ├── TrendChartView.java      # 趋势折线图基类（网格 / 折线 / 渐变填充 / 时间轴刻度）
│   ├── CurrentChartView.java    # 电流趋势图（墨绿青，Y 轴含 0 基线）
│   ├── TemperatureChartView.java # 温度趋势图（深玫红，Y 轴自适应温度范围）
│   └── BatteryLevelChartView.java # 电量趋势图（深紫，Y 轴自适应电量范围）
└── utils/
    └── DeviceInfoUtils.java     # 设备信息工具类
```

## 界面样式

界面取向是**浅色工程图纸 / 万用表面板**：等宽字体、1dp 细描边替代 Material 阴影、墨色系配色、紧凑排版让主要内容在主流机型上一屏内呈现。

每个数据族固定一个色相，数值文本与对应的趋势折线同色：电流 `#0F766E`（墨绿青）、温度 `#9D174D`（深玫红）、电量 `#6D28D9`（深紫）。其余数值统一墨色 `#1F2937`，页面底色 `#F2F4F7`，面板白底，状态栏用主色 `#0F766E`。

样式资源按职责分文件：

| 文件 | 职责 |
|---|---|
| `res/values/colors.xml` | 颜色，全项目唯一色源 |
| `res/values/dimens.xml` | 间距、描边、圆角、图高与字号 |
| `res/values/styles.xml` | 主题（页面底色、状态栏）与可复用文字样式 |
| `res/drawable/bg_panel.xml` | 面板背景：白底 + 1dp 描边 + 2dp 圆角 |

### 数据模型

```java
BatteryInfo(
    currentNow,        // 当前电流（mA）
    minCurrent,        // 最低电流
    maxCurrent,        // 最高电流
    avgCurrent,        // 平均电流（最近 10 秒采样值的均值，含 0 值）
    batteryLevel,      // 电量百分比
    temperature,       // 温度（°C）
    voltage,           // 电压（V）
    batteryHealth,     // 电池健康度
    phoneModel,        // 手机型号
    manufacturer,      // 制造商
    androidVersion     // 系统版本
)
```

## 技术栈

- **语言**: Java
- **UI**: XML 布局
- **最低 API**: 21 (Android 5.0)
- **目标 API**: 34 (Android 14)

## 构建和运行

### 环境要求

- JDK 8 或更高版本
- Android SDK (API 21+)
- Android Studio 或命令行工具

### 使用 Gradle 构建

```bash
# 编译 Debug 版本
./gradlew assembleDebug

# 安装到设备
./gradlew installDebug

# 只做 Java 编译验证（改完代码后最常用的快速检查，可完全离线）
./gradlew :app:compileDebugJavaWithJavac --offline

# 清理构建
./gradlew clean
```

> 本项目的 `assembleDebug`、`:app:compileDebugJavaWithJavac` 与 `:app:lint` 都能完全离线执行（加 `--offline`）。唯一例外是 `assembleRelease`：它要跑 `lintVitalRelease` 与 `shrinkReleaseRes`，依赖 `lint-gradle` 构件，首次需要联网拉取后才能离线重复执行 —— 这与源码无关，是环境限制。

### 使用 adb 运行

```bash
# 安装 APK
adb install app/build/outputs/apk/debug/app-debug.apk

# 启动应用
adb shell am start -n com.batterymonitor.app/.MainActivity
```

## 版本号

版本号集中维护在项目根目录的 `gradle.properties`，`app/build.gradle` 从这里读取：

| 字段 | 含义 |
|------|------|
| `VERSION_CODE` | 整数，**必须单调递增**，否则已安装用户无法覆盖安装（报 `INSTALL_FAILED_VERSION_DOWNGRADE`） |
| `VERSION_NAME` | 显示给用户的版本字符串，形如 `1.0` |

## 注意事项

⚠️ **重要说明**:

- 不同厂商设备的电流 API 支持程度可能不同
- 部分设备不支持电流检测，此时 `getIntProperty` 返回 `Integer.MIN_VALUE`，应用显示为 0
- 部分设备不支持温度检测，此时 `EXTRA_TEMPERATURE` 缺失，温度显示为 0、温度趋势图不绘制该采样点
- 广播中缺少 `EXTRA_LEVEL` / `EXTRA_SCALE` 时电量留为 0，电量趋势图同样不绘制该采样点（真实 0% 电量也会被跳过 —— 此时设备即将关机）
- 电流的最低 / 最高值**忽略 0 值**（视为传感器无数据）；若整个运行期一个非 0 采样都没采到，两者会回退显示为当前电流，而不是初值
- 正值表示充电，负数表示放电
- 电池健康度显示为状态描述（良好、过热、损坏等）

## 电池健康度说明

应用会显示以下健康状态之一（界面底部的说明也会列出这 7 个级别）：

| 值 | 状态 | 说明 |
|----|------|------|
| 1 | 未知 | 无法确定电池状态 |
| 2 | 良好 | 电池状态正常 |
| 3 | 过热 | 电池温度过高 |
| 4 | 损坏 | 电池已损坏 |
| 5 | 过压 | 电压异常 |
| 6 | 故障 | 电池故障 |
| 7 | 低温 | 电池温度过低 |

## License

MIT License
