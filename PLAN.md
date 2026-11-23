# Future Tinker 开发计划

## 项目定位

**Future Tinker** 是一个匠魂附属模组，核心目标是把高版本原版 Minecraft 中新增的装备与机制，用匠魂的材料、部件和强化系统重新实现。

第一批内容：**匠魂化狼铠**（从零定义）与**匠魂化长矛**（机制深化）。

## 设计原则

1. **不做数值搬运**：每件原版装备匠魂化后，必须引入至少一条原版没有的配装决策。
2. **模块化**：每个内容模块可独立启用/禁用，方便整合包作者按需配置。
3. **材料决定玩法**：新内容的强度差异主要来自材料选择，而非强化堆叠。
4. **先验证管线，再深化机制**：用实现门槛低的内容跑通部件注册、NBT、事件监听管线，再啃复杂判定。

## 模块一：狼铠（优先开发）

### 目标

给原版狼提供一套匠魂式部件装备，使其成为有配装逻辑的宠物装备，而非单纯加护甲值的胸甲。

**定位澄清（2026-11 修订）**：狼铠是**护甲的变种**，走匠魂的护甲线，不是工具/武器线。
它不是 `ModifiableToolItem`，也不该有近战/挖掘那套 tool stats。

### 为什么可以做成「护甲」（两个已核实的前提）

1. **狼在 1.20.1 有护甲槽。** `LivingEntity.getArmorSlots()` 是抽象方法，`Mob` 实现了它
   （`head/chest/legs/feet` 四个 `ItemStack`，`getItemBySlot`/`setItemSlot` 可用，
   `Mob` 的存档自带 `ArmorItems`）。原版没给狼用，不代表不存在。
   把铠甲 `setItemSlot(EquipmentSlot.CHEST, stack)` 进狼身，护甲值/韧性会走
   `Attributes.ARMOR`/`ARMOR_TOUGHNESS`——装备属性对任何 `LivingEntity` 生效，不只是玩家。
2. **匠魂的护甲机器是 `LivingEntity` 泛化的。** `EquipmentContext.withTool(LivingEntity, IToolStackView, EquipmentSlot)`
   收的是 `LivingEntity`，`hasModifiableArmor()` 通过 `getItemBySlot` 遍历护甲槽，
   所以狼身上那件铠甲能被同一套 hook 看见。

### 技术基底（已核实 3.12.1.236 的类与数据形状）

| 环节 | 匠魂现成的东西 | 我们要产出 |
| --- | --- | --- |
| 物品 | `library/tools/item/armor/ModifiableArmorItem`（`extends ArmorItem implements IModifiableDisplay`，构造需要 `ArmorMaterial` + `ArmorItem.Type` + `ToolDefinition`） | `WolfArmorItem extends ModifiableArmorItem`（或薄封装） |
| 材质映射 | `library/tools/definition/ModifiableArmorMaterial.create(ResourceLocation, SoundEvent, ArmorItem.Type...)`：每个槽位映射到一个 `ToolDefinition` | 一个 `ModifiableArmorMaterial` 实例 |
| 数据定义 | `data/<ns>/tinkering/tool_definitions/<id>.json`，模块：`part_stats`（部件列表 + `primary_part`）、`default_materials`、`multiply_stats`、`modifier_slots`（`defense`/`upgrades`）| `wolf_armor.json` |
| 材料强度 | `tools/stats/PlatingMaterialStats`（record：`durability/armor/toughness/...`，按槽位分 `HELMET/CHESTPLATE/LEGGINGS/BOOTS/SHIELD` 五种 `MaterialStatType`） | 复用 `CHESTPLATE` 类型 → **不需要给现有材料补任何新数据** |
| 强化/防御槽 | `modifier_slots` 里的 `defense`/`upgrades`；护甲 hook：`ProtectionModifierHook`、`ModifyDamageModifierHook`、`OnAttackedModifierHook`、`DamageBlockModifierHook`… | 直接声明，不写代码 |
| 装配/修复 | 匠魂装配台按 `ToolDefinition` 的 `part_stats` 出配方；`MaterialRepairModule` 管修复 | 站台布局 + 配方 JSON |
| 参考实现 | 匠魂板甲：`plate_chestplate.json` 的部件是 `tconstruct:chestplate_plating` + `tconstruct:maille` | 照这个形状，部件换成狼铠自己的 |

### 部件设计

匠魂板甲是「主体甲片 + 衬料」两个部件，狼铠按 PLAN 的三部件设计（部件数与 `part_stats`
的叠加权重成正比，材料选择因此能改变定位）：

