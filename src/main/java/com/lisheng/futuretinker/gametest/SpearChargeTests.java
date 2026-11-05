package com.lisheng.futuretinker.gametest;

import com.lisheng.futuretinker.FutureTinker;
import com.lisheng.futuretinker.spear.SpearCharge;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 长矛的「冲锋」：速度门槛、伤害公式、三个阶段、材料决定的疲劳窗口、挑落骑乘。
 *
 * <p>这些都是原版 1.21.11 的规则（数值写在 {@link SpearCharge} 的注释里）。测试直接调
 * {@link SpearCharge#tick}，不依赖真实玩家输入，所以每次跑都是同一个答案。</p>
 */
@GameTestHolder(FutureTinker.MOD_ID)
public final class SpearChargeTests {
    private SpearChargeTests() {}

    /**
     * 冲锋**不进原版的「使用物品」状态**——这是「右键之后跑不动」的根因：
     * {@code LocalPlayer.aiStep} 在 {@code isUsingItem()} 时把移动输入乘 0.2、并且禁止疾跑，
     * 而原版长矛冲锋恰恰是要能边跑边压的。（顺带也就不会套用三叉戟那套压低动画。）
     */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void chargeDoesNotUseTheItemSystem(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, SpearTestSupport.ironSpear());

        SpearCharge.start(player);
        helper.assertTrue(SpearCharge.isCharging(player), "右键没有进入冲锋状态");
        helper.assertTrue(!player.isUsingItem(),
                "冲锋进了原版的「使用物品」状态：那会让移动输入乘 0.2 并禁止疾跑（右键之后跑不动）");
        helper.assertTrue(SpearCharge.heldTicks(player) == 0, "刚开始冲锋就有时长");

        SpearCharge.stop(player);
        helper.assertTrue(!SpearCharge.isCharging(player), "松手之后还在冲锋");
        helper.succeed();
    }

    /** 没有矛的时候不能开始冲锋（否则空手右键也进状态）。 */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void chargeNeedsASpearInHand(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        SpearCharge.start(player);
        helper.assertTrue(!SpearCharge.isCharging(player), "空手也开始了冲锋");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void damageAndSpeedGatesMatchVanilla(GameTestHelper helper) {
        // 原版公式：1 + 倍率 × 相对速度（格/秒）；铁矛倍率约 0.95，冲刺速度 5.612 → 约 6.3
        float damage = SpearCharge.damage(0.95F, 4.0F, 5.612F);
        helper.assertTrue(Math.abs(damage - 7.0F) < 0.35F,
                "冲锋伤害公式不对：期望约 7.0（1 + 0.25×4 攻击力 + 0.95×5.612），实际 " + damage);
        helper.assertTrue(SpearCharge.damage(0.95F, 4.0F, 10.0F) > SpearCharge.damage(0.95F, 4.0F, 5.0F),
                "伤害没有随相对速度增加");
        helper.assertTrue(SpearCharge.canDamage(4.6F) && !SpearCharge.canDamage(4.59F),
                "伤害速度门槛不是 4.6 格/秒");
        helper.assertTrue(SpearCharge.canKnockback(5.1F) && !SpearCharge.canKnockback(5.09F),
                "击退速度门槛不是 5.1 格/秒");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void stagesWalkInOrder(GameTestHelper helper) {
        ToolStack spear = ToolStack.from(SpearTestSupport.ironSpear());
        SpearCharge.Timings timings = SpearCharge.timings(spear);
        helper.assertTrue(timings.activation() > 0 && timings.engaged() > 0 && timings.tired() > 0
                        && timings.disengaged() > 0,
                "时间表有空阶段：" + timings);

        int activation = timings.activation();
        int engagedEnd = activation + timings.engaged();
        int tiredEnd = engagedEnd + timings.tired();
        int disengagedEnd = tiredEnd + timings.disengaged();

        helper.assertTrue(SpearCharge.stageAt(timings, 0) == SpearCharge.Stage.IDLE,
                "0 tick 就进入了冲锋阶段（还没过激活延迟）");
        helper.assertTrue(SpearCharge.stageAt(timings, activation) == SpearCharge.Stage.ENGAGED,
                "过了激活延迟不是 engaged");
        helper.assertTrue(SpearCharge.stageAt(timings, engagedEnd) == SpearCharge.Stage.TIRED,
                "engaged 结束后不是 tired");
        helper.assertTrue(SpearCharge.stageAt(timings, tiredEnd) == SpearCharge.Stage.DISENGAGED,
                "tired 结束后不是 disengaged");
        helper.assertTrue(SpearCharge.stageAt(timings, disengagedEnd) == SpearCharge.Stage.IDLE,
                "三个阶段走完后冲锋没有结束");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void handleMaterialChangesFatigueWindow(GameTestHelper helper) {
        ItemStack longWindow = SpearTestSupport.spear(
                SpearTestSupport.material("tconstruct:iron"),
                SpearTestSupport.material("tconstruct:queens_slime"),
                SpearTestSupport.material("tconstruct:iron"));
        ItemStack shortWindow = SpearTestSupport.spear(
                SpearTestSupport.material("tconstruct:iron"),
                SpearTestSupport.material("tconstruct:electrum"),
                SpearTestSupport.material("tconstruct:iron"));

        SpearCharge.Timings longTimings = SpearCharge.timings(ToolStack.from(longWindow));
        SpearCharge.Timings shortTimings = SpearCharge.timings(ToolStack.from(shortWindow));
        helper.assertTrue(longTimings.engaged() > shortTimings.engaged(),
                "换手柄材料没有改变疲劳窗口：女王史莱姆 " + longTimings + " vs 琥珀金 " + shortTimings);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void chargeHitsFastTargetsAndDismounts(GameTestHelper helper) {
        // GameTest 的结构是紧挨着排的，别的测试的实体就在 4.5 格内。把这场测试抬到 40 格高空，
        // 扫描范围里就只剩自己的马和骑手（否则会打到隔壁测试的僵尸，断言对象就错了）。
        Player player = helper.makeMockPlayer();
        player.setPos(player.getX(), player.getY() + 40.0D, player.getZ());

        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(1, 1, 1));
        Zombie rider = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        horse.moveTo(player.getX() + 3.0D, player.getY(), player.getZ(), 0.0F, 0.0F);
        rider.startRiding(horse, true);
        rider.moveTo(horse.getX(), horse.getY(), horse.getZ(), 0.0F, 0.0F);
        helper.assertTrue(rider.getVehicle() == horse, "测试前提不成立：僵尸没骑上马");

        ItemStack spear = SpearTestSupport.ironSpear();
        player.setItemInHand(InteractionHand.MAIN_HAND, spear);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, rider.getBoundingBox().getCenter());

        // 12.5 格/秒：超过 4.6 伤害门槛、5.1 击退门槛，也超过铁矛头 12 的挑落门槛
        Vec3 look = player.getLookAngle();
        player.setDeltaMovement(look.scale(0.625D));

        float riderHealth = rider.getHealth();
        SpearCharge.Timings timings = SpearCharge.timings(ToolStack.from(spear));
        SpearCharge.tick(player, spear, timings.activation() + 1);

        helper.assertTrue(rider.getHealth() < riderHealth,
                "12.5 格/秒的冲锋没有造成伤害（" + riderHealth + " -> " + rider.getHealth()
                        + "，距离 " + rider.distanceTo(player) + "，瞄准 " + player.getLookAngle() + "）");
        helper.assertTrue(rider.getVehicle() == null,
                "高速冲锋没有把骑手挑下坐骑（铁矛头门槛 "
                        + SpearCharge.dismountSpeed(ToolStack.from(spear)) + " 格/秒）");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void chargeHitsEveryTargetInFront(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.setPos(player.getX(), player.getY() + 40.0D, player.getZ());
        Zombie near = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        Zombie far = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        near.moveTo(player.getX() + 2.5D, player.getY(), player.getZ(), 0.0F, 0.0F);
        far.moveTo(player.getX() + 3.5D, player.getY(), player.getZ(), 0.0F, 0.0F);

        ItemStack spear = SpearTestSupport.ironSpear();
        player.setItemInHand(InteractionHand.MAIN_HAND, spear);
        player.lookAt(EntityAnchorArgument.Anchor.EYES,
                near.getBoundingBox().getCenter().add(far.getBoundingBox().getCenter()).scale(0.5D));
        player.setDeltaMovement(player.getLookAngle().scale(0.625D));

        float nearHealth = near.getHealth();
        float farHealth = far.getHealth();
        SpearCharge.tick(player, spear, SpearCharge.timings(ToolStack.from(spear)).activation() + 1);

        helper.assertTrue(near.getHealth() < nearHealth && far.getHealth() < farHealth,
                "一次冲锋没有同时打到前方两个目标（原版冲锋可以命中多个目标）：近 "
                        + nearHealth + "->" + near.getHealth() + "，远 " + farHealth + "->" + far.getHealth());
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void slowChargeDealsNothing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.setPos(player.getX(), player.getY() + 40.0D, player.getZ());
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        zombie.moveTo(player.getX() + 3.0D, player.getY(), player.getZ(), 0.0F, 0.0F);

        ItemStack spear = SpearTestSupport.ironSpear();
        player.setItemInHand(InteractionHand.MAIN_HAND, spear);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.getBoundingBox().getCenter());
        player.setDeltaMovement(Vec3.ZERO);

        float before = zombie.getHealth();
        SpearCharge.Timings timings = SpearCharge.timings(ToolStack.from(spear));
        SpearCharge.tick(player, spear, timings.activation() + 1);
        helper.assertTrue(zombie.getHealth() == before,
                "站着不动也打出了冲锋伤害（相对速度门槛 " + SpearCharge.DAMAGE_SPEED + " 格/秒）");
        helper.succeed();
    }
}
