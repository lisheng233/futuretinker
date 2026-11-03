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
- 结果：`All 4 required tests passed :)`，退出码 0。

结论：`Mob` 的护甲槽可以被狼使用，护甲属性对非玩家生效，狼铠可以做成真正的护甲。

### 待实测（M2 里做，别提前当成已知）

匠魂自己的减伤流水线是否对非玩家生效：`EquipmentContext` 收 `LivingEntity`，
但监听伤害的入口是不是 Player-gated，要等有了真的「可改造护甲」才能测。
若不生效，就在 `LivingHurtEvent` 里自己构造
`EquipmentContext.withTool(wolf, toolStack, EquipmentSlot.CHEST)` 调 `ProtectionModifierHook`。
**判据**：同一种伤害打在裸狼 / 穿匠魂狼铠的狼身上，比较最终伤害数值。

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
| M2   | 狼铠部件注册、装配/修复流程          | 狼铠可制作可使用             | 未开始 |
| M3   | 狼铠内衬效果与事件逻辑               | 三种以上材料玩法差异可验证   | 未开始 |
| M4   | 长矛注册与基础攻击                   | 长矛可制作，基础伤害正常     | 未开始 |
| M5   | 冲锋机制三件套（速度伤害/击落/疲劳） | 核心差异化机制完成           | 未开始 |
| M6   | 平衡调整、配置细化、文档             | 可发布版本                   | 未开始 |

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

## 风险与注意事项

- **与 Extended Tinker 的狼铠区分**：对方当前面向犬形金属傀儡，本模组面向原版狼。
- **长矛共存**：注册名加 `futuretinker:` 前缀，避免与 Tinkers’ Tools、Dreamtinker 冲突。
- **Minecraft/匠魂版本锁定**：开工前确定目标版本组合，高版本原版长矛与狼铠的 API 差异需提前确认。
  （已锁定为 1.20.1 / Forge 47.3.0 / TConstruct 3.12.1.236。）
