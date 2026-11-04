package com.lisheng.futuretinker.wolfarmor;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.library.tools.definition.ModifiableArmorMaterial;
import slimeknights.tconstruct.library.tools.item.armor.ModifiableArmorItem;

/**
 * 狼铠。装在**狼**身上，不是给玩家穿的。
 *
 * <p>交互方式照抄原版**马铠**（{@code AbstractHorse#mobInteract} 里那套）：由物品/实体自己在
 * {@link #interactLivingEntity} 里处理「右键穿、潜行空手脱」，不依赖
 * {@code PlayerInteractEvent}——事件那条路在真客户端上会被交互包的不同分支绕过
 * （实测：只监听 EntityInteract 时右键没反应，而铠甲还会被当成玩家换装）。</p>
 *
 * <p>{@link #canEquip} 对玩家返回 false，这是**根因级**的修复：
 * 任何「把护甲塞进玩家护甲槽」的路径（原版、Forge 或其它模组的自动换装）都会先问这一句，
 * 于是这件物品永远不可能出现在玩家身上。</p>
 */
public final class WolfArmorItem extends ModifiableArmorItem {
    public WolfArmorItem(ModifiableArmorMaterial material, ArmorItem.Type type, Properties properties) {
        super(material, type, properties);
    }

    /** 马铠式交互（物品钩子）。注意：驯服的狼会先吃掉右键，所以真机上主要靠事件路径。 */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        if (!(target instanceof Wolf wolf)) {
            return InteractionResult.PASS;
        }
        if (player.level().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        com.lisheng.futuretinker.FutureTinker.LOGGER.info(
                "[wolfarmor] 物品交互 interactLivingEntity wolfTamed={} held={}", wolf.isTame(), stack.getItem());
        if (stack.isEmpty()) {
            return InteractionResult.PASS;
        }

        ItemStack current = wolf.getItemBySlot(EquipmentSlot.CHEST);
        if (!current.isEmpty()) {
            wolf.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
            giveOrDrop(player, current);
        }
        ItemStack equipped = stack.copy();
        equipped.setCount(1);
        wolf.setItemSlot(EquipmentSlot.CHEST, equipped);
        stack.shrink(1);
        wolf.playSound(SoundEvents.ARMOR_EQUIP_LEATHER, 1.0F, 1.0F);
        return InteractionResult.CONSUME;
    }

    /** 玩家永远不能穿它——这就是「铠甲跑到玩家胸甲位」的根因堵点。 */
    @Override
    public boolean canEquip(ItemStack stack, EquipmentSlot armorType, Entity entity) {
        return entity instanceof Wolf;
    }

    /** 空中右键什么都不做（避免被当成玩家换装）。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    /**
     * 潜行 + 空手右键狼：脱下来。
     *
     * <p>空手时 {@link #interactLivingEntity} 不会被调用（物品不在手上），
     * 所以这条走 {@link WolfArmorInteraction} 的事件兜底。</p>
     */
    static void giveOrDrop(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        if (!player.getInventory().add(stack.copy())) {
            player.drop(stack.copy(), false);
        }
    }
}
