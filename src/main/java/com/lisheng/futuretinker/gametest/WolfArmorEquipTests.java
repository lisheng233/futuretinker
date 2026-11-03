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
 * 狼铠（M2 第一刀）的判定：一件用匠魂材料造出来的狼铠，穿在狼身上要真的顶用。
 *
 * <p>查三件事，失败模式各不相同：</p>
 * <ol>
 *   <li>护甲定义被加载了——耐久 &gt; 0，否则说明 {@code wolf_armor_chestplate.json} 没被读到；</li>
 *   <li>护甲值生效——{@code getArmorValue() > 0}，否则「护甲变种」这条路在狼身上不通；</li>
 *   <li>减伤真的发生——同一种伤害打在裸狼/穿甲狼身上，后者掉血更少。
 *       这一条同时回答 PLAN 里的待实测项：匠魂的护甲统计有没有进入实际伤害计算。</li>
 * </ol>
 */
@GameTestHolder(FutureTinker.MOD_ID)
public final class WolfArmorEquipTests {
    private WolfArmorEquipTests() {}

    private static final float TEST_DAMAGE = 8.0F;

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void armoredWolfTakesLessDamage(GameTestHelper helper) {
        ItemStack armor = ironWolfArmor();
        helper.assertTrue(armor.is(WolfArmorContent.wolfArmor()),
                "构造出来的不是狼铠：" + armor);
        helper.assertTrue(armor.getMaxDamage() > 0,
                "狼铠耐久为 0：护甲定义没被加载（检查 data/futuretinker/tinkering/tool_definitions/wolf_armor_chestplate.json）");

        Wolf naked = helper.spawn(EntityType.WOLF, new BlockPos(1, 1, 1));
        Wolf armored = helper.spawn(EntityType.WOLF, new BlockPos(2, 1, 1));
        armored.setItemSlot(EquipmentSlot.CHEST, armor.copy());

        // ★ 伤害类型很关键：minecraft:generic 在 bypasses_armor 标签里，用它会永远测不出护甲
        //   （第一版就是这么写的，结果「护甲值 5 但伤害没变」）。玩家攻击类伤害才走护甲减免。
        Player attacker = helper.makeMockPlayer();

        // 装备属性是在 LivingEntity tick 时收集的，隔几 tick 再断言。
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(armored.getArmorValue() > 0,
                    "狼穿上匠魂狼铠后护甲值仍是 0：" + armor + "（定义 id " + WolfArmorContent.armorDefinitionId() + "）");

            float nakedLost = hurtFor(naked, attacker);
            float armoredLost = hurtFor(armored, attacker);

            // 空白对照：裸狼应该吃满伤害，这样「穿甲更少」才有意义。
            helper.assertTrue(Math.abs(nakedLost - TEST_DAMAGE) < 0.001F,
                    "裸狼没有吃满 " + TEST_DAMAGE + " 点伤害（实际 " + nakedLost + "）：对照组本身就不干净");

            // 阈值写成 0.05 是有意的：原版护甲公式 f4 = clamp(armor - damage/(2+tough/4), armor*0.2, 20)，
            // 5 点护甲打 8 点伤害时 f4 = 1，减免只有 1/25 = 4%（8.0 → 7.68）。
            // 别把阈值改成 0.5：那不是「护甲没生效」，是公式本来就这么算。
            helper.assertTrue(armoredLost < nakedLost - 0.05F,
                    "穿甲狼没有更抗打：裸狼掉血 " + nakedLost + "，穿甲狼掉血 " + armoredLost
                            + "（护甲值 " + armored.getArmorValue() + "）");
            helper.succeed();
        });
    }

    /** 用匠魂材料（铁）把狼铠造出来：走匠魂自己的构造路径，不是手搓 NBT。 */
    private static ItemStack ironWolfArmor() {
        MaterialNBT materials = MaterialNBT.of(MaterialRegistry.getMaterial(new MaterialId("tconstruct:iron")));
        return ToolBuildHandler.buildItemFromMaterials((IModifiable) WolfArmorContent.wolfArmor(), materials);
    }

    private static float hurtFor(Wolf wolf, Player attacker) {
        float before = wolf.getHealth();
        wolf.hurt(wolf.damageSources().playerAttack(attacker), TEST_DAMAGE);
        return before - wolf.getHealth();
    }
}
