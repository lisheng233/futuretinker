package com.lisheng.futuretinker.mace;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.lisheng.futuretinker.config.FutureTinkerConfig;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 狼牙棒。
 *
 * <p>两条材料方向的落点全在属性修饰符上：</p>
 * <ul>
 *   <li>棒头形态给攻速修正（轻质 +30%，高硬度 −20%）；</li>
 *   <li>手柄重量给移速惩罚（0 ~ −8%，重手柄更沉）。</li>
 * </ul>
 * <p>命中时的钩扯/穿甲/减速/横扫在 {@link MaceCombat}。</p>
 */
public class MaceItem extends ModifiableItem {
    private static final UUID ATTACK_SPEED_UUID = UUID.fromString("3c1f9a52-6b7e-4d21-9e0a-8f5c2d7b1a30");
    private static final UUID MOVE_SPEED_UUID = UUID.fromString("5d2b8e14-9c3a-4f76-b1d5-2a7e6c9f4b21");
    private static final UUID BASE_DAMAGE_UUID = UUID.fromString("9e4a1c63-7d2f-4b80-8a15-6c3e9f0d2b47");
    private static final UUID BASE_SPEED_UUID = UUID.fromString("2b7f5d90-4c1e-4a83-9f26-8d0b6e3c5a71");

    public MaceItem(Properties properties, ToolDefinition definition) {
        super(properties, definition);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(IToolStackView tool, EquipmentSlot slot) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(tool, slot);
        if (slot != EquipmentSlot.MAINHAND) {
            return base;
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.putAll(base);

        double baseDamageDelta = FutureTinkerConfig.maceBaseAttackDamage()
                - FutureTinkerConfig.MACE_DEFAULT_ATTACK_DAMAGE;
        if (Math.abs(baseDamageDelta) > 1.0E-6D) {
            builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_DAMAGE_UUID,
                    "futuretinker:config_mace_damage", baseDamageDelta,
                    AttributeModifier.Operation.ADDITION));
        }
        double baseSpeedDelta = FutureTinkerConfig.maceBaseAttackSpeed()
                - FutureTinkerConfig.MACE_DEFAULT_ATTACK_SPEED;
        if (Math.abs(baseSpeedDelta) > 1.0E-6D) {
            builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_SPEED_UUID,
                    "futuretinker:config_mace_speed", baseSpeedDelta,
                    AttributeModifier.Operation.ADDITION));
        }

        double attackSpeed = MaceCombat.attackSpeedModifier(MaceCombat.form(tool));
        if (attackSpeed != 0.0D) {
            builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(ATTACK_SPEED_UUID,
                    "futuretinker:mace_spikes", attackSpeed, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        double movement = MaceCombat.movementSpeedModifier(tool);
        if (movement != 0.0D) {
            builder.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(MOVE_SPEED_UUID,
                    "futuretinker:mace_weight", movement, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        return builder.build();
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip,
            TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        ToolStack tool = ToolStack.from(stack);
        MaceCombat.Form form = MaceCombat.form(tool);
        tooltip.add(Component.translatable("tooltip.futuretinker.mace.form",
                Component.translatable("tooltip.futuretinker.mace.form." + form.name().toLowerCase(Locale.ROOT))));
        tooltip.add(Component.translatable("material.futuretinker.hardness",
                String.format("%.2f", MaceCombat.hardness(tool))));
        tooltip.add(Component.translatable("material.futuretinker.toughness",
                String.format("%.2f", MaceCombat.toughness(tool))));
    }
}
