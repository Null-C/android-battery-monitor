# 电流监测器 (Current Monitor)

一个无需 Root 权限的 Android 应用，用于实时监测手机充电和放电电流。

> **注意**：本项目完全由 AI 生成，仅供学习和参考使用。

## 功能特性

- ✅ **实时电流监测** - 显示当前充电/放电电流
- ✅ **无 Root 要求** - 使用标准 BatteryManager API
- ✅ **电池信息** - 电量、电压、温度、健康度
- ✅ **电流统计** - 记录最低和最高电流值
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
- **正负值说明**: 负数表示放电，正数表示充电（应用层统一处理）

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
└── utils/
    └── DeviceInfoUtils.java     # 设备信息工具类
```

### 数据模型

```java
BatteryInfo(
    currentNow,        // 当前电流（mA）
    minCurrent,        // 最低电流
    maxCurrent,        // 最高电流
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

## 注意事项

⚠️ **重要说明**:

- 不同厂商设备的电流 API 支持程度可能不同
- 某些设备上 `getIntProperty` 可能返回 0 或不准确
- 电流值受硬件限制，部分设备可能不支持
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