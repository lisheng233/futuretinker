package com.lisheng.futuretinker.wolfarmor;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.lisheng.futuretinker.config.FutureTinkerConfig;
import java.util.UUID;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import slimeknights.tconstruct.library.tools.definition.ModifiableArmorMaterial;
import slimeknights.tconstruct.library.tools.item.armor.ModifiableArmorItem;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;

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
    private static final UUID ARMOR_SCALE_UUID = UUID.fromString("7d3a9c15-2e6b-4f80-9a51-8c4d1f6b3e27");
    private static final UUID ARMOR_BONUS_UUID = UUID.fromString("1c8f4b62-9d5a-4e37-8b20-6f3a7d0c9e54");
    private static final UUID TOUGHNESS_SCALE_UUID = UUID.fromString("5e2d9a74-3c8f-4b16-9d05-7a1e6f8c2b43");
    private static final UUID TOUGHNESS_BONUS_UUID = UUID.fromString("8a6c1f39-4e7b-4d52-a0c3-2b9e5d7f1c68");

    public WolfArmorItem(ModifiableArmorMaterial material, ArmorItem.Type type, Properties properties) {
        super(material, type, properties);
    }

    /**
     * 材料给出基础护甲；配置再叠一层倍率/平加值。默认 2.0/1.0 与
     * {@code wolf_armor_chestplate.json} 的 {@code multiply_stats} 对齐，默认时不加任何修饰符。
     */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(IToolStackView tool,
            EquipmentSlot slot) {
        Multimap<Attribute, AttributeModifier> base = super.getAttributeModifiers(tool, slot);
        if (slot != EquipmentSlot.CHEST) {
            return base;
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> builder = ImmutableMultimap.builder();
        builder.putAll(base);

        double armorScale = FutureTinkerConfig.wolfArmorMultiplier()
                / FutureTinkerConfig.WOLF_ARMOR_DEFAULT_ARMOR_MULTIPLIER;
        if (Math.abs(armorScale - 1.0D) > 1.0E-6D) {
            builder.put(Attributes.ARMOR, new AttributeModifier(ARMOR_SCALE_UUID,
                    "futuretinker:config_wolf_armor_scale", armorScale - 1.0D,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        double armorBonus = FutureTinkerConfig.wolfArmorBonus();
        if (Math.abs(armorBonus) > 1.0E-6D) {
            builder.put(Attributes.ARMOR, new AttributeModifier(ARMOR_BONUS_UUID,
                    "futuretinker:config_wolf_armor_bonus", armorBonus,
                    AttributeModifier.Operation.ADDITION));
        }
        double toughnessScale = FutureTinkerConfig.wolfArmorToughnessMultiplier();
        if (Math.abs(toughnessScale - 1.0D) > 1.0E-6D) {
            builder.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(TOUGHNESS_SCALE_UUID,
                    "futuretinker:config_wolf_armor_toughness_scale", toughnessScale - 1.0D,
                    AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
        double toughnessBonus = FutureTinkerConfig.wolfArmorToughnessBonus();
        if (Math.abs(toughnessBonus) > 1.0E-6D) {
            builder.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(TOUGHNESS_BONUS_UUID,
                    "futuretinker:config_wolf_armor_toughness_bonus", toughnessBonus,
                    AttributeModifier.Operation.ADDITION));
        }
        return builder.build();
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
