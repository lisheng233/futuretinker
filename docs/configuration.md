# 配置说明

配置文件：`config/futuretinker-common.toml`（Forge COMMON 配置）。
模块开关需要重启；数值配置在配置加载后读取，改完建议重新装备一次工具/重进世界让属性重算。

## 模块开关

```toml
[modules.wolfarmor]
enabled = true

[modules.spear]
enabled = true

[modules.mace]
enabled = true
```

关掉的模块**不注册任何内容**（不是数值为 0）；开关在注册表填充前读取，热重载不会补注册。

## 狼铠基值

```toml
[wolfarmor]
armor_multiplier = 2.0       # 总护甲倍率；默认与 wolf_armor_chestplate.json 的 multiply_stats 对齐
toughness_multiplier = 1.0   # 总韧性倍率
armor_bonus = 0.0            # 平加护甲
toughness_bonus = 0.0        # 平加韧性
damage_absorption = 1.0      # 受击时由狼铠耐久吸收的比例（1.0 = 原版 1:1，0 = 不吸收）
```

材料本身仍然决定基础护甲/韧性/耐久；这几个值是叠在材料统计之上的公共修正。

## 长矛基值

```toml
[spear]
base_attack_damage = 1.0             # 对应 spear.json 的 base_stats
base_attack_speed = 1.0
reach_bonus = 1.5                    # 原版近战 3.0，长矛默认 +1.5 = 4.5
charge_damage_scale = 1.0            # 冲锋伤害的额外全局倍率（叠在材料倍率上）
handle_fatigue_bonus_ticks = 10.0    # 手柄耐久修正换算出的窗口 tick 常数
```

`base_attack_damage`/`base_attack_speed` 默认值与工具定义 JSON 对齐；改配置即可，
不需要改 datapack。若同时改了 JSON 的 base_stats，配置的差额会相对内置默认值再叠一层。

## 狼牙棒基值

```toml
[mace]
base_attack_damage = 3.0             # 对应 mace.json 的 base_stats
base_attack_speed = 0.9
bleed_duration = 200                 # 钩扯流血持续时间（tick，20~72000）
bleed_move_speed = 0.05              # 目标水平速度达到多少格/秒才触发出血
hard_damage_multiplier = 1.5         # HARD 形态额外魔法伤害 = 硬度 × 此值
handle_weight_penalty = 0.08         # 重手柄最大移速惩罚（0.08 = -8%）
```

## 棒头材料覆盖（整合包作者）

```toml
[mace.material_stats]
# 格式："material_id=hardness,toughness"
# 可以给本模组没有内置的材料补值；同一 id 重复出现时最后一个生效。
overrides = [
  "tconstruct:nahuatl=4.0,2.5",
  "modid:my_material=6.5,1.5"
]
```

优先级（从高到低）：

1. `[mace.material_stats].overrides`（本表）
2. 材料自己的 `futuretinker:hardness` / `futuretinker:toughness` 数据
3. 内置经典材料表（铁/玛玉灵/钴/黏钢等）
4. 按头材料的耐久/攻击/挖掘等级推导（所有可做棒头的材料都有值，不再全部 1.0）

覆盖值同时影响部件 tooltip、狼牙棒本体 tooltip、形态（HARD/TOUGH/LIGHT/NEUTRAL）
以及横扫/攻速/穿甲；无效条目会跳过并在日志里告警，数值夹在 0.01~100。
`material_id` 写基础材料 id 即可（带 `#variant` 的完整 id 也支持，先匹配完整 id）。
