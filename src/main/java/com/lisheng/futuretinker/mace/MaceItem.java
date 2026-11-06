package com.lisheng.futuretinker.mace;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.UUID;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

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
}
