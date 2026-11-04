package com.lisheng.futuretinker.gametest;

import com.lisheng.futuretinker.FutureTinker;
import com.lisheng.futuretinker.wolfarmor.WolfArmorContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;

/**
 * 狼铠交互的判定（照原版马铠的做法）。
 *
 * <p>用户实测 bug：右键狼没穿上，铠甲反而穿到玩家胸甲位。修法不是继续补事件监听，而是改到
 * **物品自己**处理（{@link com.lisheng.futuretinker.wolfarmor.WolfArmorItem#interactLivingEntity}），
 * 并用 {@code canEquip} 对玩家返回 false —— 那是所有「塞进玩家护甲槽」路径都会问的一句。</p>
 */
@GameTestHolder(FutureTinker.MOD_ID)
public final class WolfArmorInteractionTests {
    private WolfArmorInteractionTests() {}

    /** 核心 bug 的回归断言：玩家永远穿不上它。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void playerCanNeverEquipIt(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        Wolf wolf = tamedWolf(helper);
        ItemStack armor = ironWolfArmor();
        helper.assertTrue(!armor.canEquip(EquipmentSlot.CHEST, player),
                "canEquip 对玩家返回了 true：任何自动换装路径都会把狼铠穿到玩家身上（这就是那个 bug）");
        helper.assertTrue(armor.canEquip(EquipmentSlot.CHEST, wolf),
                "canEquip 对狼返回了 false：狼永远穿不上");
        helper.succeed();
    }

    /** 物品自己的交互（马铠式）：右键驯服的狼就穿上。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void itemInteractionEquipsWolf(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        Wolf wolf = tamedWolf(helper);
        ItemStack armor = ironWolfArmor();
        player.setItemInHand(InteractionHand.MAIN_HAND, armor);

        player.getItemInHand(InteractionHand.MAIN_HAND)
                .getItem()
                .interactLivingEntity(armor, player, wolf, InteractionHand.MAIN_HAND);

        helper.assertTrue(WolfArmorContent.isWolfArmor(wolf.getItemBySlot(EquipmentSlot.CHEST)),
                "物品交互没有把狼铠装到狼身上");
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
                "狼铠跑到了玩家胸甲位");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                "手上的狼铠没有被消耗（复制 bug）");
        helper.succeed();
    }

    /** 野狼不穿。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void untamedWolfStaysNaked(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(1, 1, 1));
        ItemStack armor = ironWolfArmor();
        player.setItemInHand(InteractionHand.MAIN_HAND, armor);

        armor.getItem().interactLivingEntity(armor, player, wolf, InteractionHand.MAIN_HAND);

        helper.assertTrue(wolf.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "野狼不该被穿上狼铠");
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
                "野狼那次交互把狼铠穿到了玩家身上");
        helper.succeed();
    }

    /** 潜行 + 空手右键：脱回玩家背包（空手时物品交互不会被调用，走事件兜底）。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void sneakEmptyHandUnequips(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        Wolf wolf = tamedWolf(helper);
        ItemStack armor = ironWolfArmor();
        wolf.setItemSlot(EquipmentSlot.CHEST, armor.copy());

        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteractSpecific(
                player, InteractionHand.MAIN_HAND, wolf, new Vec3(0.5D, 0.5D, 0.5D)));

        helper.assertTrue(wolf.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "潜行右键没有脱下狼铠");
        helper.assertTrue(player.getInventory().contains(armor), "脱下来的铠甲没有回到玩家背包");
        helper.succeed();
    }

    private static Wolf tamedWolf(GameTestHelper helper) {
        Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(1, 1, 1));
        wolf.setTame(true);
        return wolf;
    }

    private static ItemStack ironWolfArmor() {
        MaterialNBT materials = MaterialNBT.of(MaterialRegistry.getMaterial(new MaterialId("tconstruct:iron")));
        return ToolBuildHandler.buildItemFromMaterials((IModifiable) WolfArmorContent.wolfArmor(), materials);
    }
}
