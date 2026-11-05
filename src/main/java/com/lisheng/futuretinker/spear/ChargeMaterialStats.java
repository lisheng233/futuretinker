package com.lisheng.futuretinker.spear;

import com.lisheng.futuretinker.FutureTinker;
import java.util.List;
import net.minecraft.network.chat.Component;
import slimeknights.mantle.data.loadable.primitive.FloatLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.library.materials.stats.IMaterialStats;
import slimeknights.tconstruct.library.materials.stats.MaterialStatType;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;

/**
 * 矛头材料的**冲刺系数**（speed → damage 的转化系数）。
 *
 * <p>数据位置与匠魂其它统计一样：{@code data/<模组>/tinkering/materials/stats/<材料>.json}
 * 里的 {@code "futuretinker:charge": {"charge": 1.15}}。一但材料没声明这条统计
 * （其它模组添加的材料基本都是这样），{@link MaterialStatType} 的默认值 1.0 会顶上，
 * 所以新材料的矛永远能用，只是倍率是基准值。</p>
 *
 * <p>真正参与计算的公式在 {@link SpearCharge#multiplier}：
 * 倍率 = 由矛头等级/攻击力推出来的基准值 × 本系数。</p>
 */
public record ChargeMaterialStats(float charge) implements IMaterialStats {
    public static final MaterialStatsId ID = new MaterialStatsId(FutureTinker.id("charge"));

    /** 默认冲刺系数：其它模组材料没有这条统计时用的就是它。 */
    public static final float DEFAULT_CHARGE = 1.0F;

    public static final MaterialStatType<ChargeMaterialStats> TYPE = new MaterialStatType<>(
            ID,
            new ChargeMaterialStats(DEFAULT_CHARGE),
            RecordLoadable.create(
                    FloatLoadable.ANY.requiredField("charge", ChargeMaterialStats::charge),
                    ChargeMaterialStats::new));

    @Override
    public MaterialStatType<?> getType() {
        return TYPE;
    }

    @Override
    public List<Component> getLocalizedInfo() {
        return List.of(Component.translatable("material.futuretinker.charge",
                String.format("%.2f", charge)));
    }

    @Override
    public List<Component> getLocalizedDescriptions() {
        return List.of(Component.translatable("material.futuretinker.charge.description"));
    }

    @Override
    public void apply(ModifierStatsBuilder builder, float scale) {
        // 这条统计不参与工具属性的计算，只被长矛的冲锋逻辑读取
    }
}
