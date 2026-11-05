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
│   ├── wolfarmor/            # 模块一：狼铠
│   │   ├── WolfArmorModule.java      # 注册物品 + 接事件 + 客户端入口（DistExecutor）
│   │   ├── WolfArmorContent.java     # ModifiableArmorMaterial + 物品注册
│   │   ├── WolfArmorItem.java        # 护甲变种（canEquip 只允许狼）
│   │   ├── WolfArmorInteraction.java # 右键穿 / 剪刀脱
│   │   ├── WolfArmorCombat.java      # 用耐久顶掉护甲减免后的伤害
│   │   ├── WolfArmorDrops.java       # 死亡掉一份
│   │   ├── WolfArmorClient.java      # 图标按材料上色（模组总线）+ 挂渲染层
│   │   └── WolfArmorLayer.java       # 狼身上的铠甲渲染（复用狼模型）
│   ├── spear/SpearModule.java          # 模块二（同上）
│   └── gametest/                       # 判定：闸门/槽位/穿脱/耐久/减伤/掉落/定义部件
├── src/main/resources/
│   ├── META-INF/mods.toml    # 硬依赖 forge / minecraft / tconstruct / mantle
│   ├── pack.mcmeta
│   ├── assets/futuretinker/  # 物品模型/贴图、实体铠甲贴图、语言、书页
│   └── data/futuretinker/    # 护甲定义、站台布局、装配配方、标签
├── art/                      # 贴图与实体图集的生成脚本（mc-art 引擎 + 记录）
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

## 狼铠（模块一）的形状

一句定义：**它是护甲的变种**，不是新工具。物品是匠魂的 `ModifiableArmorItem`，穿在狼的
`EquipmentSlot.CHEST` 上（`WolfEquipmentTests` 证明狼的这个槽位真的提供护甲值），
数据是一份普通的护甲定义 JSON。

| 行为 | 在哪 | 判据 |
| --- | --- | --- |
| 4 个部件拼装 | `tool_definitions/wolf_armor_chestplate.json` | `WolfArmorDataTests` 断言部件数 4、其中 2 块是 `tconstruct:large_plate` |
| 部件/铸造 | 复用匠魂 `chestplate_plating` / `maille` / `large_plate`，不自制 | 不自制就没有「插件部件缺 `<贴图>_<材料>` 变体」的紫黑问题 |
| 护甲值 | 胸甲板材 × 2（`multiply_stats armor 2.0`） | 铁 = 10 点；原版 11 点是「只有一种材质」的固定值，这里按材料换算 |
| 站台布局 | `station_layouts/wolf_armor.json`（4 个格子，顺序与部件一致） | `ToolBuildingRecipe.matches` 是**按下标**比对部件，顺序写错就配不上 |
| 在哪做 | 匠魂砧（`ToolBuildingRecipe.requiresAnvil()`：部件 ≥ 4 就要砧） | 2 块大板的「贵」在这里兑现 |
| 右键穿 / 剪刀脱 | `WolfArmorInteraction`（`EntityInteract` + `EntityInteractSpecific`） | 狼的 `mobInteract` 会先吃掉右键（坐下），所以必须抢占并取消事件 |
| 不会穿到玩家身上 | `WolfArmorItem.canEquip` 只对 `Wolf` 返回 true | `WolfArmorInteractionTests` 断言玩家装备失败 |
| 挡伤害 | `WolfArmorCombat`：`LivingDamageEvent` 里按 1:1 扣耐久、`setAmount(damage - absorbed)` | 跳过 `BYPASSES_ARMOR` 与 `WITCH_RESISTANT_TO`；耐久不够只吃剩下的 |
| 死亡掉落 | `WolfArmorDrops`：恒定 1 份 | 实测默认掉落为 0（狼不是玩家/盔甲架，装备直接消失） |
| 图标颜色 | `WolfArmorClient`：`RegisterColorHandlersEvent.Item`（**模组总线**） | 取 `MaterialRenderInfo#vertexColor()`，与匠魂部件同一套色 |
| 世界内渲染 | `WolfArmorLayer` + `WolfArmorModel`：狼骨骼**逐箱放大**一圈 + 实体图集 | 背/肚子/腿内外侧都包住；腿/尾巴/坐下/幼狼缩放由同一组动画参数复现 |

**渲染为什么要「再画一遍狼」**：1.20.1 原版没有狼铠模型可抄，自建模型的骨骼要和
`WolfModel` 逐帧同步（腿、尾巴、坐下、幼狼缩放），错一帧就是穿模。做法是把狼的骨骼抄一份、
每个箱子用 `CubeDeformation` 向外长 0.4～0.75 像素，再把**同一组动画参数**喂给它
（`prepareMobModel` + `setupAnim`），姿态自动一致；「哪里露肉」交给贴图透明像素（头部全透明）。
图集由 `art/wolf_armor_atlas.py` 按原版 `WolfModel` 的 UV 矩形生成，`mc-art` 的
`entity --atlas` 判过「每个不透明像素都落在某个 box 矩形内」。

