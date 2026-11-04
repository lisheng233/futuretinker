# Future Tinker

匠魂（Tinkers' Construct）附属模组：把高版本原版 Minecraft 新增的装备与机制，用匠魂的
材料、部件与强化系统重新实现。设计与里程碑见 [PLAN.md](PLAN.md)。

## 目标版本组合（已锁定）

| 项 | 值 | 依据 |
| --- | --- | --- |
| Minecraft | 1.20.1 | 由 PLAN/需求锁定 |
| Forge | 47.3.0 | `gradle.properties` |
| 匠魂 TConstruct | 3.12.1.236（编译锁定，运行期 `[3.9.0,)`） | `libs/` 里的 jar，已核验 `modId=tconstruct` |
| Mantle | 1.11.122（运行期 `[1.11.0,)`） | `libs/` 里的 jar，已核验 `modId=mantle` |
| Java | 17 | Forge 1.20.1 线 |
| pack_format | 15 | 从 1.20.1 `client-extra.jar` 的 `version.json` 读出（`pack_version.resource/data = 15`） |
| GameTest 结构 DataVersion | 3465 | 同上（`world_version = 3465`） |

`libs/` 里的匠魂 / Mantle jar 是**生产 reobf 版**，通过 `flatDir` 仓库 + `fg.deobf` 引入：
ForgeGradle 只对「仓库解析出来的依赖」做重映射，用 `files(...)` 会静默使用混淆版。
deobf 产物在 `forge_gradle/deobf_dependencies/.../*_mapped_official_1.20.1.jar`。

## 目录结构

```
futuretinker/
├── build.gradle              # ForgeGradle 6.0.54；含 client/server/data/gameTestServer 四个 run
├── gradle.properties         # 版本与 mods.toml 占位符的值
├── libs/                     # 锁定版本的 TConstruct / Mantle 生产 jar（故意入仓，保证离线可复现）
├── gameteststructures/
│   └── empty3x3x3.snbt       # GameTest 模板（3×3×3 空结构，DataVersion 3465）
├── src/main/java/com/lisheng/futuretinker/
│   ├── FutureTinker.java     # @Mod 主类：注册配置 → 提前读开关 → 接模块表
│   ├── config/FutureTinkerConfig.java   # 模块开关（ForgeConfigSpec + 注册表填充前的提前读取）
│   ├── module/FtModule.java             # 模块生命周期契约
│   ├── module/ModuleRegistry.java       # 模块表 + 唯一的开关闸门（RegisterEvent）
│   ├── wolfarmor/WolfArmorModule.java   # 模块一（M1 只到「可独立启停」）
│   ├── spear/SpearModule.java           # 模块二（同上）
│   └── gametest/ModuleGateTests.java    # 判定：闸门是否真的接在配置上
├── src/main/resources/
│   ├── META-INF/mods.toml    # 硬依赖 forge / minecraft / tconstruct / mantle
│   ├── pack.mcmeta
│   └── assets/futuretinker/lang/{en_us,zh_cn}.json
└── tools/verify_gametest.py  # 判定脚本（要求日志证据，不只看退出码）
```

## 怎么跑

```bash
# 构建可发布的 jar -> build/libs/futuretinker-0.1.0.jar
./gradlew build                       # Windows: gradlew.bat build

# 判定（推荐都用这个，而不是直接看 gradle 的退出码）
python tools/verify_gametest.py       # 退出码 0 = 通过；2 = 根本没跑到测试
python tools/verify_gametest.py --fault   # 注入故障：退出码 0 = 判定确实会说「不」
```

Windows 上 Python 通常叫 `python` 或 `py -3`（`python3` 往往是 0 字节的 Store 存根）。

### 为什么需要 `tools/verify_gametest.py`

实测过：**模组加载期崩溃时 `runGameTestServer` 也报 `BUILD SUCCESSFUL`、退出码 0**。
那种情况下「退出码 = 失败的必需测试数」不成立（游戏没走到测试），只看退出码会把一次彻底
失败读成通过。脚本的判据是日志证据 + 退出码交叉验证：

