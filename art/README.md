# 狼铠的像素资产

两张贴图，都由**可重跑的脚本/plan** 产出，没有一张是手绘后放进去的：

| 贴图 | 落地路径 | 来源 |
| --- | --- | --- |
| 成品护甲图标（16×16） | `src/main/resources/assets/futuretinker/textures/item/wolf_armor.png` | `plans/wolf_armor.plan.json` |
| 狼身上的铠甲图集（64×32） | `.../textures/entity/wolf_armor.png` | `wolf_armor_atlas.py` |

部件贴图不做了：部件直接复用匠魂的 `chestplate_plating` / `maille` / `large_plate`，
所以 `art/` 里也不再有自己的部件 plan。

**颜色都是灰度**：游戏会用材料色乘上去（`MaterialRenderInfo#vertexColor()`），
图自己带色就变成双重上色——金的铠甲会变橄榄色。这一条踩过。

## 参考与重建步骤

形交给**同类参考**（护甲给护甲、皮垫给皮革、绑带给绑带），材质与配色由我们定；
采样用 `value`（只借明暗节奏，不借颜色）。

```bash
# 1) 参考图（从依赖 jar / 官方 jar 里取，不入仓，见 .gitignore）
python -m mc_art list-groups --source <原版 1.21.4 client.jar> \
    --extract minecraft:item/wolf_armor --to art/refs1214

# 2) 渲染（在 mc-art skill 根目录下执行，plan 里的相对路径 ../refs1214 指 art/refs1214）
python -m mc_art render --plan art/plans/wolf_armor.plan.json --out art/out/wolf_armor
# 产物 art/out/wolf_armor/sprite.png 复制到 src/main/resources/assets/futuretinker/textures/item/wolf_armor.png
```

**环境要求**：引擎调用 `PIL.Image.get_flattened_data()`，Pillow **< 12 没有这个方法**
（本机 11.3.0 实测报 `'Image' object has no attribute 'get_flattened_data'`）。
用一个装 Pillow ≥ 12 的 venv 跑，别升级全局环境。

## 阈值不是拍脑袋定的

`band_maximum_isolated` 曾经在 0.10 上失败：先量参考自身——1.21.4 的狼铠图标是 **6.76%**，
金属件天生带一道硬高光，0.10 是给方块的预算，不是给金属物品的。我们的第 2 轮是 16.22%，
比参考高两倍多，所以判定「是我画得差，不是阈值不合适」，改画法而不是加 waiver；
第 3 轮 9 档 gradient 做到 **5.41%**，比参考自身的 6.76% 还低。加了 waiver 的是
`threshold_waiver` 写在 plan 里的那一处，理由必须可读。

## 最后一关：三问答

- **能看出是什么**：能。横着的背垫 + 两条垂下的带子，和 1.21.4 原版狼铠图标同一个轮廓；
  不会读成胸甲——上一版就是画成换皮胸甲被打回来的。
- **丑不丑**：不丑，但它是**安静**的：整张图只有一个金属色阶、没有花纹。这和匠魂部件的
  观感一致；玩家要盯着看的不是图标，是穿在狼身上的那层，所以图标不需要更热闹。
- **和原版像同一个游戏吗**：像。用的是原版图标自己的明暗节奏（`value` 采样），
  只把材质换成中性灰——**颜色由材料决定**，所以铁的狼铠是铁色、玛玉灵的狼铠是紫色，
  这一点用一张合成图核过（灰 / 铁 / 玛玉灵 / 金四档并排）。

## 成品图标改版：形取 1.21.4，不再画成胸甲

首版把成品图标画成了「换皮铁胸甲」，方向错了。改成取**高版本原版狼铠**的形状：

```bash
# 参考来源：Mojang 官方版本清单 -> 1.21.4 client.jar（下载到 .art-work/，不入仓）
python -m mc_art list-groups --source <1.21.4 client.jar> --extract minecraft:item/wolf_armor --to art/refs1214
```

1.21.4 的狼铠图标是**横着的背垫 + 两条垂下的小带子**（不是竖着的胸甲），
`appearance_only` 直接沿它的 alpha 轮廓，材质换成中性灰（颜色交给材料）。

明暗调了三轮，每轮都有数：

| 轮次 | 画法 | isolated | 结论 |
| --- | --- | --- | --- |
| 1 | 等 α + 5 档 gradient | 1.35% | 轮廓对了，但整体压成灰泥，太暗 |
| 2 | 5 档 bands + 描边 | **16.22%（失败）** | 相邻档位亮度差 > 24，被判 scattered |
| 3 | 9 档细密色阶 + gradient | **5.41%** | **比 1.21.4 参考自身的 6.76% 还低**，色阶整体上移后亮顶读得出来 |

第 2 轮是关键判据：先量参考自身（6.76%），才判定「16.22% 是我画得差，不是阈值不合适」——
所以没有走 waiver，而是改画法。

## 实体铠甲图集（64×32，穿在狼身上）

`wolf_armor_atlas.py` 生成 `.../textures/entity/wolf_armor.png`。它不是「另画一只狼」，
而是**同一张 UV 上的第二层皮**：游戏里用一套「逐箱放大」的狼骨骼画（`WolfArmorModel`），
铠甲像素不透明、露肉处透明，所以头部那几块矩形全留空（露出真脸），只铺
body / upper_body / 四条腿 / 尾巴。放大不改变 UV，只是把同一批矩形撑大一点，图集不用改。

- UV 矩形不是猜的：从 1.20.1 的 `WolfModel` 反编译源里逐个 `m_171481_(u,v,x,y,z,w,h,d)` 抄出来，
  写成 `.art-work/wolfarmor/wolf_model.json`，再用 `mc-art ingame` 拿**原版狼贴图**渲染一遍——
  渲染出来是一只正常的狼，说明矩形对上了；`entity --atlas` 报 `99.7% covered, 3 stray`
  （3 stray 是原版狼贴图自带的已知杂点）。
- 放大版另存 `wolf_armor_model.json`（`inflate` 0.75 / 腿 0.4），拿它再跑一次 `entity --atlas`
  仍是 `100% covered, 0 stray`——说明「放大」没有把贴图挪出采样区。
- 颜色是灰度色阶：颜色由游戏用材料色乘上去（`MaterialRenderInfo#vertexColor()`），
  图集自己带色会变成「双重上色」。
- 明暗只留三档（MID/LIGHT/SHADE）+ 深色描边；2 像素宽的腿试过纵向条纹，渲染出来像糖果棒，
  改成「平铁 + 关节处一道深色箍」。
- 判据：`mc-art entity --spec ... --atlas <png>` → `VERDICT PASS every opaque texel lands inside a box`；
  再用 `mc-art ingame --model ... --texture <png> --view front34/side` 渲染并**看图**。
  数字全绿也说明不了「像不像铠甲」，这一步的结论来自图。