**为什么不能直接 `poseStack.scale` 放大**：缩放绕一个点做。绕脚底放大时，模型 y 轴朝下
（`LivingEntityRenderer` 做了 `scale(-1,-1,1)`），上半身会往世界里侧缩，背就露出来；
绕躯干中心放大时，两条腿的**内侧面朝远离身体的方向**移动，腿内侧照样露狼皮。
实测第一版（`scale(1.01)`）就是这个症状：狼背和腿内侧的毛从头盔边缘露出来。
逐箱 inflate 才是「每个面都向外长」，一个方向都不漏。

## 长矛（模块二）

复刻高版本原版长矛（1.21.11）的战斗机制。参照的数值来自 wiki 的长矛页面与 25w41a~rc1 的更新日志，
代码里每个常数旁边都写了出处（`SpearCharge` 顶部注释）。

**物品**：`futuretinker:spear`（名字就叫「矛」），匠魂工具。部件是 **矛头（本模组新增）+ 坚韧绑定结 + 坚韧手柄**：

| 部件 | 来源 | 统计 | 拿到的方式 |
| --- | --- | --- | --- |
| `futuretinker:spear_head` | 自制（`ToolPartItem` + `HeadMaterialStats`） | head（耐久/攻击力 = 矛的伤害与「等级」） | 部件台空白模板 + 材料；或三种铸模（金/沙子/红沙）灌出来 |
| `tconstruct:tough_handle` | 匠魂 | handle（手柄的耐久修正 = 疲劳窗口） | 匠魂既有流程 |
| `tconstruct:tough_binding` | 匠魂 | binding（无统计） | 匠魂既有流程 |

**矛头的三种铸模**：`futuretinker:spear_head_cast`（金，可反复用）/ `_sand_cast` / `_red_sand_cast`，
配方各三种（金铸造台、沙子压模、沙子模板）都在 `data/futuretinker/recipes/smeltery/casts/`。

**图标**（全部 `mc-art` 渲染，plan 由 `art/spear_masks.py` 生成，形状是「圆锥的二维投影」）：

- 矛头**物品**图标：实心圆锥（做出来的东西是实心的）；
- 部件台的 pattern 按钮、以及三种铸模：**中间透明**——铸模是「整块料挖出矛头形状的洞」，
  pattern 按钮是 1px 轮廓环（这两处按 GUI/铸模的规矩来）；
- 矛本体：**复用原版 1.21.11 的矛图标**（`art/refs_vanilla_spear.png`，你给的那张）——
  `art/reuse_vanilla_icon.py` 抠黑底、沿矛轴切成三段（尾包口 / 木杆 / 圆锥头）、各自去饱和，
  作为三层贴图；游戏里三层 tintindex 0/1/2 分别按「矛头 / 手柄 / 绑定结」三个材料上色。
  所以**既是原版的形状，又能按材料变色**（父模型 `item/handheld`）。

**姿势与动画**（1.20.1 没有物品动画接口，走 mixin）：

- 冲锋**不进原版的「使用物品」状态**：`isUsingItem()` 会让 `LocalPlayer.aiStep` 把移动输入
  乘 0.2 并禁止疾跑——原版长矛冲锋要能边跑边压，所以右键只发一个「开始冲锋」包，
  松手发「结束冲锋」，计时在服务端走。顺带也就不会套用三叉戟那套压低动画。
- `ItemInHandRendererMixin`（`futuretinker.mixins.json`，`mods.toml` 的 `[[mixins]]`）：
  注入 `ItemInHandRenderer#renderArmWithItem` 的 HEAD，只接管【拿着矛且正在压低或挥击】的情况，
  其余原样交回原版。压低 = 随时间向前向下压 + 绕 X 旋转，戳刺 = 挥击进度驱动的向前一顶。
  注入目标用 SRG 名（`m_109371_`），mixin 配置 `required=false`，注入失败不会拖垮客户端。

**百科条目**：匠魂百科的工具页是**按标签自动生成**的（`tconstruct:load_tools` + 语言文件），
不是手写页面清单。所以矛进了 `tconstruct:modifiable/small`，文案在
`assets/tconstruct/book/encyclopedia/{en_us,zh_cn}/tools/small/futuretinker_spear.json`。
（之前狼铠手写的那份页面之所以不出现，就是因为它没走这条机制。）

