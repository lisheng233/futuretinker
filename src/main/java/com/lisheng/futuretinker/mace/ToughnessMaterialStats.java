package com.lisheng.futuretinker.mace;

import com.lisheng.futuretinker.FutureTinker;
import java.util.List;
import net.minecraft.network.chat.Component;
import slimeknights.mantle.data.loadable.primitive.FloatLoadable;
import slimeknights.mantle.data.loadable.record.RecordLoadable;
import slimeknights.tconstruct.library.materials.stats.IMaterialStats;
import slimeknights.tconstruct.library.materials.stats.MaterialStatType;
import slimeknights.tconstruct.library.materials.stats.MaterialStatsId;
import slimeknights.tconstruct.library.tools.stat.ModifierStatsBuilder;

/** 狼牙棒头的「韧性」：越高，钉刺越偏控制（减速 / 抑制击退），横扫角度中等。 */
public record ToughnessMaterialStats(float toughness) implements IMaterialStats {
    public static final MaterialStatsId ID = new MaterialStatsId(FutureTinker.id("toughness"));
    public static final float DEFAULT = 1.0F;

    public static final MaterialStatType<ToughnessMaterialStats> TYPE = new MaterialStatType<>(
            ID, new ToughnessMaterialStats(DEFAULT),
            RecordLoadable.create(FloatLoadable.ANY.requiredField("toughness", ToughnessMaterialStats::toughness),
                    ToughnessMaterialStats::new));

    @Override
    public MaterialStatType<?> getType() {
        return TYPE;
    }

    @Override
    public List<Component> getLocalizedInfo() {
        return List.of(Component.translatable("material.futuretinker.toughness", String.format("%.2f", toughness)));
    }

    @Override
    public List<Component> getLocalizedDescriptions() {
        return List.of(Component.translatable("material.futuretinker.toughness.description"));
    }

    @Override
    public void apply(ModifierStatsBuilder builder, float scale) {
        // 只被狼牙棒逻辑读取，不参与工具属性
    }
}