| 部件     | 决定属性     | 材料影响方向                         | 对应匠魂部件类型 |
| -------- | ------------ | ------------------------------------ | ---------------- |
| 外层甲片 | 护甲值、韧性 | 硬抗型减伤 vs 闪避型减伤             | `wolf_armor_plating`（主部件） |
| 内衬     | 特殊效果     | 吸引仇恨 / 受击反弹 / 低血量速度爆发 | `wolf_armor_lining` |
| 绑定     | 可修复性     | 常规修复 / 狼休息时缓慢自修复        | `wolf_armor_binding` |

### 需要我们自己补的三件事（原版与匠魂都不管）

1. **渲染**：1.20.1 的 `WolfRenderer` 没有护甲层，装上去不会自己显示。要么加一个
   `RenderLayer`（狼铠的模型/贴图要过 `mc-art` 引擎），要么先只做物品栏外观、世界内不显示。
2. **掉落策略**：`Mob.armorDropChances` 默认会让装备在死亡时掉落，匠魂的
   `ModifierEvents.onLivingDrops` 也会遍历装备槽物品。驯服的狼死了铠甲掉了属于设计缺陷，
   要显式处理（保留 / 交给主人 / 掉在死亡点）。
3. **交互**：右键狼穿、潜行右键脱（`Wolf#mobInteract`），以及换部件走装配台。

### 前提验证（已实测通过）

**狼的护甲槽真的生效**——用原版铁胸甲当探针跑了一次 GameTest
（`gametest/WolfEquipmentTests.chestSlotGivesWolfArmorValue`）：

- 空白对照：裸狼 `getArmorValue() == 0`；
- 往 `EquipmentSlot.CHEST` 放进铁胸甲，第 5 tick 断言 `getArmorValue() > 0` 且槽位保存住了物品；
- 结果：5 条必需测试全过，退出码 0。

结论：`Mob` 的护甲槽可以被狼使用，护甲属性对非玩家生效，狼铠可以做成真正的护甲。
后续 M2 第一刀进一步确认：穿匠魂狼铠后护甲值 5、8 点玩家攻击的伤害从 8.0 降到 7.68。

### 仍未实测（别提前当成已知）

**匠魂自己的保护类强化**（`ProtectionModifierHook` 等）是否对非玩家生效。现在狼铠还没有任何
保护强化，护甲值是走原版属性 + 原版减免公式的，所以测不出这一层。等 M3 给狼铠加强化后再测；
若不生效，就在 `LivingHurtEvent` 里自己构造
`EquipmentContext.withTool(wolf, toolStack, EquipmentSlot.CHEST)` 调 hook。
**判据**：同一种伤害打在裸狼 / 穿**带保护强化**的狼铠的狼身上，比较最终伤害数值。

### 技术要点

- 狼铠的物品命名空间隔离（`futuretinker:` 前缀），与其它匠魂附属共存
- 狼的受击、低血量、战斗状态事件监听（内衬效果）
- 组装走匠魂装配台；部件替换与修复走匠魂既有流程，不另造一套

### 验收标准

- 狼铠可正常装配、修复、替换部件（走匠魂站台，不是自定义界面）
- 至少三种内衬材料产生可感知的玩法差异
- 模块可通过配置禁用，禁用后不影响其他模块

## 模块二：长矛（第二批开发）

### 目标

把高版本原版长矛的冲锋机制——相对速度伤害、击落骑乘、持矛疲劳——嫁接到匠魂材料系统中。

### 机制映射

| 匠魂部件 | 影响的机制             |
| -------- | ---------------------- |
| 矛头材料 | 冲锋伤害系数、击落阈值 |
| 手柄材料 | 疲劳出现的时间窗口     |

### 差异化要求

现有匠魂附属（Tinkers‘ Tools、Dreamtinker）的长矛只做了“冲刺加伤”或“长触及”，**未实现相对速度判定、击落骑乘、疲劳阈值**。本模块必须完整实现这三条，且材料选择能明显改变武器定位（长时间追杀的坐骑杀手 vs 爆发高但易疲劳的步兵武器）。

### 技术要点

- 玩家与目标速度向量计算
- 骑乘状态检测与击落判定
- 持矛疲劳计时器与阈值触发
- 与原版长矛机制的兼容性处理

### 验收标准

- 冲锋伤害随相对速度变化可感知
- 高速冲锋可击落骑乘目标
- 持矛超时触发疲劳，手柄材料改变疲劳窗口
- 与现有匠魂附属长矛共存时不冲突（注册名隔离）

## 技术栈决策

- **核心逻辑用 Java**：狼铠的 NBT/事件、长矛的速度向量与疲劳判定，纯 KubeJS 难以稳定实现。
- **模组 ID**：`futuretinker`，包结构按内容模块划分（`futuretinker.wolfarmor`、`futuretinker.spear`）。