- 必须出现 `Running test batch '...' (N tests)...`，否则判定为「没跑到测试」（退出码 2）；
- 必须出现 `All N required tests passed :)` 或 `N required tests failed :(`；
- 日志与 gradle 退出码必须自洽，否则报「证据自相矛盾」；
- **交付物新鲜度**：`build/libs/*.jar` 必须比 `src/` 下任何文件都新，否则退出码 **5**。
  `runGameTestServer` 只编译类、**不重建 jar**，踩过一次——有人拿旧包测了半天，以为代码没改。
  所以交付前要么跑 `gradlew build`，要么让判定脚本替你挡住（`--no-jar-check` 可跳过）。

## 模块开关怎么生效（这条有实测依据）

`config/futuretinker-common.toml` 里的 `modules.<id>.enabled` 决定模块存不存在：
**关掉的模块不向任何注册表写入内容**，对整合包来说它是「不存在」而不是「数值为 0」。

这条契约要求开关在注册表填充之前就有值，而实测的事件顺序是（1.20.1 / Forge 47.3.0）：

```
模组构造                                    → 配置未加载
所有 RegisterEvent（sound_event … worldgen/biome） → 配置未加载
ModConfigEvent.Loading                      → 配置已加载
FMLCommonSetupEvent                         → 配置已加载
```

所以在模组构造阶段用 nightconfig 把**同一个文件**先读一遍
（`FutureTinkerConfig.bootstrap`），之后 Forge 自己加载它；schema 只有 `SPEC` 一份，
早期读取只取值、缺值回落到默认值。两条读取路径必须给出同一个答案，
`ModuleGateTests.gateIsWiredToConfig` 把这件事本身当成断言来查。

验证方法（两次运行对照）：

1. 默认配置跑 `python tools/verify_gametest.py`：日志里
   `Future Tinker 模块开关（注册表填充前，从 futuretinker-common.toml 读到 2 项）: {wolfarmor=true, spear=true}`
   + `模块闸门（注册阶段）: 启用=[wolfarmor, spear] 关闭=[]`，测试通过；
2. 把 `modules.spear.enabled` 改成 `false` 再跑：同一行变成 `{wolfarmor=true, spear=false}`、
   `启用=[wolfarmor] 关闭=[spear]`，测试仍然通过——因为「spear 的标签页不存在」正是它要求的结果。

## 已验证 / 未验证

**已验证（有命令与日志）**

- `gradlew build` 退出码 0；产物 `build/libs/futuretinker-0.1.0.jar` 里 `mods.toml` 的占位符
  已展开、中文描述是合法 UTF-8、`pack.mcmeta` 的 `pack_format=15`。
- `libs/` 里的匠魂/Mantle jar 经 `flatDir` + `fg.deobf` 正确解析，deobf 产物存在。
- `python tools/verify_gametest.py` 通过（3 条必需测试全过，日志有批次计数，退出码 0）。
- `--fault` 时判定失败（`1 required tests failed :( `，gradle 退出码 1，脚本判定「故障被抓住」）。
- 模组加载期崩溃/编译失败时脚本判定「没跑到测试」（退出码 2），不会误读成通过。
- 模块开关端到端：开→标签页存在，关→标签页不存在，两次运行都通过各自的断言。
- **狼的护甲槽生效**（狼铠「护甲变种」方案的前提）：`WolfEquipmentTests.chestSlotGivesWolfArmorValue`
  证明裸狼护甲值为 0、往 `EquipmentSlot.CHEST` 放铁胸甲后护甲值 > 0 且槽位保存住了物品。

**未验证（别当成已验证）**

- 没有启动过客户端（`runClient`），没有实际进游戏看界面/创造标签页的样子。
- 匠魂 API 还没有被真正调用过（M1 不注册匠魂内容）；编译期能否用上具体 API 要到 M2 才知道。
- 匠魂自己的护甲减伤流水线对非玩家是否生效——要等有了真的可改造狼铠才能测（PLAN 里写了判据）。
- 狼铠的世界内渲染、死亡掉落策略、右键穿戴交互都还没做。
- 没有在真实整合包环境（几十个模组、Tinkers' Tools / Dreamtinker 共存）里跑过。
- 没有验证「关掉模块后存档/配方不残留引用」——M1 没有内容，这条要到 M2 才有意义。

## 后续（M2 起）

部件注册、装配/修复流程、内衬事件逻辑、长矛三件套机制——见 PLAN.md 的里程碑表。
新增模块时必须同时加配置开关，否则 `ModuleRegistry.validateIds()` 会在启动时直接失败。
