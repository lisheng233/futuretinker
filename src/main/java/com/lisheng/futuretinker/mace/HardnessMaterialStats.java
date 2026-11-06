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

/** 狼牙棒头的「硬度」：越高，钉刺越穿甲、横扫角度越窄、攻速惩罚越重。 */
public record HardnessMaterialStats(float hardness) implements IMaterialStats {
    public static final MaterialStatsId ID = new MaterialStatsId(FutureTinker.id("hardness"));
    public static final float DEFAULT = 1.0F;

    public static final MaterialStatType<HardnessMaterialStats> TYPE = new MaterialStatType<>(
            ID, new HardnessMaterialStats(DEFAULT),
            RecordLoadable.create(FloatLoadable.ANY.requiredField("hardness", HardnessMaterialStats::hardness),
                    HardnessMaterialStats::new));

    @Override
    public MaterialStatType<?> getType() {
        return TYPE;
    }

    @Override
    public List<Component> getLocalizedInfo() {
        return List.of(Component.translatable("material.futuretinker.hardness", String.format("%.2f", hardness)));
    }

    @Override
    public List<Component> getLocalizedDescriptions() {
        return List.of(Component.translatable("material.futuretinker.hardness.description"));
    }

    @Override
    public void apply(ModifierStatsBuilder builder, float scale) {
        // 只被狼牙棒逻辑读取，不参与工具属性
    }
}