## 里程碑

| 阶段 | 内容                                 | 产出                         | 状态 |
| ---- | ------------------------------------ | ---------------------------- | ---- |
| M1   | 项目骨架、模组元数据、配置系统       | 可加载的空模组，模块开关生效 | ✅ 已完成（见下「M1 完成情况」） |
| M2   | 狼铠部件/护甲/穿脱/减伤/耐久/掉落/配方 | 狼铠可制作可使用             | ✅ 已通（GameTest）；只剩「客户端观感」一项没在实机确认 |
| M3   | 狼铠内衬效果与事件逻辑               | 三种以上材料玩法差异可验证   | 未开始 |
| M4   | 长矛注册与基础攻击                   | 长矛可制作，基础伤害正常     | ✅ 已通（触及/最短距离/满蓄力/无暴击） |
| M5   | 冲锋机制三件套（速度伤害/击落/疲劳） | 核心差异化机制完成           | ✅ 已通（伤害公式/速度门槛/三阶段/挑落/多目标） |
| M6   | 平衡调整、配置细化、文档             | 可发布版本                   | 进行中：工具/狼铠基值与棒头材料覆盖配置已落地，见 `docs/configuration.md` |

## M4/M5 进度（长矛）

**已通**（41 条 GameTest 全部通过，其中长矛相关 19 条）：

- 物品是匠魂工具 `futuretinker:spear`（显示名「矛」）；部件是 **矛头（自制）+ `tough_binding` + `tough_handle`**。
  矛头是普通部件物品（`ToolPartItem` + `HeadMaterialStats`），部件台里用空白模板 + 材料做，
  也可以先做一次再灌出**三种铸模**（金/沙子/红沙，`PartCastItem`）。
- **图标**：`mc-art` 渲染，plan 由 `art/spear_masks.py` 从几何生成（圆锥的二维投影）。
  矛头部件与 pattern 按钮**只画轮廓、中间透明**；矛本体是 `item/generated` 三层
  （矛头/矛杆/绑定结），tintindex 0/1/2 分别上三个材料的颜色。
- **不再紫黑**：原先把模型指向 `tconstruct:item/tool/javelin/*` 想蹭匠魂的材料变体，
  但那是给匠魂自己的工具页生成的，插件贴图没有变体 → 手里/地上是紫黑格。改成平铺多层 + 染色。
- **百科**：矛进 `tconstruct:modifiable/small`，百科的「小工具」章节按标签自动生成页面，
  文案在 `assets/tconstruct/book/encyclopedia/{en_us,zh_cn}/tools/small/futuretinker_spear.json`。
- **创造模式**：所有模块共用一个标签页「未来匠魂」（`futuretinker:main`），由模块表统一注册；
  矛头/棒头走 `IMaterialItem.addVariants`，每个可用材料各列一份成品，和匠魂「部件」页一致。
- **戳刺**：主手加 `forge:entity_reach` +1.5（触及 4.5）；2 格内取消；不满蓄力取消；
  `CriticalHitEvent` 设 `DENY` 取消暴击；结算前取消疾跑状态以规避冲刺击退；
  左键方块取消（不挖方块）；`sweep` 用眼睛射线 + `box.inflate(0.125)` 扫多个目标，
  主目标交给原版、其余补一发；左键空气时客户端发 swing 包让服务端也扫（否则放大没意义）。
  第一人称戳刺沿视线滚 75°（渐入渐出）并上抬 0.05，把 1.20.1 的竖直持矛姿势放平。
- **冲锋**：按住右键（自定义开始/结束包，不进原版使用状态，有压低音效）；每 tick 在服务端
  从「眼睛 + 视线×2.0」到「眼睛 + 视线×(4.5，创造 6.5) + 一 tick 位移」投射射线；
  精确命中箱没中时再用放大 0.125 的命中箱并检查到中心的连线（原版
  `ProjectileUtil.getManyEntityHitResult`）；方块从眼睛裁剪，比 2.0 还近就整 tick 取消，
  否则截断射线。速度：玩家用**位置差**（服务端 `deltaMovement` 不可靠），怪物用
  `deltaMovement`（等价 `lastKnownSpeed*20`）。伤害 = `攻击力 + ⌊相对速度 × 倍率⌋`；
  伤害只看相对速度 ≥ 4.6，挑落/击退只看沿视线速度（阈值 8 / 5.1），三者条件互相独立；
  伤害命中本身也击退；射线命中后先登记 10 tick 接触冷却，再判速度/窗口。
