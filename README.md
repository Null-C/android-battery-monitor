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
- ✅ **设备信息** - 显示手机型号、制造商和系统版本
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

通过 `ACTION_BATTERY_CHANGED` 广播获取：

- 电量百分比 (`EXTRA_LEVEL`, `EXTRA_SCALE`)
- 电压（毫伏）(`EXTRA_VOLTAGE`)
- 温度（0.1°C）(`EXTRA_TEMPERATURE`)
- 健康度 (`EXTRA_HEALTH`)

#### 3. 获取设备信息

使用 Android SDK 的 `Build` 类：

- `Build.MANUFACTURER` - 制造商
- `Build.MODEL` - 手机型号
- `Build.VERSION.RELEASE` - Android 版本

### 应用架构

```
app/src/main/java/com/batterymonitor/app/
├── MainActivity.java            # 主界面
├── BatteryMonitor.java          # 电池监测核心类
├── model/
│   └── BatteryInfo.java         # 电池信息数据模型
├── view/
│   ├── TrendChartView.java      # 趋势折线图基类（网格 / 折线 / 渐变填充 / 时间轴刻度）
│   ├── CurrentChartView.java    # 电流趋势图（蓝色，Y 轴含 0 基线）
│   └── TemperatureChartView.java # 温度趋势图（橙色，Y 轴自适应温度范围）
└── utils/
    └── DeviceInfoUtils.java     # 设备信息工具类
```

### 趋势图实现

两张趋势图共用基类 `TrendChartView`，子类只提供颜色、Y 轴标签格式与范围策略：

| | 电流趋势图 | 温度趋势图 |
|---|---|---|
| 折线颜色 | 蓝色 `#2196F3` | 橙色 `#FF9800` |
| Y 轴标签 | 整数（mA） | 一位小数（°C） |
| Y 轴范围 | **始终包含 0 基线**（正负电流需对比） | **自适应温度数据范围**（否则 25~35°C 的波动会被压成直线） |
| 无数据时 | 仅绘制空白网格 | 居中显示「暂无电池温度数据」 |

温度图会**跳过传感器无数据的采样点**（`EXTRA_TEMPERATURE` 缺失时 `BatteryMonitor` 将温度归零），因此其时间轴为「最近 3 分钟的有效采样点」——传感器全程不可用的设备上该图始终为空。

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

# 清理构建
./gradlew clean
```

### 使用 adb 运行

```bash
# 安装 APK
adb install app/build/outputs/apk/debug/app-debug.apk

# 启动应用
adb shell am start -n com.batterymonitor.app/.MainActivity
```

## 版本发布流程

版本号集中维护在项目根目录的 `gradle.properties`，`app/build.gradle` 从这里读取：

| 字段 | 含义 |
|------|------|
| `VERSION_CODE` | 整数，**必须单调递增** |
| `VERSION_NAME` | 显示给用户的版本字符串，形如 `1.0` |

> ⚠️ `VERSION_CODE` 若不增大，Android 系统会认为不是新版本，已安装用户**无法覆盖安装**（报 `INSTALL_FAILED_VERSION_DOWNGRADE`），必须先卸载。因此每次发版务必 +1。

### 发布一个新版本

```bash
# 1. 修改 gradle.properties 中的 VERSION_CODE（+1）与 VERSION_NAME
#    例如 1 → 2、1.0 → 1.1

# 2. 提交这次版本号改动
git commit -am "发布 1.1"

# 3. 在同一个 commit 上打附注 tag，命名沿用 v<版本名>
git tag -a v1.1 -m "Release 1.1"

# 4. 推送分支与 tag（注意：git push 不会自动推送 tag）
git push && git push origin v1.1

# 5. 构建发布包
./gradlew assembleRelease
```

### 为什么版本号写在文件里，而不是由 git tag 自动推导

tag 只作为**发布锚点**，用于回溯"哪个 commit 对应哪个版本"，不参与版本号计算。原因是构建不应依赖 git 状态：

- GitHub 的 "Download ZIP" 得到的源码 tarball 没有 `.git` 目录
- CI 默认的浅克隆（`fetch-depth: 1`）拉不到 tag，`git describe` 会失败或回退到错误值

版本号写在文件里，任何环境下都能构建出正确的 APK。

## 注意事项

⚠️ **重要说明**:

- 不同厂商设备的电流 API 支持程度可能不同
- 部分设备不支持电流检测，此时 `getIntProperty` 返回 `Integer.MIN_VALUE`，应用显示为 0
- 部分设备不支持温度检测，此时 `EXTRA_TEMPERATURE` 缺失，温度显示为 0、温度趋势图不绘制该采样点
- 正值表示充电，负数表示放电
- 电池健康度显示为状态描述（良好、过热、损坏等）

## 电池健康度说明

应用会显示以下健康状态之一：

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
