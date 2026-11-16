# 长矛冲锋：与 Minecraft 1.21.11 的判定对照

日期：2026-11-15
范围：右键冲锋的**攻击范围、距离、射线/命中箱、方块遮挡、速度门槛、接触冷却、三个效果窗口与结算顺序**。
参考：`.art-work/vanilla-1.21.11-client.jar` 中的 `Item.Properties.spear(...)`（`dlx`/`dlp$a`）、
`KineticWeapon`（`doq`）、`AttackRange`（`dnu`）、`PiercingWeapon`（`dow`）、
`ProjectileUtil`（`dee`）、`LivingEntity.stabAttack`（`chl`）。

## 结论

- 普通/创造射程、射线起止点、命中箱回退、方块遮挡、接触冷却、速度条件、三个窗口与结算顺序
  已按 1.21.11 重写。GameTest **38/38** 通过。
- 本轮修掉的偏差：
  1. 创造模式射程 4.5 → **6.5**；
  2. 射线末端补上「一 tick 位移」；
  3. 方块裁剪从眼睛开始，墙比 2.0 格还近时整 tick 取消（删掉了生产代码里的 `<0.5` 特判）；
  4. 命中箱改为「精确盒 → 0.125 放大盒 + 到中心的视线检查」；
  5. 接触冷却改为射线命中后、速度条件前登记，使用 `gameTime`，10 tick；
  6. 挑落 / 击退 / 伤害三个条件解耦，不再被伤害条件一票否决；
  7. 伤害命中本身也附带原版击退；
  8. 钻石 / 下界合金的击退窗口修正为 **130 / 110 tick**（原抄成 150 / 140）；
  9. 实体过滤补上 `isPickable`、PvP `canHarmPlayer`、`isAlliedTo`。
- 保留的差异都是匠魂/1.20.1 适配或表现层，见文末。

## 原版参数

`Item.Properties.spear(...)` 写入的组件：

| 组件 | 值 |
| --- | --- |
| `AttackRange` | `min_reach 2.0`，`max_reach 4.5`，`min_creative_reach 2.0`，`max_creative_reach 6.5`，`hitbox_margin 0.125`，`mob_factor 0.5` |
| `KineticWeapon` | `contact_cooldown_ticks 10`，`forward_movement 0.38`，damage multiplier 按材质 |
| `PiercingWeapon` | `deals_knockback true`，`dismounts false` |

`KineticWeapon` 的攻击条件（`doq$a`）：

```text
elapsed = useTicks - delay
条件成立 = elapsed <= max_duration_ticks
        && forwardSpeed   >= min_speed * factor
        && relativeSpeed  >= min_relative_speed * factor
factor = 玩家 1.0 / 其他生物 0.2
```

- 挑落：`min_speed = 材质挑落速度`，`min_relative_speed = 0`；
- 击退：`min_speed = 5.1`，`min_relative_speed = 0`；
- 伤害：`min_speed = 0`，`min_relative_speed = 4.6`。

`relativeSpeed = max(0, attackerForwardSpeed - targetProjectedSpeed)`；
`forwardSpeed` 与 `targetProjectedSpeed` 都是 `视线 · 速度`，单位格/秒。

伤害：

```text
damage = attackDamage + floor(relativeSpeed * damageMultiplier)
```

## 射线与命中箱（ProjectileUtil）

原版 `getHitEntitiesAlong(attacker, attackRange, canHit, ClipContext.Block.COLLIDER)`：

1. `look = getHeadLookAngle()`；
2. `start = eye + look * minReach`；
3. `end = eye + look * (maxReach + max(0, knownMovement · look))`；
4. 从**眼睛**到 `end` 做方块裁剪：
   - 方块命中点比 `start` 还近 → 直接返回方块命中，本 tick 没有实体命中；
   - 否则把 `end` 截到方块命中点；
5. 搜索盒 = `AABB.ofSize(start, margin, margin, margin).expandTowards(end - start).inflate(1.0)`；
6. 对每个候选实体：
   - 命中箱包含 `start` → 命中；
   - 否则 `box.clip(start, end)`；
   - 否则 `box.inflate(margin).clip(start, end)`，再从放大命中点到命中箱中心做一次方块裁剪，
     用 `box.clip(放大命中点, 视线终点)` 判断是否真的能碰到；
7. 返回所有命中的 `EntityHitResult`；`KineticWeapon` 逐个处理。

## 实现对照