- **疲劳**：挑落窗口 → 击退窗口 → 伤害窗口，走完自动停止使用。
- **动作**：`SpearAnimation` 用原版 1.21.11 的计时与 stab 曲线；第一人称压低姿势逐行照抄
  `firstPersonUse`（X 轴 -65° 压平 + **-Y 轴** ±90° 收向准星；第二段写成 Z 轴滚转会一直
  竖直），第一段按 `raise` 再往玩家手侧 +0.08、前方 -0.06 偏移；手部由第一/第三人称 mixin
  显式渲染原版 32×32 `_in_hand` 灰层（不能用手持
  predicate 切模型，GUI 也会传玩家实体）；第三人称手臂由 `HumanoidModelMixin` 抬起，
  其他玩家的冲锋状态走服务端广播；`LivingEntitySwingMixin` 把挥击时长改成 `20 / 攻速`。
  **in-hand 图层保持原版方向**：原版图的头在左上，`spear_in_hand` 的 firstperson display
  变换下左上端正好在准星附近；之前转 180° 是弄反了（三色分层实机截图确认）。
- **铸模/书**：矛头和棒头的金/沙/红沙铸模都走匠魂 `PartCastItem` + casting_table/molding/
  item_part_builder；腔体直接从匠魂三张空白铸模（`blank`）扣出，形状取对应部件图标 alpha
  的原尺寸（居中、不缩放、不膨胀，`art/cast_art.py`）；
  浇筑部件的 `table_casting_material` / `table_casting_composite` 配方在
  `data/futuretinker/recipes/tools/parts/casting/`，三种铸模挂进 `tconstruct:casts/`
  的 `multi_use` / `single_use` / `gold` / `sand` / `red_sand` 标签；铸模**不**加入部件台
  `pattern_item`（部件台只用空白模板）。矛头部件图标在画布中居中，保证铸造台里产物与型腔重合；
  工具定义加 `fixed_material_name`（百科显示“任意 矛/狼牙棒”）。
  狼铠百科页挂到 `tconstruct:modifiable/book_armor` 标签（护甲章读的是这个，不是普通 armor 标签）。
- **换部件**：矛、狼牙棒、狼铠都挂进 `tconstruct:modifiable/multipart`，工匠砧可替换部件；
  狼铠另有 `table_casting_part_swapping` 配方（index 0 = 胸甲板材，6 点材料），按板甲规则
  可在铸造台用熔融材料换板材。`PartSwappingTests` 查标签、配方类型和材料罗列版。
- **材料映射**：矛头耐久分档 0~4 当「等级」（正好对应原版木/石/铁/钻石/下界合金），
  等级决定倍率（0.7~1.2）、挑落阈值（14/10/8/7.5/7 格/秒）与四个数值
  （激活延迟 + 激活后的三个窗口；击退窗口按原版 10.0/9.0/6.75/6.5/5.5 秒 =
  200/180/135/130/110 tick）；
  手柄材料的 `durability` 比例修正（-0.35~+0.35）给三个窗口各加 ±10 tick。
- 判定：41 条必需测试全过；`--fault` 仍被抓到。

## 狼牙棒（模块三，后加）

- 两个部件：棒头（`futuretinker:mace_head`）+ 坚韧手柄；棒头读
  `futuretinker:hardness` / `futuretinker:toughness` 两条材料统计（未声明的材料先查内置
  经典表，再按头材料的耐久/攻击/挖掘等级推导），决定 HARD / TOUGH / LIGHT / NEUTRAL 四种形态。
- **命中必挂流血**；流血期间目标移动时每 10 tick 触发一次魔法伤害，站着不动不掉血。
- 按真实狼牙棒重画：粗椭圆棒身 + 成排狼牙 + 底部斜向箍环 + 尖头；箍环垂直于棒轴，
  手柄沿同一轴接入，逐行按固定宽度光栅化（中线不会中途错位）。`art/mace_wolf_tooth.py`
  生成 32×32 工具两层灰度（棒头/手柄按材料上色）；部件图标/pattern 是同一轮廓的 16×16 版本，
  铸模腔取部件图标 alpha 原尺寸从空白铸模扣出；手持用 `handheld_mace` 的 display 变换。
  横扫角度/伤害系数与手柄移速惩罚由形态/重量决定。
- 棒头的三种铸模走匠魂 `PartCastItem` + 对应 recipe 类型，浇筑配方和标签与矛头同规则；
  `MaceHeadItem`/`MaceItem`
  的 tooltip 显示 `futuretinker:hardness/toughness`（材料数据优先，经典材料走内置表，
  其余材料按头材料统计推导）。铸模腔从部件图标 alpha 生成（矛头/棒头同一规则）。
- 补充 `pattern.futuretinker.mace_head`、铸模物品名、形态 tooltip 等翻译键。
- `MaceTests` 5 条：部件顺序、形态分类、命中必挂、移动才掉血、重手柄移速。