**创造模式**：所有模块共用一个标签页「未来匠魂」（`futuretinker:main`）。标签页由模块表在
物品注册完之后统一建，模块只把自己启用的物品交上去——关掉一个模块，标签页里就没有它的东西；
全关掉时标签页本身也不注册。

**工匠砧里的样例**：站台布局的 `icon` 改成用具体材料（铁/木/铁）显示的同一件矛，
不再用 `ui_render#*` 占位材料（那样会显示成一块白图，和旁边的匠魂工具格格不入）。

| 行为 | 实现在哪 | 判据（GameTest） |
| --- | --- | --- |
| 触及 4.5 格 | `SpearItem` 给主手加 `forge:entity_reach` +1.5 | `reachBonusIsOnMainHand` |
| 2 格内戳不到 | `SpearAttack.onAttack` 看距离 | `jabNeedsFullChargeAndMinRange` |
| 必须满蓄力（不能提前攻击） | 同上，`getAttackStrengthScale < 0.999` 就取消 | 同上 |
| 不会暴击 | `SpearAttack.onCritical` → `CriticalHitEvent` 设 `DENY` | `criticalHitIsDenied` |
| 不挖方块（对块也是戳刺） | `SpearAttack.onLeftClickBlock` 把 `useBlock/useItem` 设 `DENY` | `spearDoesNotMineBlocks` |
| 一次戳多个目标 | `SpearAttack.sweep`：眼睛射线扫所有候选，主目标交给原版、其余补一发 | `sweepHitsEveryTargetInFront` |
| 0.125 碰撞箱放大 | `sweep` 用 `box.inflate(0.125)` 做射线求交；左键空气时客户端发 swing 包让服务端也扫 | `hitboxMarginMakesNearMissesHit` |
| 按住右键压低矛身 | `SpearItem.use` + `UseAnim.SPEAR`（+压低音效） | 客户端观感，未验证 |
| 冲锋伤害 = 1 + 倍率 × 相对速度 | `SpearCharge.damage` / `tick` | `damageAndSpeedGatesMatchVanilla` |
| 相对速度 ≥ 4.6 才结算 | `SpearCharge.canDamage` | 同上 |
| 自身速度 ≥ 5.1 才击退 | `SpearCharge.canKnockback` | 同上 |
| 一次冲锋命中多个目标 | `tick` 扫描前方锥体 | `chargeHitsEveryTargetInFront` |
| 高速冲锋挑落骑乘 | `tick`：engaged 阶段 + 达到挑落阈值才 `stopRiding` | `chargeHitsFastTargetsAndDismounts` |
| 三阶段疲劳（engaged → tired → disengaged） | `SpearCharge.stageAt` | `stagesWalkInOrder` |
| 材料决定数值 | 矛头：倍率/挑落阈值/激活延迟；手柄：疲劳窗口长度 | `handleMaterialChangesFatigueWindow` |

**数值怎么由材料算出来**（原版是按材质等级写的，这里换成匠魂材料）：

- 矛头「等级」由矛头耐久分档（<100 / <200 / <600 / <1600 / 其余 = 0/1/2/3/4，正好对应原版木/石/铁/钻石/下界合金）；
- 倍率 = `0.65 + 0.1×等级 + 0.03×矛头攻击力`，夹在 0.7~1.2（原版就是 0.7~1.2）；
- 挑落速度 = `14 − 等级`，夹在 9~14（原版正好是 14 到 9）；
- 疲劳窗口倍率 = `1 + 手柄材料的 durability 修正`（匠魂手柄是 -0.35~+0.35 的比例），夹在 0.75~1.5；
  女王史莱姆手柄窗口最长，琥珀金最短。

**音效**：压低时 `PLAYER_ATTACK_SWEEP`（0.7 音高），冲锋命中 `TRIDENT_HIT`。
原版 `item.spear.*` 这套音效在 1.20.1 不存在，只能用最接近的替代；也没有音频素材可以自制。

**没做的**（别当成做了）：**三阶段的持矛姿势差异**。1.20.1 的 item 系统只有一档
`UseAnim.SPEAR`（压低）姿态，没有按阶段换姿态的动画/模型接口；硬做只能靠换模型文件假装，
在客户端上不可验证，所以没做——阶段的差别目前体现在**机制**（tired 不再挑落、disengaged 不再击退）
和命中音效上。

## 已验证 / 未验证

**已验证（有命令与日志）**

- `gradlew build` 退出码 0；产物 `build/libs/futuretinker-0.1.0.jar` 里 `mods.toml` 的占位符
  已展开、中文描述是合法 UTF-8、`pack.mcmeta` 的 `pack_format=15`。
