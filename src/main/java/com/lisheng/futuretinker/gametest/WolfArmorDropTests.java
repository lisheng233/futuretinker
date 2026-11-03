package com.lisheng.futuretinker.gametest;

import com.lisheng.futuretinker.FutureTinker;
import com.lisheng.futuretinker.wolfarmor.WolfArmorContent;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;

/**
 * 狼死亡时铠甲的去向（策略：死亡必掉，且只掉一份）。
 *
 * <p>为什么要测：`Mob.armorDropChances` 默认只有 8.5%，而匠魂自己的
 * `ModifierEvents.onLivingDrops` 也会遍历装备槽掉落可改造物品——两条路径叠在一起，
 * 很容易变成「有时不掉」或「掉两份」。这条测试把结果钉成 1 份。</p>
 */
@GameTestHolder(FutureTinker.MOD_ID)
public final class WolfArmorDropTests {
    private WolfArmorDropTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void armorDropsExactlyOnceOnDeath(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Wolf wolf = helper.spawn(EntityType.WOLF, pos);
        wolf.setTame(true);
        wolf.setItemSlot(EquipmentSlot.CHEST, ironWolfArmor());

        wolf.kill();

        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(
                ItemEntity.class, new AABB(helper.absolutePos(pos)).inflate(4.0D),
                item -> WolfArmorContent.isWolfArmor(item.getItem()));

        helper.assertTrue(!drops.isEmpty(),
                "狼死了但狼铠没有掉落：辛苦造出来的护甲消失了（当前没人在掉落阶段把它吐出来）");
        helper.assertTrue(drops.size() == 1,
                "狼铠掉了 " + drops.size() + " 份：掉落路径重复（Mob 的掉落概率 + 匠魂的装备掉落）");
        helper.succeed();
    }

    private static ItemStack ironWolfArmor() {
        MaterialNBT materials = MaterialNBT.of(MaterialRegistry.getMaterial(new MaterialId("tconstruct:iron")));
        return ToolBuildHandler.buildItemFromMaterials((IModifiable) WolfArmorContent.wolfArmor(), materials);
    }
}
