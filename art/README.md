# 狼铠的像素资产（首版）

四张 16×16 贴图，全部由 `mc-art` 引擎（skill 自带）栅格化，**没有一张是手绘 PNG**。
`plans/` 里是生成它们的 plan；plan 是记录，改美术先改 plan 再重渲染。

| 贴图 | 落地路径 | plan |
| --- | --- | --- |
| 成品护甲 | `src/main/resources/assets/futuretinker/textures/item/wolf_armor.png` | `plans/wolf_armor.plan.json` |
| 外层甲片 | `.../textures/item/parts/wolf_armor_plating.png` | `plans/wolf_armor_plating.plan.json` |
| 内衬 | `.../textures/item/parts/wolf_armor_lining.png` | `plans/wolf_armor_lining.plan.json` |
| 绑定 | `.../textures/item/parts/wolf_armor_binding.png` | `plans/wolf_armor_binding.plan.json` |

## 参考与重建步骤

形交给**同类参考**（护甲给护甲、皮垫给皮革、绑带给绑带），材质与配色由我们定；
采样用 `value`（只借明暗节奏，不借颜色）。

```bash
# 1) 参考图（从我们的两个依赖 jar 里取，不引入外部素材）
python -m mc_art list-groups --source libs/TConstruct-1.20.1-3.12.1.236.jar \
    --extract tconstruct:item/chestplate_plating --to art/refs
python -m mc_art list-groups --source libs/TConstruct-1.20.1-3.12.1.236.jar \
    --extract tconstruct:item/tool_binding --to art/refs
python -m mc_art list-groups --source <原版 client-extra.jar> \
    --extract minecraft:item/leather --to art/refs
python -m mc_art list-groups --source <原版 client-extra.jar> \
    --extract minecraft:item/iron_chestplate --to art/refs

# 2) 渲染（在 mc-art skill 根目录下执行，plan 里的相对路径 ../refs 指 art/refs）
python -m mc_art render --plan art/plans/wolf_armor.plan.json --out art/out/wolf_armor
```

**环境要求**：引擎调用 `PIL.Image.get_flattened_data()`，Pillow **< 12 没有这个方法**
（本机 11.3.0 实测报 `'Image' object has no attribute 'get_flattened_data'`）。
用一个装 Pillow ≥ 12 的 venv 跑，别升级全局环境。

## 两条按实测做的决定（不是拍脑袋）

1. **`band_maximum_isolated` 从 0.10 放宽到 0.18，并写了 `threshold_waiver`。**
   实测：这张 16×16 金属护甲的同类参考自身就是 `isolated=15.83%`（tconstruct 甲片）、
   `15.94%`（原版铁胸甲）——金属件天生带一道硬高光。我们的产物是 14.17%，**比两个参考都低**。
   0.10 是给方块的预算，不是给金属物品的。引擎会拦住「悄悄放宽阈值」，理由必须写在 plan 里。
2. **绑带压成 3 档紧密色阶。** 细 X 形只有 1–2 像素宽，5 档跨度会让相邻像素亮度差 > 24 luma，
   实测 32.7% 被判 scattered；压到 2 档跨度后 isolated 归零。

## 最后一关：三问答（首版结论）

- **能看出是什么**：是。钢护甲、皮垫、皮绑带、成品护甲都可读。
- **丑不丑**：能用但**偏闷**——钢件有肩部高光、已像金属，整体对比不如原版强烈；皮革件偏暗。
- **和原版像同一个游戏吗**：钢件是同一套语言（暗底+亮高光）；皮革件比原版皮革更暗更闷，是
  「匠魂的皮」而不是原版皮革。一处刻意不同：钢件偏冷蓝（寒钢），四个 plan 共用同一 steel palette。

这一版是能交付的**首版**，不是终稿。M3 做内衬效果时需要世界内渲染（1.20.1 的 `WolfRenderer`
没有护甲层），那时会回来调这三问答。

## 成品图标改版：形取 1.21.4，不再画成胸甲

首版把成品图标画成了「换皮铁胸甲」，方向错了。改成取**高版本原版狼铠**的形状：

```bash
# 参考来源：Mojang 官方版本清单 -> 1.21.4 client.jar（下载到 .art-work/，不入仓）
python -m mc_art list-groups --source <1.21.4 client.jar> --extract minecraft:item/wolf_armor --to art/refs1214
```

1.21.4 的狼铠图标是**横着的背垫 + 两条垂下的小带子**（不是竖着的胸甲），
`appearance_only` 直接沿它的 alpha 轮廓，材质换成我们的冷钢。

明暗调了三轮，每轮都有数：

| 轮次 | 画法 | isolated | 结论 |
| --- | --- | --- | --- |
| 1 | 等 α + 5 档 gradient | 1.35% | 轮廓对了，但整体压成灰泥，太暗 |
| 2 | 5 档 bands + 描边 | **16.22%（失败）** | 相邻档位亮度差 > 24，被判 scattered |
| 3 | 9 档细密色阶 + gradient | **5.41%** | **比 1.21.4 参考自身的 6.76% 还低**，色阶整体上移后亮顶读得出来 |

第 2 轮是关键判据：先量参考自身（6.76%），才判定「16.22% 是我画得差，不是阈值不合适」——
所以没有走 waiver，而是改画法。