**还缺**：命中反馈的位移（原版 `hitFeedback` 的命中回弹）没有接，常驻为 0；音效仍是
1.20.1 能找到的替代音（原版 `item.spear.*` 不存在）。三阶段姿势、第三人称手臂、戳刺动画
已在 dev 客户端实机确认；真实整合包观感未验证。

## M2 进度（狼铠）

**已通**（每条都有 GameTest 或日志证据）：

- 定位是**护甲的变种**：`ModifiableArmorItem` + `ModifiableArmorMaterial` + 一份护甲定义 JSON，
  穿在狼的 `EquipmentSlot.CHEST`；`canEquip` 只对 `Wolf` 返回 true（这就是「铠甲穿到玩家身上」的根因修法）。
- **部件不复用不了匠魂的东西，就都复用匠魂的**：`chestplate_plating` + `maille` + **两块 `large_plate`**。
  自制部件的紫黑问题（匠魂只为自己的贴图生成 `<贴图>_<材料>` 变体）从构造上消失。
- 自定义部件的代价：`large_plate` 每块 4 点材料，整套材料消耗翻倍；部件数到 4 触发
  `ToolBuildingRecipe.requiresAnvil()`，所以狼铠现在要上**匠魂砧**做——「高防御的代价是贵」。
- 护甲值：`material_stats`（`plating_chestplate` + `maille`）+ `multiply_stats armor 2.0`，
  即**胸甲板材 × 2**（铁 10 点）。原版 11 点是单材质固定值，不适用于多材料。
- 站台布局 `station_layouts/wolf_armor.json` 4 格，顺序与 `tool_parts` 一致
  （`ToolBuildingRecipe.matches` 是按下标比对的，顺序错了静默配不上）。
- 穿脱：右键驯服/野生狼穿甲（抢在狼的 `mobInteract` 之前并取消事件，否则会坐下）、剪刀取下。
- 减伤：`LivingDamageEvent` 里按护甲减免后的最终伤害 1:1 扣狼铠耐久，`setAmount(damage - absorbed)`；
  跳过 `BYPASSES_ARMOR` 与 `WITCH_RESISTANT_TO`；耐久不够只吃剩余。
- 死亡掉落：恒定 1 份（实测默认是 0）。
- 书：百科的护甲/工具两章都有条目（位置只是显示问题，不再改）。
- 图标颜色：灰度遮罩 + `RegisterColorHandlersEvent.Item`（**模组总线**）取
  `MaterialRenderInfo#vertexColor()`，与匠魂部件同一套材料色。
- 世界内渲染：`WolfArmorLayer` 复用狼自己的模型，按实体图集把整只再画一遍
  （头部矩形透明 → 露出真脸；腿/尾/坐下/幼狼缩放自动跟本体）。
- 判定：14 条必需测试全过；`--fault` 仍被抓到。

**还缺**：客户端实机看一眼（颜色、穿在狼身上的观感、砧里 4 格布局）。这三项都能在
GameTest 之外单独确认，但没有客户端就只能是「代码路径 + 图集证据」。修复流程（用材料修）也还没验证。

## M1 完成情况（准备工作）
**交付**：能在 1.20.1 / Forge 47.3.0 上加载的空模组，模块开关真正生效（关掉 = 不注册内容），
以及一套不会被「BUILD SUCCESSFUL」骗过的判定。细节与命令见 [README.md](README.md)。

**目标版本组合（已锁定）**：Minecraft 1.20.1 · Forge 47.3.0 · TConstruct 3.12.1.236 · Mantle 1.11.122 · Java 17。

**已验证**（每条都有命令与日志）：

- `gradlew build` 退出码 0；jar 里 `mods.toml` 占位符已展开、中文描述为合法 UTF-8、`pack_format=15`。
- `python tools/verify_gametest.py` 通过：3 条必需测试全过，日志有 `Running test batch` 与
  `All 3 required tests passed :)`，gradle 退出码 0。
- `python tools/verify_gametest.py --fault` 下判定失败（`1 required tests failed :( `，退出码 1），
  证明这套检查是会说不的。
- 模块开关两次运行对照：`spear=true` → 标签页存在；`spear=false` → 标签页不存在，两次各自通过断言。
- `libs/` 里的匠魂/Mantle 生产版 jar 能被 `flatDir` + `fg.deobf` 正确解析（deobf 产物已确认存在）。

**未验证**：客户端从未启动过；匠魂 API 还没有被真正调用（M1 不注册匠魂内容）；
没有在真实整合包（与 Tinkers' Tools / Dreamtinker 共存）里跑过。

## M1 踩到的三个坑（记下来，后面别再踩）

