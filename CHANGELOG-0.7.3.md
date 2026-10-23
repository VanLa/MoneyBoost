# MoneyBoost 0.7.3

此版本已在独立工作目录构建，未推送、未发布 Maven，未修改 Unity 项目当前引用或线上 Ads 转化目标。

## 收入事件

- 移除 SDK 手动发送的标准 `ad_impression`。AdMob / Firebase 自动采集继续负责标准广告展示及广告收入，避免同一收入被自动和手动各报一次。
- 按需求保留默认发送的 Taichi 事件：每次 paid 回调发送 `Ad_Impression_Revenue`；本地累计至少 USD 0.01 时发送 `Total_Avenue_001`，事件 value 为累计金额，然后清零。沿用原有 USD 金额和本地缓存，未增加开关。
- 收入处理加同步，避免不同广告回调并发覆盖累计金额；拒绝负数、NaN、无穷金额。
- 两个 Taichi 事件与标准收入代表同一批收入，不能相加用于 BI。以后使用 Taichi 时选择相应转化目标，不能将这些重叠事件同时计入同一个收入优化目标。

## 广告加载

- 插屏、激励、开屏统一使用 GMA Next-Gen Preloader。授权且初始化完成后，每个 adapter 生命周期只注册一次；SDK 自动缓存、重试、过期更新、补货。仅在即将展示时 pollAd，不保存待展示的 RewardedAd 对象。
- 删除 SDK 自建的全屏失败退避调度器和基于库存的每 5 秒补货逻辑。前台健康检查只恢复未成功的 Preloader 注册，注册成功后不会重复 start 或自行补货。就绪状态直接查询 Google 库存，不再依赖可能滞后的 ready 标记。
- 保留隐私授权检查、展示回调、Unity 公共接口、开屏超时/时机控制。UI 排队后再次检查授权和 Activity，并防止同时消耗两条全屏广告；激励完成回调只派发一次。
- Banner / 折叠 Banner 继续使用 AdView；全屏 Preloader 不适用 Banner。保留现有普通 Banner 首次失败补载；已加载 Banner 的自动刷新由 Google SDK 及广告单元设置负责。

## 依赖与交付

- MoneyBoost Maven 版本：0.7.3。
- `ads-mobile-sdk`：1.5.0 → 1.6.0，其他依赖不变；Meta 6.22.0.1 / Audience Network 6.22.0 保留。
- 继续在 Gradle 与生成的 POM 排除旧 GMA Ads/Ads Lite/Ads API，避免 Duplicate class。
- 已构建 AAR、POM、校验文件：`frpianosdk/build/release-package/com/joyboost/moneyboost/0.7.3/`。
- AAR 本身不包含传递依赖；集成时同时使用生成 POM 中依赖，不能把裸 AAR 当作包含 Google/Firebase 的整包。
- 发布 Maven 并更新宿主依赖后，需要重新打包、发布 App。旧安装仍会执行旧上报代码，历史重复数据不会被 SDK 修复追溯删除。

## 验证

- `:frpianosdk:assembleRelease`、`:frpianosdk:generatePomFileForReleasePublication` 通过。
- Runtime classpath 核对：Next-Gen 1.6.0、Meta 6.22.0.1，无旧 GMA Ads/Ads Lite/Ads API。
- `python3 tests/run_preloading_test.py`：真实 adapter 源码 + Google/Android 模拟 transport；三种全屏格式单次注册、失败/耗尽不重启、SDK 库存查询、展示前取广告、队列执行时撤销授权、多次展示保护、奖励单次派发、销毁库存。
- `python3 tests/run_firebase_diagnostics_test.py`：已编译 AAR；Taichi 两事件、阈值金额/清零、非法收入、无手动标准事件、Firebase 诊断队列。
- `python3 tests/run_ump_gate_test.py`：已编译 AAR；UMP gate、撤销授权、诊断事件。
- 尚未做 Android 真机/测试广告展示与 Firebase DebugView 验证；上述测试不能替代真实广告网络回调、填充率和收入核对。

## 复查修复

- 注册调用抛异常时，原实现会把 started 留在 true，后续 Load 永久跳过。现已回滚 started/loading；前台健康检查可重新注册。
- show() 抛异常后的清理与异步回调原来使用两套终态，迟到的关闭回调会再次清理，并可能清掉下一条广告的 showing 标记。现统一使用同一个原子终态；已失败的激励广告不会因迟到奖励回调发奖。
- 新增三种格式的异常注入回归。旧 adapter 的迟到关闭问题已用旧编译类复现，新代码通过相同检查。
- 删除未使用的旧 Native 全屏/半屏倒计时、与插屏交替及关闭补货常量，保留有效的冷/热开屏开关。

## 官方参考

- https://developers.google.com/admob/android/next-gen/ad-preloading
- https://developers.google.com/admob/android/next-gen/rel-notes
- https://developers.google.com/admob/android/next-gen/mediation/meta
