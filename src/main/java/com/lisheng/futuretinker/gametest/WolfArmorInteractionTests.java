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
 * 右键交互的判定。
 *
 * <p>用户实测 bug：右键狼没给狼穿上，铠甲跑到了玩家自己的胸甲位。根因是**只监听了
 * {@code EntityInteract}**：真客户端点实体会发 {@code INTERACT_AT} 包，Forge 把它派发成
 * {@code EntityInteractSpecific}，那条路径上没人处理、也没有取消事件，于是客户端的
 * 「用物品」逻辑接手，把铠甲当成了玩家换装。</p>
 *
 * <p>这里在**事件层**测试（Forge 的集成点就是这两个事件），并显式断言事件被取消——
 * 取消是阻止客户端继续用物品、从而阻止「穿到玩家身上」的那一步。</p>
 */
@GameTestHolder(FutureTinker.MOD_ID)
public final class WolfArmorInteractionTests {
    private WolfArmorInteractionTests() {}

    /** 真客户端的路径：INTERACT_AT → EntityInteractSpecific。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void specificEventEquipsWolfAndCancels(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        Wolf wolf = tamedWolf(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, ironWolfArmor());

        PlayerInteractEvent.EntityInteractSpecific event = new PlayerInteractEvent.EntityInteractSpecific(
                player, InteractionHand.MAIN_HAND, wolf, new Vec3(0.5D, 0.5D, 0.5D));
        MinecraftForge.EVENT_BUS.post(event);

        helper.assertTrue(event.isCanceled(),
                "事件没有被取消：客户端会继续执行用物品逻辑，铠甲会被穿到玩家身上（这就是那个 bug）");
        assertEquippedOnWolf(helper, player, wolf);
        helper.succeed();
    }

    /** 服务端指令式交互路径：INTERACT → EntityInteract。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void plainEventEquipsWolfAndCancels(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        Wolf wolf = tamedWolf(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, ironWolfArmor());

        PlayerInteractEvent.EntityInteract event = new PlayerInteractEvent.EntityInteract(
                player, InteractionHand.MAIN_HAND, wolf);
        MinecraftForge.EVENT_BUS.post(event);

        helper.assertTrue(event.isCanceled(), "EntityInteract 没有被取消");
        assertEquippedOnWolf(helper, player, wolf);
        helper.succeed();
    }

    /** 副手拿铠甲右键。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void offhandEquipsWolf(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        Wolf wolf = tamedWolf(helper);
        player.setItemInHand(InteractionHand.OFF_HAND, ironWolfArmor());

        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteractSpecific(
                player, InteractionHand.OFF_HAND, wolf, new Vec3(0.5D, 0.5D, 0.5D)));

        helper.assertTrue(WolfArmorContent.isWolfArmor(wolf.getItemBySlot(EquipmentSlot.CHEST)),
                "副手拿铠甲右键狼没有穿上");
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
                "副手那次右键把铠甲穿到了玩家胸甲位");
        helper.assertTrue(player.getItemInHand(InteractionHand.OFF_HAND).isEmpty(),
                "副手的铠甲没有被消耗");
        helper.succeed();
    }

    /** 野狼不穿；而且决不能被穿到玩家身上。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void untamedWolfStaysNaked(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(1, 1, 1));
        player.setItemInHand(InteractionHand.MAIN_HAND, ironWolfArmor());

        MinecraftForge.EVENT_BUS.post(new PlayerInteractEvent.EntityInteractSpecific(
                player, InteractionHand.MAIN_HAND, wolf, new Vec3(0.5D, 0.5D, 0.5D)));

        helper.assertTrue(wolf.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "野狼不该被穿上狼铠");
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
                "野狼那次右键把铠甲穿到了玩家胸甲位：这就是要修的 bug");
        helper.succeed();
    }

    /** 潜行 + 空手右键：脱回玩家背包。 */
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

    private static void assertEquippedOnWolf(GameTestHelper helper, Player player, Wolf wolf) {
        helper.assertTrue(WolfArmorContent.isWolfArmor(wolf.getItemBySlot(EquipmentSlot.CHEST)),
                "狼身上没有铠甲：交互没有把铠甲装到狼的护甲槽里");
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
                "铠甲被穿到了玩家自己的胸甲位：右键被当成了玩家换装，必须修");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()
                        && player.getItemInHand(InteractionHand.OFF_HAND).isEmpty(),
                "玩家手上还留着铠甲，说明发生了复制");
    }

    private static ItemStack ironWolfArmor() {
        MaterialNBT materials = MaterialNBT.of(MaterialRegistry.getMaterial(new MaterialId("tconstruct:iron")));
        return ToolBuildHandler.buildItemFromMaterials((IModifiable) WolfArmorContent.wolfArmor(), materials);
    }
}
