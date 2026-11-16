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
import net.minecraft.world.level.block.Blocks;
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
        // 原版公式：攻击力 + floor(相对速度 × 倍率)；铁矛倍率约 0.95，冲刺 5.612 格/秒。
        float damage = SpearCharge.chargeDamage(2.0F, 5.612F, 0.95F);
        helper.assertTrue(Math.abs(damage - 7.0F) < 0.01F,
                "冲锋伤害公式不对：期望 7.0（2.0 + floor(0.95×5.612)），实际 " + damage);
        helper.assertTrue(SpearCharge.chargeDamage(2.0F, 10.0F, 0.95F)
                        > SpearCharge.chargeDamage(2.0F, 5.0F, 0.95F),
                "伤害没有随相对速度增加");
        helper.assertTrue(SpearCharge.DAMAGE_SPEED == 4.6F,
                "伤害速度门槛不是 4.6 格/秒");
        helper.assertTrue(SpearCharge.KNOCKBACK_SPEED == 5.1F,
                "击退速度门槛不是 5.1 格/秒");
        helper.assertTrue(SpearCharge.MIN_REACH == 2.0D && SpearCharge.MAX_REACH == 4.5D,
                "普通模式射程不是 2.0~4.5 格");
        helper.assertTrue(SpearCharge.MAX_CREATIVE_REACH == 6.5D,
                "创造模式最大射程不是 6.5 格");
        helper.assertTrue(SpearCharge.HITBOX_MARGIN == 0.125D,
                "命中箱放大不是 0.125");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void windowsWalkInOrder(GameTestHelper helper) {
        ToolStack spear = ToolStack.from(SpearTestSupport.ironSpear());
        SpearCharge.Timings timings = SpearCharge.timings(spear);
        helper.assertTrue(timings.activation() > 0 && timings.engaged() > 0 && timings.tired() > 0
                        && timings.disengaged() > 0,
                "时间表有空窗口：" + timings);

        int engagedEnd = timings.activation() + timings.engaged();
        int tiredEnd = engagedEnd + timings.tired();
        int damageEnd = tiredEnd + timings.disengaged();
        helper.assertTrue(engagedEnd < tiredEnd && tiredEnd < damageEnd,
                "窗口没有按 挑落 → 击退 → 伤害 的顺序排列：" + timings);
        helper.assertTrue(timings.total() == damageEnd,
                "总时长不等于伤害窗口结束：" + timings);
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

    /**
     * GameTest 把结构抬到 y+40 后，上方是**石头**；原版冲锋的方块裁剪从眼睛开始，
     * 会把石头当成贴脸的墙。测试里先挖出一个沿 +X 的空气房，再验证真实的射线判定。
     */
    private static void carveTestRoom(GameTestHelper helper, Player player) {
        BlockPos eye = BlockPos.containing(player.getEyePosition());
        for (int dx = -2; dx <= 7; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    helper.getLevel().setBlockAndUpdate(eye.offset(dx, dy, dz),
                            Blocks.AIR.defaultBlockState());
                }
            }
        }
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void chargeHitsFastTargetsAndDismounts(GameTestHelper helper) {
        // GameTest 的结构是紧挨着排的，别的测试的实体就在 4.5 格内。把这场测试抬到 40 格高空，
        // 扫描范围里就只剩自己的马和骑手（否则会打到隔壁测试的僵尸，断言对象就错了）。
        Player player = helper.makeMockPlayer();
        player.setPos(player.getX(), player.getY() + 40.0D, player.getZ());
        carveTestRoom(helper, player);

        ItemStack spear = SpearTestSupport.ironSpear();
        player.setItemInHand(InteractionHand.MAIN_HAND, spear);
        player.lookAt(EntityAnchorArgument.Anchor.EYES,
                new Vec3(player.getX() + 10.0D, player.getEyeY(), player.getZ()));
        SpearCharge.Timings timings = SpearCharge.timings(ToolStack.from(spear));
        // 原版接触冷却会在目标进入射线时立刻登记，所以先用一个**没有目标**的 tick 记下起点；
        // 速度按位置差算（服务端玩家的 deltaMovement 不可靠），下一步再前进 0.625 格。
        SpearCharge.tick(player, spear, timings.activation());

        Horse horse = helper.spawn(EntityType.HORSE, new BlockPos(1, 1, 1));
        Zombie rider = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        horse.moveTo(player.getX() + 3.0D, player.getY(), player.getZ(), 0.0F, 0.0F);
        rider.startRiding(horse, true);
        rider.moveTo(horse.getX(), horse.getY(), horse.getZ(), 0.0F, 0.0F);
        // 空气房没有地板，靶子会自由落体；速度判定必须只由玩家这一步提供
        horse.setNoGravity(true);
        rider.setNoGravity(true);
        horse.setDeltaMovement(Vec3.ZERO);
        rider.setDeltaMovement(Vec3.ZERO);
        helper.assertTrue(rider.getVehicle() == horse, "测试前提不成立：僵尸没骑上马");

        player.lookAt(EntityAnchorArgument.Anchor.EYES, rider.getBoundingBox().getCenter());
        float riderHealth = rider.getHealth();
        // 前进 0.625 格 = 12.5 格/秒。超过 4.6 伤害、5.1 击退，也超过铁矛头 8 的挑落门槛。
        Vec3 step = player.getLookAngle().scale(0.625D);
        player.setPos(player.getX() + step.x, player.getY() + step.y, player.getZ() + step.z);
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
        carveTestRoom(helper, player);

        ItemStack spear = SpearTestSupport.ironSpear();
        player.setItemInHand(InteractionHand.MAIN_HAND, spear);
        player.lookAt(EntityAnchorArgument.Anchor.EYES,
                new Vec3(player.getX() + 10.0D, player.getEyeY(), player.getZ()));
        SpearCharge.Timings timings = SpearCharge.timings(ToolStack.from(spear));
        // 先用无目标 tick 预热位置差速度，避免接触冷却把下一 tick 的目标提前登记掉。
        SpearCharge.tick(player, spear, timings.activation());

        Zombie near = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        Zombie far = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        near.moveTo(player.getX() + 3.0D, player.getY(), player.getZ(), 0.0F, 0.0F);
        far.moveTo(player.getX() + 4.0D, player.getY(), player.getZ(), 0.0F, 0.0F);
        near.setNoGravity(true);
        far.setNoGravity(true);
        near.setDeltaMovement(Vec3.ZERO);
        far.setDeltaMovement(Vec3.ZERO);
        player.lookAt(EntityAnchorArgument.Anchor.EYES,
                near.getBoundingBox().getCenter().add(far.getBoundingBox().getCenter()).scale(0.5D));

        // 前进 0.3 格 = 6 格/秒：近目标 3.0→2.7 格，远目标 4.0→3.7 格，都在 2.0~4.5 的射线里。
        Vec3 step = player.getLookAngle().scale(0.3D);
        player.setPos(player.getX() + step.x, player.getY() + step.y, player.getZ() + step.z);

        float nearHealth = near.getHealth();
        float farHealth = far.getHealth();
        SpearCharge.tick(player, spear, timings.activation() + 1);

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
        carveTestRoom(helper, player);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 1, 1));
        zombie.moveTo(player.getX() + 3.0D, player.getY(), player.getZ(), 0.0F, 0.0F);
        zombie.setNoGravity(true);
        zombie.setDeltaMovement(Vec3.ZERO);

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