1. **Forge 1.20.1 的配置在注册表之后才加载。** 实测事件顺序是
   模组构造 → 全部 `RegisterEvent`（配置未加载）→ `ModConfigEvent.Loading`（已加载）→ `FMLCommonSetupEvent`。
   所以在 `RegisterEvent` 里直接读 `ForgeConfigSpec` 的 `ConfigValue` 必然抛异常；
   「关掉的模块不注册内容」只能靠在构造阶段提前读**同一个配置文件**（`FutureTinkerConfig.bootstrap`）。
   （拿不准顺序时不要猜：打一行 `SPEC.isLoaded()` 的日志跑一次，游戏会告诉你。）
2. **模组加载期崩溃时 `runGameTestServer` 也会 `BUILD SUCCESSFUL`、退出码 0。**
   「退出码 = 失败的必需测试数」只在「跑到了测试」时成立。判定必须同时要求日志证据
   （`Running test batch` / `All N required tests passed`），否则会把彻底失败读成通过。
   这也是 `tools/verify_gametest.py` 存在的原因。
3. **`gradle.properties` 被 Gradle 按 ISO-8859-1 读取。** 里面的中文进入 Gradle 时已经变成
   `ç”¨` 这种码点，`processResources` 的 `expand` 之后就成了双编码乱码（jar 里的模组描述会是乱码）。
   含非 ASCII 的项要在 build.gradle 里按 UTF-8 从同一文件重取一次，并给 `processResources` 钉死
   `filteringCharset = 'UTF-8'`。

## M2 踩到的坑

4. **`Item` 的构造函数会向注册表写一个 intrusive holder。** 所以**物品不能在模组构造阶段 new**
   （注册表那时已经冻结，实测抛 `Registry is already frozen`），必须放在 `RegisterEvent` 的
   supplier 里创建。狼铠的物品就是因此改成「字段接住、supplier 里 new」的写法。
5. **`minecraft:generic` 在 `bypasses_armor` 标签里。** 用它测护甲会永远测不出减免
   （第一版测试就得到「护甲值 5 但伤害没变」的错误结论）。测护甲要用玩家/生物攻击类伤害。
6. **5 点护甲对 8 点伤害只减 4%**（原版公式 `clamp(armor - damage/(2+tough/4), armor*0.2, 20)/25`），
   断言阈值不能拍脑袋定成 0.5——那不是「护甲没生效」，是公式本来就这么算。
7. **`mc-art` 引擎需要 Pillow ≥ 12**（用 `Image.get_flattened_data()`；本机 11.3.0 直接报
   `'Image' object has no attribute` ），用项目内 venv 解决，别升级全局 Python 环境。
8. **不要用 Windows PowerShell 的文本重写去改 UTF-8 源码**：`Get-Content -Raw` 会按 GBK 解码、
   `Set-Content -Encoding UTF8` 会加 BOM，一个中文源码文件当场变成 18 个编译错误。改动一律用编辑器工具。

## M2 后半段踩到的坑

9. **`tconstruct:tool_parts` 不能和 `part_stats` 同时用。** 两者都实现 `ToolPartsHook`，而它
   **不支持 merge**，定义加载当场抛 `IllegalStateException: tconstruct:tool_parts does not support merging`，
   整个护甲定义变成空定义（护甲值 0、部件数 0，但游戏不崩）。想要「有统计的部件 + 不贡献统计的部件」，
   正确写法是匠魂 `plate_shield` 那样：`material_stats`（显式 stat_types）+ `tool_parts`（全部部件）。
10. **`RegisterColorHandlersEvent` / `EntityRenderersEvent.AddLayers` 在模组总线上。**
   Forge 用 `ModLoader.get().postEvent(...)` 发它们（见 `net.minecraftforge.client.ColorResolverManager`），
   挂到 `MinecraftForge.EVENT_BUS` 上监听器**一次都不会被调用**，表现就是「改了代码但颜色没变」，
   而且没有任何日志。判据：翻 Forge 源码里的 `postEvent` 调用点，别靠印象。
11. **1.20.1 原版狼渲染器没有护甲层。** 世界内要显示铠甲必须自己 `AddLayers` 挂一层；
   而**整体 `poseStack.scale` 放大是错的**：模型 y 轴朝下（`scale(-1,-1,1)`），绕脚底放大让上半身
    缩进体内（背露出），绕躯干中心放大让腿的内侧面朝*远离*身体的方向移动（腿内侧露出）——
    实机第一版就是这个症状。正确做法是逐箱 `CubeDeformation` 向外长（`WolfArmorModel`），
    动画用同一组参数喂 `prepareMobModel`/`setupAnim` 复现（骨骼字段是私有的，抄不到）。
    图集用 `mc-art` 的 `entity --atlas` 判「每个不透明像素都落在 box 矩形内」，再用 `ingame`
    渲染一遍看，别只看数字。
