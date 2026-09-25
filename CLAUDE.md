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
- `TrendChartView` — 趋势折线图**抽象基类**，`onDraw` 直接绘制网格、折线、渐变填充、时间轴刻度，无外部图表库。子类只实现配置钩子：`getLineColor()`（折线/圆点/填充基准色，必须不透明）、`formatAxisLabel()`（Y 轴标签文本）、`includeZeroBaseline()`（是否含 0 基线，故意定为 abstract 以防漏实现）、`getMinSpan()`（Y 轴最小跨度，默认 0 = 不限制，对「采样值全部相等」的退化情形同样生效）、`getEmptyTextResId()`（无数据提示文案，默认 0 = 不提示）。数据由图表自身持有（`addData`，最多 180 点滚动）。
- `CurrentChartView` — 电流图，`chart_line_current`（墨绿青）、整数标签、含 0 基线。
- `TemperatureChartView` — 温度图，`chart_line_temperature`（深玫红）、一位小数标签、Y 轴自适应（`MIN_SPAN = 0.5f`）、跳过 `temperature <= 0` 的采样点、无数据时显示提示。
- `BatteryLevelChartView` — 电量图，`chart_line_battery_level`（深紫）、整数标签、Y 轴自适应（`MIN_SPAN = 3f`）、跳过 `batteryLevel <= 0` 的采样点、无数据时显示提示。
- `DeviceInfoUtils` — 静态工具类，基于 `Build` 类取设备信息。

`TrendChartView` 的网格色与坐标轴文字色从 `colors.xml` 读取（构造函数里一次读入），子类的折线色经 `resolveColor()` 惰性读取。图内的绘制几何常量（`CHART_TOP_DP`、`LABEL_BASELINE_DP`、`LABEL_LEFT_MIN_DP`、`AXIS_TEXT_SIZE_SP`）刻意留在 Java 里并附公式注释，**不进 `dimens.xml`** —— 它们是绘制常量，与布局尺寸不是一类东西。

资源文件职责：`colors.xml` 是唯一色源，`dimens.xml` 管间距与字号，`styles.xml` 管主题与可复用文字样式，`drawable/bg_panel.xml` 是面板背景。

## 关键业务规则（修改时格外注意）