- `libs/` 里的匠魂/Mantle jar 经 `flatDir` + `fg.deobf` 正确解析，deobf 产物存在。
- `python tools/verify_gametest.py` 通过：**32 条必需测试全过**，日志有批次计数，退出码 0。
- `python tools/verify_gametest.py --fault` 下判定失败（`1 required tests failed :(`，
  gradle 退出码 1，脚本判「注入的故障被抓住」），证明这套检查会说不。
- 模组加载期崩溃/编译失败时脚本判定「没跑到测试」（退出码 2），不会误读成通过。
- 模块开关端到端：开→标签页存在，关→标签页不存在，两次运行都通过各自的断言。
- **狼的护甲槽生效**：`WolfEquipmentTests.chestSlotGivesWolfArmorValue` 证明裸狼护甲值 0、
  放入铁胸甲后 > 0 且槽位保存住了物品。
- **狼铠的护甲值随材料**：`WolfArmorDurabilityTests` / `WolfArmorEquipTests` 用铁材料造出的
  狼铠，护甲值断言通过（胸甲 5 × 2 = 10）。
- **部件数真的是 4（含 2 块大板）**：`WolfArmorDataTests.wolfArmorRequiresTwoLargePlates`
  读运行期的护甲定义，断言 4 个部件、其中 2 块 `tconstruct:large_plate`。
  这条会失败的原因写进断言消息里：少写一块大板不会报错，只是变便宜。
- **受击掉匠魂耐久**：`WolfArmorDurabilityTests` 打 8 点伤害后 `Damage` 值上升。
- **死亡必掉一份**：`WolfArmorDropTests`。
- **右键穿 / 剪刀脱 / 野生狼也能穿**：`WolfArmorInteractionTests`（走事件路径，不是直接写槽位）。
- **长矛的部件/配方**：`SpearDataTests` 读运行期定义，断言 4 个部件**顺序**是
  `futuretinker:spear_head / tconstruct:tough_handle / tconstruct:tough_binding`
  （顺序决定材料下标与模型 index）。
- **长矛的戳刺规矩**：`SpearAttackTests` 六条——触及加在 `forge:entity_reach`、
  不满蓄力/2 格内会被取消、`CriticalHitEvent` 被设成 `DENY`、拿矛左键方块不挖、
  一次扫到两个目标、擦边 0.06 格靠 0.125 碰撞箱放大打中（并先断言「不放大就打不中」）。
- **长矛的冲锋**：`SpearChargeTests` 六条——伤害公式与两个速度门槛、三阶段顺序、
  手柄材料改变疲劳窗口、12.5 格/秒冲锋造成伤害并挑落骑手、前方两个目标同时被命中、
  原地不动打不出伤害。冲锋测试把战场抬到 40 格高空，避免隔壁结构的实体进入扫描范围
  （GameTest 的结构是紧挨着排的，这条踩过）。
- **三种铸模真的注册了**：`SpearDataTests.castsAreRegistered`（三个 `PartCastItem`），
  外加 9 个配方 id 的加载断言（装配、部件台、金铸模、沙子/红沙的压模与两种模板）。
- **百科里有矛**：`SpearDataTests.spearIsListedInTheEncyclopedia` 断言矛在
  `tconstruct:modifiable/small` 里（这是百科生成工具页的机制），且文案文件存在。
- **只有一个标签页，且跟着开关走**：`ModuleGateTests.gateIsWiredToConfig` 现在断言
  「共用的 `futuretinker:main` 有模块启用时存在、全关时不存在」，并逐个模块断言它的物品
  在关掉时一个都不在注册表里。

**未验证（别当成已验证）**

- **没有启动过客户端**：狼铠的颜色/世界内渲染、长矛的持矛姿势与模型、矛头图标的上色与
  部件台里那个 pattern 按钮，都只核过代码路径（事件总线、图集矩形、`mc-art ingame` 渲染），
  **没有在真客户端里看过一眼**。
- 没有在真实整合包环境（几十个模组、Tinkers' Tools / Dreamtinker 共存）里跑过。
- 匠魂砧/部件台的界面观感（格子位置、pattern 按钮是否出现在列表里）没看过。
- 长矛与矛头的模型/贴图都是「借匠魂 javelin 的贴图 + 自制灰度图标」；材质变体与染色只有实机能证明。
- 没有验证「关掉模块后存档/配方不残留引用」。

## 后续（M4 起）

长矛的机制已按原版接完（M4/M5 的核心），剩下的只有「三阶段的持矛姿势」这一项
（1.20.1 没有对应动画接口）和实机观感——见 PLAN.md 的里程碑表。
新增模块时必须同时加配置开关，否则 `ModuleRegistry.validateIds()` 会在启动时直接失败。