12. **狼的装备槽在客户端靠装备同步包更新。** 服务端 `setItemSlot` 之后渲染层读
   `wolf.getItemBySlot(EquipmentSlot.CHEST)` 才有东西；这条只在实机可见，GameTest 只能验服务端。

## M4/M5 踩到的坑

13. **GameTest 的结构是紧挨着排的。** 在测试里用 `getEntitiesOfClass(...)` 扫 4.5 格，
   会扫到隔壁结构的实体——真实症状是「伤害打出来了，但断言的那个僵尸没掉血」（打中的是别人的僵尸）。
   冲锋类测试把玩家和靶子抬到结构上方 40 格避开隔壁实体，但那里是石头，
   还要先 `carveTestRoom` 挖出空气房，否则原版的贴脸方块裁剪会把攻击全部挡住。
14. **`AttackEntityEvent` 在 `getAttackStrengthScale()` 读取之前触发**，所以「必须满蓄力」
   可以直接在事件里判断；反过来，取消暴击要用 `CriticalHitEvent.setResult(DENY)`
   （`setDamageModifier(1.0)` 只去掉 1.5 倍率，暴击粒子还在）。
   测试里 `attackStrengthTicker` 是 protected 设不了，改成临时加 `attack_speed` 修饰符把冷却压到 1 tick。
15. **`Tier#getLevel()` 在 Forge 1.20.1 已被标为过时**（要用 `TierSortingRegistry`）。
   长矛的「矛头等级」改成按矛头耐久分档：对原版五种材质还原出同样的 0~4，也不必碰过时 API。
16. **匠魂工具模型可以引用匠魂自己的贴图。** 长矛的模型直接指 `tconstruct:item/tool/javelin/*`，
   材料变体（`<贴图>_<材料>`）由匠魂生成，所以没有自制部件那条紫黑路；
   但这也意味着形状是「标枪」——要真正像矛，得等有自己的一套贴图并确认变体生成路径。
17. **自制部件不要用匠魂的 `tconstruct:material` 模型。** 那个 loader 会去找 `<贴图>_<材料>`
   变体，而变体是匠魂给**自己的**贴图预生成的，插件自加的贴图没有 → 紫黑。
   正确做法：部件物品用 `item/generated` + 灰度遮罩，颜色交给
   `RegisterColorHandlersEvent.Item`（模组总线）按材料染。
   部件本身只要一个 `tconstruct:part_builder` 配方（`pattern` + 空白模板 tag）就会出现在
   部件台里——部件台是**按钮列表**（`PartBuilderBlockEntity` 的 `Map<Pattern, Recipe>`），
   不是按 pattern NBT 匹配，所以新部件不需要新的 pattern 物品。
18. **工具本体同理，别蹭匠魂的工具贴图。** 把模型指向 `tconstruct:item/tool/javelin/*`
   看起来能复用材料变体，但那些变体是给匠魂自己的模型生成的，插件指向的路径没有 → 手里/地上紫黑。
   改用 `item/generated` 多层：每层一张灰度贴图，`ItemModelGenerator` 给的 tintindex 就是层号，
   于是能把「矛头 / 矛杆 / 绑定结」三层分别染成三个材料的颜色。
19. **匠魂百科的工具页是按标签自动生成的**，不是手写页面清单：section JSON 里有
   `"extraData": {"tconstruct:load_tools": {"tag": "tconstruct:modifiable/small", "path": "tools/small"}}`，
   页面从标签里的每个物品生成，文案从 `book/encyclopedia/<lang>/tools/small/<ns>_<path>.json` 读。
   所以「书里没有某个工具」的修法是把物品加进对应的 `tconstruct:modifiable/*` 标签 + 写文案，
   手写一份页面 JSON 放在目录里不会出现在书上（狼铠那份就是这么失效的）。
22. **1.20.1 没有物品动画接口。** 物品「使用动画」只有 `UseAnim` 那几个硬编码分支，
   模型的 `display` 变换只能整体挪，做不出压低的过程；攻击挥击也插不进前刺。
   要真正的动画只能 mixin 客户端渲染：本项目注册了 `futuretinker.mixins.json`
   （`mods.toml` 的 `[[mixins]]`），`ItemInHandRendererMixin` 注入 `renderArmWithItem`，
   只接管拿着矛且压低/挥击的帧。`required=false`，注入失败顶多没动画，不会让客户端起不来。
