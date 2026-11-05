package com.lisheng.futuretinker.gametest;

import com.google.common.collect.Multimap;
import com.lisheng.futuretinker.FutureTinker;
import com.lisheng.futuretinker.spear.SpearAttack;
import com.lisheng.futuretinker.spear.SpearItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 长矛的「戳刺」规矩：触及、最短距离、满蓄力、不暴击、不挖方块、多目标、0.125 碰撞箱放大。
 *
 * <p>都用 40 格高空做战场：GameTest 的结构紧挨着，不抬高的话扫描会打到隔壁测试的实体。</p>
 */
@GameTestHolder(FutureTinker.MOD_ID)
public final class SpearAttackTests {
    private SpearAttackTests() {}

    private static Player highPlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.setPos(player.getX(), player.getY() + 40.0D, player.getZ());
        return player;
    }

    private static Wolf wolfAt(Player player, double distance, GameTestHelper helper) {
        Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(1, 1, 1));
        wolf.moveTo(player.getX() + distance, player.getY(), player.getZ(), 0.0F, 0.0F);
        return wolf;
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void reachBonusIsOnMainHand(GameTestHelper helper) {
        ItemStack spear = SpearTestSupport.ironSpear();
        Multimap<Attribute, AttributeModifier> modifiers = spear.getAttributeModifiers(EquipmentSlot.MAINHAND);
        boolean found = modifiers.get(ForgeMod.ENTITY_REACH.get()).stream()
                .anyMatch(modifier -> modifier.getAmount() == SpearItem.REACH_BONUS
                        && modifier.getOperation() == AttributeModifier.Operation.ADDITION);
        helper.assertTrue(found,
                "长矛没有给主手加 forge:entity_reach +" + SpearItem.REACH_BONUS
                        + "，触及还是原版 3 格（实际修饰符：" + modifiers + "）");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void jabNeedsFullChargeAndMinRange(GameTestHelper helper) {
        Player player = highPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, SpearTestSupport.ironSpear());
        player.resetAttackStrengthTicker();
        Wolf far = wolfAt(player, 3.0D, helper);

        AttackEntityEvent notCharged = new AttackEntityEvent(player, far);
        SpearAttack.onAttack(notCharged);
        helper.assertTrue(notCharged.isCanceled(),
                "没满蓄力也戳出去了：原版长矛必须等攻击冷却 100% 才能攻击");

        SpearTestSupport.forceFullCharge(player);
        AttackEntityEvent charged = new AttackEntityEvent(player, far);
        SpearAttack.onAttack(charged);
        helper.assertTrue(!charged.isCanceled(),
                "满蓄力 + 3 格距离，戳刺被错误取消（min range 是 " + SpearAttack.MIN_JAB_RANGE + "）");

        Wolf close = wolfAt(player, 1.0D, helper);
        AttackEntityEvent tooClose = new AttackEntityEvent(player, close);
        SpearAttack.onAttack(tooClose);
        helper.assertTrue(tooClose.isCanceled(),
                "距离 1 格还能戳：原版长矛 2 格内打不到");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void criticalHitIsDenied(GameTestHelper helper) {
        Player player = highPlayer(helper);
        Wolf wolf = wolfAt(player, 3.0D, helper);

        player.setItemInHand(InteractionHand.MAIN_HAND, SpearTestSupport.ironSpear());
        CriticalHitEvent withSpear = new CriticalHitEvent(player, wolf, 1.5F, true);
        SpearAttack.onCritical(withSpear);
        helper.assertTrue(withSpear.getResult() == Event.Result.DENY,
                "拿着长矛仍然暴击：原版长矛不会暴击（应该把事件结果设成 DENY）");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        CriticalHitEvent withFist = new CriticalHitEvent(player, wolf, 1.5F, true);
        SpearAttack.onCritical(withFist);
        helper.assertTrue(withFist.getResult() == Event.Result.DEFAULT,
                "空手时也被我们的暴击拦截改了结果，影响面太大");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void spearDoesNotMineBlocks(GameTestHelper helper) {
        Player player = highPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, SpearTestSupport.ironSpear());
        BlockPos pos = helper.absolutePos(new BlockPos(0, 0, 0));

        PlayerInteractEvent.LeftClickBlock event =
                new PlayerInteractEvent.LeftClickBlock(player, pos, Direction.UP);
        SpearAttack.onLeftClickBlock(event);
        helper.assertTrue(event.getUseBlock() == Event.Result.DENY && event.getUseItem() == Event.Result.DENY,
                "拿着长矛左键方块还能挖：原版长矛对块也是戳刺，不破坏方块");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void sweepHitsEveryTargetInFront(GameTestHelper helper) {
        Player player = highPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, SpearTestSupport.ironSpear());
        SpearTestSupport.forceFullCharge(player);
        player.setYRot(-90.0F);
        player.setXRot(0.0F);

        Zombie near = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        Zombie far = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        near.moveTo(player.getX() + 2.5D, player.getY(), player.getZ(), 0.0F, 0.0F);
        far.moveTo(player.getX() + 3.5D, player.getY(), player.getZ(), 0.0F, 0.0F);

        float nearHealth = near.getHealth();
        float farHealth = far.getHealth();
        SpearAttack.sweep(player, null);

        helper.assertTrue(near.getHealth() < nearHealth && far.getHealth() < farHealth,
                "一次戳刺没有同时打到前方两个目标（原版长矛可以命中多个目标）：近 "
                        + nearHealth + "->" + near.getHealth() + "，远 " + farHealth + "->" + far.getHealth());
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void hitboxMarginMakesNearMissesHit(GameTestHelper helper) {
        Player player = highPlayer(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, SpearTestSupport.ironSpear());
        SpearTestSupport.forceFullCharge(player);
        player.setYRot(-90.0F);
        player.setXRot(0.0F);

        // 僵尸碰撞箱半宽 0.3；把它偏到 0.36：射线擦不到真实碰撞箱，但放大 0.125 后能擦到
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        zombie.moveTo(player.getX() + 3.0D, player.getY(), player.getZ() + 0.36D, 0.0F, 0.0F);

        AABB realBox = zombie.getBoundingBox();
        var rawHit = realBox.clip(player.getEyePosition(),
                player.getEyePosition().add(player.getLookAngle().scale(4.5D)));
        helper.assertTrue(rawHit.isEmpty(),
                "测试前提不成立：不放大碰撞箱时射线本来就打中了（目标没偏够）");

        float health = zombie.getHealth();
        SpearAttack.sweep(player, null);
        helper.assertTrue(zombie.getHealth() < health,
                "擦边 0.06 格没打中：原版长矛给目标碰撞箱放大 " + SpearAttack.HITBOX_MARGIN + " 格");
        helper.succeed();
    }
}
