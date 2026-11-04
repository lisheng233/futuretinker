package com.lisheng.futuretinker.gametest;

import com.google.common.collect.Multimap;
import com.lisheng.futuretinker.FutureTinker;
import com.lisheng.futuretinker.spear.SpearCombat;
import com.lisheng.futuretinker.spear.SpearItem;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 长矛的「戳刺」规矩：触及 +1.5、2 格内打不到、必须满蓄力、不会暴击。
 *
 * <p>这些是原版长矛和普通武器不同的地方；每条都直接调用事件处理器，不依赖玩家输入，
 * 所以判定是确定的（不是「看起来像」）。</p>
 */
@GameTestHolder(FutureTinker.MOD_ID)
public final class SpearCombatTests {
    private SpearCombatTests() {}

    private static Wolf spawnWolf(Player player, double distance, GameTestHelper helper) {
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
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, SpearTestSupport.ironSpear());
        player.resetAttackStrengthTicker();
        Wolf far = spawnWolf(player, 3.0D, helper);

        AttackEntityEvent notCharged = new AttackEntityEvent(player, far);
        SpearCombat.onAttack(notCharged);
        helper.assertTrue(notCharged.isCanceled(),
                "没满蓄力也戳出去了：原版长矛必须等攻击冷却 100% 才能攻击");

        // 把攻击速度拉高，冷却变成 1 tick → 当前刻度就算「满蓄力」
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        helper.assertTrue(attackSpeed != null, "玩家没有 attack_speed 属性，测试前提不成立");
        attackSpeed.addTransientModifier(new AttributeModifier(
                UUID.fromString("6a1f2d3c-4b5e-4f60-8a71-9c2d3e4f5a6b"), "spear_test_charge", 40.0D,
                AttributeModifier.Operation.ADDITION));

        AttackEntityEvent charged = new AttackEntityEvent(player, far);
        SpearCombat.onAttack(charged);
        helper.assertTrue(!charged.isCanceled(),
                "满蓄力 + 3 格距离，戳刺被错误取消（min range 是 " + SpearCombat.MIN_JAB_RANGE + "）");

        Wolf close = spawnWolf(player, 1.0D, helper);
        AttackEntityEvent tooClose = new AttackEntityEvent(player, close);
        SpearCombat.onAttack(tooClose);
        helper.assertTrue(tooClose.isCanceled(),
                "距离 1 格还能戳：原版长矛 2 格内打不到");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void criticalHitIsDenied(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        Wolf wolf = spawnWolf(player, 3.0D, helper);

        player.setItemInHand(InteractionHand.MAIN_HAND, SpearTestSupport.ironSpear());
        CriticalHitEvent withSpear = new CriticalHitEvent(player, wolf, 1.5F, true);
        SpearCombat.onCritical(withSpear);
        helper.assertTrue(withSpear.getResult() == Event.Result.DENY,
                "拿着长矛仍然暴击：原版长矛不会暴击（应该把事件结果设成 DENY）");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        CriticalHitEvent withFist = new CriticalHitEvent(player, wolf, 1.5F, true);
        SpearCombat.onCritical(withFist);
        helper.assertTrue(withFist.getResult() == Event.Result.DEFAULT,
                "空手时也被我们的暴击拦截改了结果，影响面太大");
        helper.succeed();
    }
}
