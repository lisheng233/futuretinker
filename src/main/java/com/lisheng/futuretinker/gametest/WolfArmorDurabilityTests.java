package com.lisheng.futuretinker.gametest;

import com.lisheng.futuretinker.FutureTinker;
import com.lisheng.futuretinker.wolfarmor.WolfArmorContent;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;

/**
 * 狼铠受击行为：挨打要掉**匠魂耐久**。
 *
 * <p>用户实测：狼穿狼铠被打，耐久不掉，只有原版护甲减免。匠魂护甲是「护甲点数 + 每次受击扣耐久」
 * 两条都算，玩家身上由匠魂处理，狼不在那条路径上，所以这条断言就是那个补丁的证据。</p>
 */
@GameTestHolder(FutureTinker.MOD_ID)
public final class WolfArmorDurabilityTests {
    private WolfArmorDurabilityTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void armorLosesDurabilityWhenWolfIsHurt(GameTestHelper helper) {
        Wolf wolf = helper.spawn(EntityType.WOLF, new BlockPos(1, 1, 1));
        ItemStack armor = ironWolfArmor();
        helper.assertTrue(armor.getMaxDamage() > 0, "狼铠没有耐久上限，测试前提不成立");
        wolf.setItemSlot(EquipmentSlot.CHEST, armor.copy());
        int before = wolf.getItemBySlot(EquipmentSlot.CHEST).getDamageValue();

        Player attacker = helper.makeMockPlayer();
        wolf.hurt(wolf.damageSources().playerAttack(attacker), 8.0F);

        int after = wolf.getItemBySlot(EquipmentSlot.CHEST).getDamageValue();
        helper.assertTrue(after > before,
                "狼被打了但狼铠耐久没变（before=" + before + ", after=" + after + "）："
                        + "说明它只是当原版护甲点数用，没走匠魂的耐久损毁");
        helper.succeed();
    }

    private static ItemStack ironWolfArmor() {
        MaterialNBT materials = MaterialNBT.of(MaterialRegistry.getMaterial(new MaterialId("tconstruct:iron")));
        return ToolBuildHandler.buildItemFromMaterials((IModifiable) WolfArmorContent.wolfArmor(), materials);
    }
}
