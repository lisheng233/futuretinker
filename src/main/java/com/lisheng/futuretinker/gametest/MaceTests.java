package com.lisheng.futuretinker.gametest;

import com.google.common.collect.Multimap;
import com.lisheng.futuretinker.FutureTinker;
import com.lisheng.futuretinker.mace.MaceCombat;
import com.lisheng.futuretinker.mace.MaceContent;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.ToolDefinitionLoader;
import slimeknights.tconstruct.library.tools.definition.module.material.ToolPartsHook;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.part.IToolPart;

/** 狼牙棒：部件顺序、形态分类、钩扯流血、重手柄移速。 */
@GameTestHolder(FutureTinker.MOD_ID)
public final class MaceTests {
    private MaceTests() {}

    private static ItemStack mace(String head, String handle) {
        return ToolBuildHandler.buildItemFromMaterials((IModifiable) MaceContent.mace(),
                MaterialNBT.of(MaterialRegistry.getMaterial(new MaterialId(head)),
                        MaterialRegistry.getMaterial(new MaterialId(handle))));
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void definitionHasTwoParts(GameTestHelper helper) {
        ToolDefinition definition = ToolDefinitionLoader.getInstance().getRegisteredToolDefinitions().stream()
                .filter(candidate -> candidate.getId().equals(MaceContent.DEFINITION.getId()))
                .findFirst().orElse(null);
        helper.assertTrue(definition != null, "狼牙棒的工具定义没加载");
        List<String> names = ToolPartsHook.parts(definition).stream()
                .map(part -> ForgeRegistries.ITEMS.getKey(((IToolPart) part).asItem()).toString())
                .toList();
        helper.assertTrue(names.equals(List.of("futuretinker:mace_head", "tconstruct:tough_handle")),
                "狼牙棒的部件应该是 棒头 + 坚韧手柄，实际 " + names);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void formComesFromHeadStats(GameTestHelper helper) {
        ToolStack tool = ToolStack.from(mace("tconstruct:iron", "tconstruct:wood"));
        // 铁没有声明硬度/韧性 → 两根默认值 1.0 → NEUTRAL
        helper.assertTrue(MaceCombat.form(tool) == MaceCombat.Form.NEUTRAL,
                "没有硬度/韧性数据时应为 NEUTRAL，实际 " + MaceCombat.form(tool));
        helper.assertTrue(MaceCombat.sweepAngle(MaceCombat.Form.HARD) < MaceCombat.sweepAngle(MaceCombat.Form.LIGHT),
                "横扫角度应该：高硬度更窄、轻质更宽");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void movingTargetGetsHookBleed(GameTestHelper helper) {
        Player player = helper.makeMockPlayer();
        player.setPos(player.getX(), player.getY() + 40.0D, player.getZ());
        player.setItemInHand(InteractionHand.MAIN_HAND, mace("tconstruct:iron", "tconstruct:wood"));

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new net.minecraft.core.BlockPos(1, 1, 1));
        zombie.moveTo(player.getX() + 3.0D, player.getY(), player.getZ(), 0.0F, 0.0F);
        zombie.setDeltaMovement(new Vec3(0.5D, 0.0D, 0.0D));

        MaceCombat.onAttack(new AttackEntityEvent(player, zombie));
        helper.assertTrue(zombie.hasEffect(MaceContent.bleed()),
                "目标在移动却没有挂上钩扯流血（读的应该是目标速度）");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void heavyHandleSlowsTheHolder(GameTestHelper helper) {
        // 狗屎一样重的木手柄 vs 轻的骨头手柄：只比较相对关系，不锁具体数值
        Multimap<Attribute, AttributeModifier> heavy = mace("tconstruct:iron", "tconstruct:wood")
                .getAttributeModifiers(EquipmentSlot.MAINHAND);
        Multimap<Attribute, AttributeModifier> light = mace("tconstruct:iron", "tconstruct:bone")
                .getAttributeModifiers(EquipmentSlot.MAINHAND);
        double heavySum = heavy.get(Attributes.MOVEMENT_SPEED).stream().mapToDouble(AttributeModifier::getAmount).sum();
        double lightSum = light.get(Attributes.MOVEMENT_SPEED).stream().mapToDouble(AttributeModifier::getAmount).sum();
        helper.assertTrue(heavySum < 0.0D, "重手柄没有移速惩罚：" + heavySum);
        helper.assertTrue(heavySum <= lightSum, "重手柄的移速惩罚应不轻于轻手柄");
        helper.succeed();
    }
}
