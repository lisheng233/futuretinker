package com.lisheng.futuretinker.spear;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.UUID;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.helper.ToolDamageUtil;
import slimeknights.tconstruct.library.tools.item.ModifiableItem;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

/**
 * 匠魂长矛。
 *
 * <p>原版长矛有两条攻击路径：点左键的「戳刺」和按住右键的「冲锋」。戳刺基本是原版近战流程，
 * 本类只负责三件原版长矛特有的事：</p>
 *
 * <ul>
 *   <li><b>触及 4.5</b>：给主手加 {@code forge:entity_reach} +1.5（原版近战达标线是 3.0）。
 *       这个属性同时被客户端拾取（{@code GameRenderer}）与服务端使用，所以不是「看着远、打不到」。</li>
 *   <li><b>冲锋的持矛姿势</b>：用原版的 {@link UseAnim#SPEAR}（三叉戟那套），按住右键把矛压低。</li>
 *   <li><b>冲锋结算</b>：每 tick 交给 {@link SpearCharge}（相对速度伤害、击退、挑落、疲劳阶段）。</li>
 * </ul>
 */
public class SpearItem extends ModifiableItem {
    /** 原版长矛的攻击距离是 4.5，原版近战是 3.0。 */
    public static final double REACH_BONUS = 1.5D;

    private static final UUID REACH_UUID = UUID.fromString("8b0e3d1a-6d3f-4a52-9d0c-2f7e5a1c4b90");
    private static final AttributeModifier REACH_MODIFIER = new AttributeModifier(
            REACH_UUID, "futuretinker:spear_reach", REACH_BONUS, AttributeModifier.Operation.ADDITION);

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
        builder.put(ForgeMod.ENTITY_REACH.get(), REACH_MODIFIER);
        return builder.build();
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (ToolDamageUtil.isBroken(stack) || player.isSpectator()) {
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        if (!level.isClientSide) {
            SpearModule.playLowerSound(player);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        // 只在服务端结算；客户端的持矛姿势由 UseAnim.SPEAR 自己播
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        int heldTicks = getUseDuration(stack) - remainingUseDuration;
        SpearCharge.tick(player, stack, heldTicks);
    }
}
