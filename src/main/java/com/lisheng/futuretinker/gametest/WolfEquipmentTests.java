package com.lisheng.futuretinker.gametest;

import com.lisheng.futuretinker.FutureTinker;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 狼铠方案（护甲变种）的前提验证：狼的 {@code EquipmentSlot.CHEST} 到底生不生效。
 *
 * <p>PLAN 的模块一建筑在一个判断上：1.20.1 的狼没有护甲槽「只是原版没用」，
 * 而 {@code Mob} 已经实现了 {@code LivingEntity.getArmorSlots()} 的四个槽，
 * 把护甲放进 `CHEST` 就该拿到护甲属性。这个判断错了，整个狼铠方案要重做。
 * 所以这里用**原版铁胸甲当探针**（不需要任何新内容）把它变成一条会失败的断言。</p>
 *
 * <p>注意这条测试只证明「原版装备属性对狼生效」；匠魂自己的护甲 hook 是否对非玩家生效
 * 是另一件事，等有了真的狼铠再单独验。</p>
 */
@GameTestHolder(FutureTinker.MOD_ID)
public final class WolfEquipmentTests {
    private WolfEquipmentTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void chestSlotGivesWolfArmorValue(GameTestHelper helper) {
        Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(1, 1, 1));

        // 空白对照：先证明「不穿就是 0」，否则后面的 >0 可能来自别处。
        helper.assertTrue(wolf.getArmorValue() == 0,
                "空白对照失败：裸狼的护甲值不是 0（是 " + wolf.getArmorValue() + "），这条测试就没有意义了");

        wolf.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));

        // 装备属性是 LivingEntity 在 tick 时收集的，隔几 tick 再断言。
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(wolf.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),
                    "狼的胸甲槽没有保存住放进去的物品：Mob 的护甲槽不是可用的装备栏");
            helper.assertTrue(wolf.getArmorValue() > 0,
                    "狼穿上铁胸甲后护甲值仍是 0：Mob 护甲槽这条路不生效，狼铠「护甲变种」方案需要重做");
            helper.succeed();
        });
    }
}
