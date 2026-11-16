package com.lisheng.futuretinker.spear;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.lisheng.futuretinker.config.FutureTinkerConfig;
import java.util.UUID;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.fml.DistExecutor;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/**
 * 匠魂长矛。
 *
 * <p>原版长矛有两条攻击路径：点左键的「戳刺」和按住右键的「冲锋」。</p>
 *
 * <ul>
 *   <li><b>触及 4.5</b>：给主手加 {@code forge:entity_reach} +1.5（原版近战达标线是 3.0）。
 *       这个属性同时被客户端拾取（{@code GameRenderer}）与服务端使用，所以不是「看着远、打不到」。</li>
 *   <li><b>冲锋</b>：右键只发一个「开始冲锋」包，**不进原版的「使用物品」状态**——
 *       那个状态会把移动输入乘 0.2（冲锋时应当能跑），还会触发三叉戟的压低动画。
 *       计时与结算在服务端（{@link SpearCharge}），客户端只负责动画进度。</li>
 * </ul>
 */
public class SpearItem extends ModifiableItem {
    /** 原版长矛的攻击距离是 4.5，原版近战是 3.0。 */
    public static final double REACH_BONUS = 1.5D;

    private static final UUID REACH_UUID = UUID.fromString("8b0e3d1a-6d3f-4a52-9d0c-2f7e5a1c4b90");
    private static final UUID DAMAGE_UUID = UUID.fromString("0f6d2b91-3a8e-4c57-9b14-7d2e5c8a6f32");
    private static final UUID SPEED_UUID = UUID.fromString("4a9c7e21-8b3d-4f60-a2c5-1e6b9d0f3a84");

    public SpearItem(Properties properties, ToolDefinition definition) {
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
        builder.put(ForgeMod.ENTITY_REACH.get(), new AttributeModifier(REACH_UUID,
                "futuretinker:spear_reach", FutureTinkerConfig.spearReachBonus(),
                AttributeModifier.Operation.ADDITION));
        double damageDelta = FutureTinkerConfig.spearBaseAttackDamage()
                - FutureTinkerConfig.SPEAR_DEFAULT_ATTACK_DAMAGE;
        if (Math.abs(damageDelta) > 1.0E-6D) {
            builder.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(DAMAGE_UUID,
                    "futuretinker:config_spear_damage", damageDelta,
                    AttributeModifier.Operation.ADDITION));
        }
        double speedDelta = FutureTinkerConfig.spearBaseAttackSpeed()
                - FutureTinkerConfig.SPEAR_DEFAULT_ATTACK_SPEED;
        if (Math.abs(speedDelta) > 1.0E-6D) {
            builder.put(Attributes.ATTACK_SPEED, new AttributeModifier(SPEED_UUID,
                    "futuretinker:config_spear_speed", speedDelta,
                    AttributeModifier.Operation.ADDITION));
        }
        return builder.build();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || ToolDamageUtil.isBroken(stack) || player.isSpectator()) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            // 客户端：本地记开始时间（驱动压低动画）+ 通知服务端开始冲锋
            DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> SpearClientInput::startCharge);
        }
        return InteractionResultHolder.consume(stack);
    }
}