| 项目 | 1.21.11 | 修正前 | 现在 |
| --- | --- | --- | --- |
| 普通射程 | 2.0 ~ 4.5 | 2.0 ~ 4.5 | 一致 |
| 创造射程 | 2.0 ~ 6.5 | 2.0 ~ 4.5 | `MAX_CREATIVE_REACH = 6.5` |
| 射线起点 | 眼睛 + 视线×2.0 | 眼睛 | 一致 |
| 射线终点 | 最大射程 + 一 tick 位移 | 固定 4.5 | `forwardSpeed / 20` 补偿 |
| 方块裁剪 | 从眼睛；< 2.0 整 tick 取消 | 眼睛→4.5；`<0.5` 忽略 | 一致（测试用空气房） |
| 命中箱 | 精确 → 0.125 放大 + 中心视线 | 始终 0.125 放大 + 距离区间 | 一致 |
| 接触冷却 | 10 tick，`gameTime`，射线命中后、条件前 | 10 tick，`tickCount`，伤害后 | 一致 |
| 挑落条件 | 前向速度 ≥ 材质阈值，窗口内 | 被伤害条件绑定 | 独立 |
| 击退条件 | 前向速度 ≥ 5.1，窗口内 | 被伤害条件绑定 | 独立；伤害命中本身也击退 |
| 伤害条件 | 相对速度 ≥ 4.6，窗口内 | 一致 | 一致 |
| 击退方向/强度 | 偏航角；`0.4 + getKnockback/2` | `look.x/z`；固定 0.5 | 偏航角 + 属性/附魔 |
| 实体过滤 | 存活、非无敌、可拾取、PvP/队伍 | 存活、非观察者 | 增加无敌/可拾取/队伍/PvP（另保留观察者过滤） |

## 材质窗口（激活后计时，tick）

原版物品工厂的常数（秒 × 20 = 激活后的 `max_duration_ticks`；激活延迟单独一列）：

| 等级（对应原版） | 激活延迟 | 挑落窗口 | 挑落速度 | 击退窗口 | 击退速度 | 伤害窗口 | 伤害相对速度 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 0 木 | 15 | 100 | 14 | 200 | 5.1 | 300 | 4.6 |
| 1 石 | 14 | 90 | 10 | 180 | 5.1 | 275 | 4.6 |
| 2 铁 | 12 | 50 | 8 | 135 | 5.1 | 225 | 4.6 |
| 3 钻石 | 10 | 60 | 7.5 | **130** | 5.1 | 200 | 4.6 |
| 4 下界合金 | 8 | 50 | 7 | **110** | 5.1 | 175 | 4.6 |

（铜/金没有单独的匠魂等级，按耐久映射到相邻档；这是匠魂侧的适配。）

## 有意保留的差异

- **数值来自匠魂材料**：等级按矛头耐久分档，手柄耐久修正给窗口 ±10 tick，矛头
  `futuretinker:charge` 冲刺系数乘到伤害倍率上；原版是按材质写死的。
- **只处理玩家冲锋**：`mob_factor`（非玩家 0.2）不会用到。
- **候选只取 `LivingEntity`**：原版 `canHit` 也允许 `Interaction` 等可拾取实体，
  但它们吃不到长矛伤害，只会消耗接触冷却；对实际战斗没有影响。
- **附魔伤害是近似**：1.20.1 用 `EnchantmentHelper.getDamageBonus`；1.21.11 用
  `EnchantmentHelper.modifyDamage`（可能包含更多附魔/修正）。
- **速度来源**：1.20.1 服务端玩家的 `deltaMovement` 不可靠，玩家用位置差；怪物用
  `deltaMovement`（更接近原版 `lastKnownSpeed`）；非玩家乘客用**根载具**的速度（原版
  `KineticWeapon.a(Entity)` 的行为）。同一 tick 先快照所有候选的相对速度，避免前面的目标
  被击退后 `deltaMovement` 立刻改变、影响后面的骑手/根载具判定。玩家目标第一次进入射线时
  还没有历史样本，会先按 0 算一 tick。
- **表现层**：命中音效是 1.20.1 的替代音；没有接 `SpearMobsTrigger` 进度和原版
  命中反馈位移。动画另见 README 的 `firstPersonUse` 说明。

## 验证

- `./gradlew build` 通过；
- `python tools/verify_gametest.py` → `All 38 required tests passed`；
- 新增断言：射程常数 2.0/4.5/6.5、命中箱 0.125、速度门槛 4.6/5.1；
- `SpearChargeTests` 的冲锋用例先 `carveTestRoom` 挖出空气房：GameTest 结构上方 40 格是
  石头，原版方块裁剪会正确地把贴脸石头判成墙；不能为此在生产代码里加特判。