23. **Mixin 源码写 SRG 名、又没有 refmap = dev 客户端静默不注入。** 生产 jar 的类名是 SRG，
   写 SRG 看起来能跑；dev 环境是 official 名，没有 refmap 时注入目标找不到，而 `required=false`
   会让失败悄无声息（表现：改了动画、实机一点没变）。正确做法：源码写 official 名，
   `build.gradle` 引入 MixinGradle 插件 + `annotationProcessor 'org.spongepowered:mixin:0.8.5:processor'`，
   用 `mixin { add sourceSets.main, 'futuretinker.refmap.json' }` 生成 refmap；
   产物 jar 里必须能 `unzip -l` 看到 `futuretinker.refmap.json`。
24. **1.20.1 的 `applyItemArmTransform` 比原版 1.21.11 多减 `equipProgress*0.6`。**
   照抄 1.21.11 的持矛动画时不能直接复用它，否则整套动作压低 0.6 格（矛掉出屏幕）；
   原版的基准是 `(side*0.56, -0.52, -0.72)`。
25. **in-hand 贴图保持原版方向（矛头左上）。** 用「头/杆/尾三色分层」实机截图确认：
   `spear_in_hand` 的 firstperson display 变换把左上端放在准星附近；转 180° 反而会让玩家
   在第一人称看到尾包。不要凭「远端/近端」直觉翻转。
26. **firstPersonUse 的第二段是绕 -Y（`Axis.YN`），不是 Z 轴滚转。** 反编译里 `a.c` 对应
   `Axis.YN`；写成 `Axis.ZP` 会让矛在右键全程竖直、矛头始终朝上。正确组合是
   `XP(-65°·easeInOutBack(raise))` 后用 `YN(side·(...))` 把矛头转向准星右前方，两项都带
   原版支点；命中反馈的下压（`hitFeedbackAmount`）没有对应状态可省略。
27. **服务端玩家的 `getDeltaMovement()` 不能用来算冲锋速度。** 玩家移动走位置包，服务端
   `deltaMovement` 常常是 0——这正是「冲刺时老打不到」的根因；玩家按位置差
   （等价于 `lastKnownSpeed * 20`）算，怪物则可以直接用 `deltaMovement`（更接近原版）。
   目标第一次进入射线时位置差还没有样本，会先按 0 算；不要因此把整段逻辑改成只看玩家速度。
28. **GameTest 结构上方 40 格是石头，不是空气。** 原版方块裁剪从眼睛开始，贴脸的石头会
   正确地把整次攻击判成「被墙挡住」；不要在生产代码里加「忽略 <0.5 方块」的特判。
   测试里用 `carveTestRoom` 先挖出空气房，再验证射线。
29. **百科护甲章读 `tconstruct:modifiable/book_armor`，不是 `modifiable/armor`。**
   工具章读 `modifiable/small`/`broad`；护甲只进普通 armor 标签不会生成百科页。
30. **自定义 MaterialStats 不会自动出现在 tooltip。** 部件/工具要自己 `appendHoverText`
   把 `futuretinker:hardness/toughness` 写出来；内置经典材料表是代码 fallback，UI 看不到。
31. **本模组自定义部件也要用匠魂的 `PartCastItem` 和 `casting_table`/`molding_table`/
   `item_part_builder` recipe 类型**，不要另造铸模 item/recipe 体系；矛头和棒头都照此注册。
32. **客户端代码别在构造阶段碰物品。** `DistExecutor.unsafeRunWhenOn(CLIENT, ...)` 是在模组构造函数里跑的，
   那时物品还没注册；例如 `ItemProperties.register(SpearContent.spear(), ...)` 会 NPE，整个模组
   构造失败（客户端实测：`Failed to complete lifecycle event CONSTRUCT`）。
   物品 predicate 要在 `FMLClientSetupEvent`（`event.enqueueWork`）里登记。
   **这条 GameTestServer 抓不到**：服务端不加载客户端类，所以那套判定对这类「客户端起不来」完全失明，
   只有真的启动一次客户端才看得见。
20. **一个标签页 + 多个可独立开关的模块**：标签页不属于任何模块，由模块表在物品注册完之后统一建；
   模块只在被启用时把自己的物品登记进去。判定同时查两件事：共用标签页的存在性跟着「有没有模块启用」，
   每个模块的物品存在性跟着自己的开关走。

## 风险与注意事项

- **与 Extended Tinker 的狼铠区分**：对方当前面向犬形金属傀儡，本模组面向原版狼。
- **长矛共存**：注册名加 `futuretinker:` 前缀，避免与 Tinkers’ Tools、Dreamtinker 冲突。
- **Minecraft/匠魂版本锁定**：开工前确定目标版本组合，高版本原版长矛与狼铠的 API 差异需提前确认。
  （已锁定为 1.20.1 / Forge 47.3.0 / TConstruct 3.12.1.236。）