- **电流符号约定**：`getCurrentCurrent()` 对 `BATTERY_PROPERTY_CURRENT_NOW` 的微安值**取反**，使**正值=充电、负值=放电**。此约定与 AOSP 官方 javadoc 相反，但经实测设备驱动确认，取反是刻意的，**勿改动**。
- **不支持电流检测**：`getIntProperty` 在设备不支持时返回 `Integer.MIN_VALUE`，代码将其视为 0（无数据）。
- **平均电流**：窗口 = 最近 10 个采样点（每秒 1 个，故约 10 秒），**包含 0 值**，与界面文案"最近 10 秒采样值的均值"一致。
- **最低/最高电流**：忽略 0 值（视为传感器无数据）。
- **趋势图**：最多 180 个采样点（3 分钟），新数据从右侧进入，旧数据左移滚动。三张图共用 `MAX_POINTS = 180` 与 `UPDATE_INTERVAL = 1000`，各自持有历史数据（`BatteryMonitor` 不保存历史）。
- **温度趋势图 Y 轴自适应**：与电流图不同，温度图**不强制包含 0 基线**，只按温度数据的最小/最大值上下各留 10% 余量。原因是电池温度常年在 25~35 °C，含 0 会把曲线压成直线。`includeZeroBaseline()` 因此是按图区分的钩子，**不要统一成两图共用**。
- **温度图跳过无数据采样点**：`BatteryMonitor` 在 `EXTRA_TEMPERATURE` 缺失时把温度归零，`TemperatureChartView.addData()` 对 `value <= 0` 直接 return。注意真实 0.0 °C 也会被跳过，这是刻意的既有约定（与 `BatteryMonitor` 的 `tempValue > 0` 判断一致）。副作用：温度图时间轴含义是「最近 3 分钟的有效采样点」，故其卡片说明文案与电流图不同。
- **温度图最小跨度**：`MIN_SPAN = 0.5f`，保证 4 个 `%.1f` 网格标签不会重复。温度图与电量图启用（`getMinSpan()` 默认 0 = 不限制）；**不要给电流图设置** —— 小跨度电流数据的 Y 轴标签会全部改变。
- **电量趋势图 Y 轴自适应**：与温度图同理**不强制包含 0 基线**，否则常年在高位的电量会被压成直线。`MIN_SPAN = 3f` 是必需的：电量是整数百分比，3 分钟内常常一个采样点都不变，跨度退化成 0 时若不强制最小跨度，4 个整数标签必然重复（`86 / 85 / 85 / 84`）。
- **电量图跳过无数据采样点**：`BatteryMonitor` 在 `EXTRA_LEVEL` / `EXTRA_SCALE` 缺失时把电量留为 0，`BatteryLevelChartView.addData()` 对 `value <= 0` 直接 return。真实 0% 电量同样会被跳过（此时设备即将关机），与温度图跳过 0 的既有约定一致。副作用：电量图时间轴含义也是「最近 3 分钟的有效采样点」，故其卡片说明文案与电流图、温度图都不同。
- **`computeRange()` 里最小跨度与退化保护的顺序**：必须先判 `span < getMinSpan()`，再兜底 `max - min <= 0` 的退化保护。顺序反过来会让 `MIN_SPAN` 在「采样值全部相等」时被静默短路 —— 而这正是电量图最常出现的情形。电流图（`MIN_SPAN = 0`）在新旧顺序下走的是同一条 `±1` 路径，渲染逐位不变；温度图只在「180 点全相同」时轴范围收紧（如恒 41.3°C：`42.5 / 41.7 / 40.9 / 40.1` → `41.6 / 41.4 / 41.2 / 41.0`）。
- **电流图渲染不变量**：重构 `TrendChartView` 时须保持填充 Path 是折线 Path 的副本、绘制顺序为 填充 → 折线 → 圆点、Y 标签基线用 `getTextSize() / 3f`、`data.size() > 1` 守卫、`Math.round` 转整数标签。这些细节改动都会让既有电流图渲染变化。左侧留白 `labelLeft` 由标签实测宽度与 44dp 保底值取大，电流标签 ≤ 5 字符（即 |电流| ≤ 9999 mA）时保底值恒定生效。

  **注意区分**：上面这份清单是**渲染不变量**；而图内边距（`CHART_TOP_DP` / `LABEL_BASELINE_DP`）、轴字号（`AXIS_TEXT_SIZE_SP`）、轴字体属于**绘制几何**，样式改版时允许调整（会改变像素输出）。轴字号不要升到 12dp 以上：等宽族数字前进量约 0.6em，12dp 下 5 字符标签 + 8dp 间隙恰好卡在 44dp 保底边界，一旦翻边 `labelLeft` 会随标签位数在 44↔47dp 间跳变，整张图横向抖动。

## 界面文案与代码一致性

`strings.xml` 中的文案与代码逻辑有严格对应关系，用户对此关注度高，改文案必须同步改代码、反之亦然：

- "平均（近 10 秒）" ↔ `AVG_WINDOW_SECONDS = 10`，含 0 值
- "正数表示充电，负数表示放电" ↔ `getCurrentCurrent()` 的取反
- "时间轴为最近 3 分钟，每秒一个采样点" ↔ `MAX_POINTS = 180`、`UPDATE_INTERVAL = 1000`
- "时间轴为最近 3 分钟；传感器无数据的时间点不绘制" ↔ 温度图跳过 `temperature <= 0` 的采样点（**与电流图、电量图的说明文案都不同，勿混用**）
- "时间轴为最近 3 分钟；无数据的时间点不绘制" ↔ 电量图跳过 `batteryLevel <= 0` 的采样点（**同上，勿混用**）
- "-3分 / -2分 / -1分 / 现在" ↔ `MAX_POINTS = 180`、`UPDATE_INTERVAL = 1000`（三张图共用 `strings.xml` 中的 `chart_axis_*` 四条）
- "暂无电池温度数据" ↔ `TemperatureChartView` 的 `getEmptyTextResId()`，在 `data.size() < 2` 时显示
- "暂无电池电量数据" ↔ `BatteryLevelChartView` 的 `getEmptyTextResId()`，同样在 `data.size() < 2` 时显示
- "健康度共 7 级：未知、良好、过热、损坏、过压、故障、低温" ↔ `battery_health` 数组（7 项，索引 = 健康度值 − 1）与 `MainActivity.getHealthString()`；**改数组必须同步改这句**
- 说明面板中原有的"部分设备可能不支持电流检测""部分设备可能不支持温度检测""平均电流为最近 10 秒采样值的均值""数据每秒自动更新"四条已按用户要求移除。**行为一律不变**（仍是 `Integer.MIN_VALUE` / 缺失 `EXTRA_*` → 0，见「关键业务规则」），不要在后续调整说明文案时自行加回。

## 样式约定

界面取向：**浅色工程图纸 / 万用表面板**。等宽字体、1dp 细描边（不用 `elevation` 阴影）、墨色系配色、紧凑排版。

- **改颜色只改 `colors.xml`**，改尺寸只改 `dimens.xml`；布局与 drawable 里不写字面量颜色或 dp/sp 值（唯一例外是 `layout_weight` 用的 `0dp`）
- **一色一族**：电流族 `#0F766E`（数值文本与折线同色）、温度族 `#9D174D`、电量族 `#6D28D9`，其余数值统一墨色 `#1F2937`。三个色相刻意拉开（175° / 340° / 265°，两两相隔 75°~90°），保证三张堆叠的图在同一亮度下仍能一眼区分
- **全局等宽字体走 `android:textViewStyle`**（`styles.xml` 的 `AppTextView`），不要用主题的 `android:fontFamily` —— 后者平台自带主题从未设置过，不可靠
- **含 emoji 的区块标题显式 `android:fontFamily="sans-serif"`**：`monospace` 族不含 CJK 与 emoji 字形，API 21/22 上「非默认族 + 彩色 emoji 字体」的回退在部分 ROM 上有豆腐块记录
- **状态栏固定主色 `#0F766E`**：`windowLightStatusBar` 与 `SYSTEM_UI_FLAG_LIGHT_STATUS_BAR` 都是 API 23，minSdk 21 上做不到「浅色状态栏 + 深色图标」；同理不设 `navigationBarColor`（`windowLightNavigationBar` 是 API 27）。因此**本项目不建 `values-v23`** —— 跨 config 的 `<style>` 不合并，v23 里漏写 `parent` 会让 API 23+ 掉回无主题状态
- **API 级别红线**（minSdk 21 下会被静默忽略，lint 只有 warning 级，拦不住构建）：`android:lineHeight`（28）、`android:fontWeight`（26）、`android:textFontWeight`（28）、`android:breakStrategy`（23）、`android:layout_marginHorizontal`（26）、XML 的 `android:clipToOutline`（31，`setClipToOutline()` 方法才是 21）。行距只能用 `lineSpacingMultiplier` / `lineSpacingExtra`，加粗只能用 `textStyle="bold"`
- **数据格式串与标签文案不得随样式改动**：`strings.xml` 里的 `+%1$d mA`、`%1$.1f°C` 等格式串，以及全部标签、标题（含 emoji）、说明文案，都属于「展示的数据内容」，样式改版只允许动字号、颜色、字间距与排版
- **不要用 `android:textAllCaps`**：会把 `mA` 变成 `MA`，破坏单位语义
- **不要在右对齐的数值上加 `android:letterSpacing`**：Android 会在最后一个字符之后也加间距，右对齐文字会看起来左边多缩一块。`letterSpacing` 的单位是 em 的无量纲浮点（写 `0.06`，写 `0.06em` 会被 AAPT2 报 `invalid float`）

### 布局陷阱

- **`ScrollView` 内不要用纵向 `layout_weight`**：`ScrollView` 以 `UNSPECIFIED` 测量子 View，纵向权重会塌成 0 高度（横向权重不受影响，宽度是 EXACTLY）
- **两列表格用「两个 50% 半 + 每半内 label/value 双 weight」结构**：不要写成「label + value + label + value」的单行四孩子结构（每行 value 宽度不同会让第二列起点逐行错位），也不要让 label 用 `weight` 而 value 用 `wrap_content`（value 超宽时会把 label 压成 0 宽直接消失）
- **1dp 分隔线用 `<View>` 或 `<Space>`，不要用 `android:divider` 配纯色**：`ColorDrawable` 的 intrinsic height 是 -1，`LinearLayout` 会算出负高度，结果什么线都画不出来
- **`<shape>` 里不要写 `<padding>` 元素**：`setBackgroundDrawable()` 会用它静默覆盖 View 自己的 padding
- 页面底色由主题的 `windowBackground` 提供，根节点不要重复铺一层（否则 lint 报 `Overdraw`）

## 环境

- AGP 8.1.0，JDK 8+，minSdk 21 / targetSdk 34
- `assembleDebug` / `:app:compileDebugJavaWithJavac` / `:app:lint` 可完全离线（`--offline`）
- **`assembleRelease` 不能离线**：它依赖 `lint-gradle`，本机 Gradle 缓存里没有该构件，`--offline` 会在 `lintVitalAnalyzeRelease` 阶段报依赖解析失败（与源码无关，是环境限制）。需要联网跑一次才能触发 `lintVitalRelease` 与 `shrinkReleaseRes`
- git 写操作（commit/push 等）需用户确认（见 `.claude/settings.json` 权限配置）