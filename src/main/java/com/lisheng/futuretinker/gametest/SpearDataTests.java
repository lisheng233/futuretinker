package com.lisheng.futuretinker.gametest;

import com.lisheng.futuretinker.FutureTinker;
import com.lisheng.futuretinker.spear.ChargeMaterialStats;
import com.lisheng.futuretinker.spear.SpearCharge;
import com.lisheng.futuretinker.spear.SpearContent;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.ToolDefinitionLoader;
import slimeknights.tconstruct.library.tools.definition.module.material.ToolPartsHook;
import slimeknights.tconstruct.library.tools.part.IToolPart;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.part.PartCastItem;

/**
 * 长矛的数据形状：工具定义真的加载了、部件真的是「矛头 + 坚韧手柄 + 坚韧绑定结」、
 * 矛头的部件配方 / 装配配方 / 三种铸模配方都在、矛在匠魂百科的「小工具」里。
 */
@GameTestHolder(FutureTinker.MOD_ID)
public final class SpearDataTests {
    private static final List<String> EXPECTED_PARTS = List.of(
            "futuretinker:spear_head",
            "tconstruct:tough_handle",
            "tconstruct:tough_binding");

    private static final List<String> EXPECTED_RECIPES = List.of(
            "tools/building/spear",
            "tools/parts/builder/spear_head",
            "smeltery/casts/gold/spear_head",
            "smeltery/casts/sand/molding/spear_head",
            "smeltery/casts/sand/builder_block/spear_head",
            "smeltery/casts/sand/builder_cast/spear_head",
            "smeltery/casts/red_sand/molding/spear_head",
            "smeltery/casts/red_sand/builder_block/spear_head",
            "smeltery/casts/red_sand/builder_cast/spear_head");

    private static final List<String> EXPECTED_CASTS = List.of(
            "futuretinker:spear_head_cast",
            "futuretinker:spear_head_sand_cast",
            "futuretinker:spear_head_red_sand_cast");

    private SpearDataTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void definitionHasThreeOrderedParts(GameTestHelper helper) {
        ToolDefinition definition = ToolDefinitionLoader.getInstance().getRegisteredToolDefinitions().stream()
                .filter(candidate -> candidate.getId().equals(SpearContent.DEFINITION.getId()))
                .findFirst()
                .orElse(null);
        helper.assertTrue(definition != null,
                "找不到长矛的工具定义 " + SpearContent.DEFINITION.getId()
                        + "（data/futuretinker/tinkering/tool_definitions/spear.json 没加载？）");

        List<String> names = ToolPartsHook.parts(definition).stream()
                .map(part -> ForgeRegistries.ITEMS.getKey(((IToolPart) part).asItem()).toString())
                .toList();
        helper.assertTrue(names.equals(EXPECTED_PARTS),
                "长矛的部件顺序不对。期望 " + EXPECTED_PARTS + "，实际 " + names
                        + "（顺序决定材料下标：0 矛头 / 1 手柄 / 2 绑定结）");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void recipesAreLoaded(GameTestHelper helper) {
        for (String path : EXPECTED_RECIPES) {
            ResourceLocation id = FutureTinker.id(path);
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id).isPresent(),
                    "配方没有加载：" + id + "（data/futuretinker/recipes/" + path + ".json）");
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void castsAreRegistered(GameTestHelper helper) {
        for (String id : EXPECTED_CASTS) {
            var item = BuiltInRegistries.ITEM.getOptional(new ResourceLocation(id)).orElse(null);
            helper.assertTrue(item instanceof PartCastItem,
                    "矛头铸模没注册： " + id + "（期望 PartCastItem，实际 " + item + "）");
        }
        helper.succeed();
    }

    /**
     * 匠魂百科的工具页是按标签自动生成的：{@code tconstruct:modifiable/small} 里的每个物品
     * 都会在「小工具」章节里生成一页（语言文件 {@code tools/small/<ns>_<path>.json} 提供文案）。
     * 所以「书里有没有矛」这件事，判据就是这条标签 + 那个语言文件。
     */

    /**
     * 矛头的「冲刺系数」是一条真的材料统计：材料可以声明 {@code futuretinker:charge}，
     * 没声明的（其它模组添加的材料）拿到默认值 1.0，矛照样能用。
     */
    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void chargeCoefficientIsAMaterialStat(GameTestHelper helper) {
        // 匠魂的 getMaterialStats 对「材料没声明这条统计」的情况返回 empty（默认值在别处兜底），
        // 所以这里直接断言：铁（没数据）走默认系数，倍率仍在原版区间。
        var stats = MaterialRegistry.getInstance().getMaterialStats(new MaterialId("tconstruct:iron"),
                ChargeMaterialStats.ID);
        float value = stats.map(stat -> ((ChargeMaterialStats) stat).charge())
                .orElse(ChargeMaterialStats.DEFAULT_CHARGE);
        helper.assertTrue(Math.abs(value - ChargeMaterialStats.DEFAULT_CHARGE) < 1.0E-4F,
                "没声明这条统计的材料应该走默认值 " + ChargeMaterialStats.DEFAULT_CHARGE + "，实际 " + value);
        helper.assertTrue("futuretinker:charge".equals(ChargeMaterialStats.TYPE.getId().toString())
                        || ChargeMaterialStats.ID.toString().equals("futuretinker:charge"),
                "冲刺系数的材料统计 id 不对： " + ChargeMaterialStats.ID);

        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, SpearTestSupport.ironSpear());
        float multiplier = SpearCharge.multiplier(ToolStack.from(player.getMainHandItem()));
        helper.assertTrue(multiplier >= 0.7F && multiplier <= 1.2F,
                "默认冲刺系数下倍率应落在原版区间 0.7~1.2，实际 " + multiplier);
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void spearIsListedInTheEncyclopedia(GameTestHelper helper) {
        var tag = ItemTags.create(new ResourceLocation("tconstruct:modifiable/small"));
        helper.assertTrue(SpearContent.spear().builtInRegistryHolder().is(tag),
                "矛不在 tconstruct:modifiable/small 里，匠魂百科的「小工具」章节就不会有它");
        String page = "assets/tconstruct/book/encyclopedia/en_us/tools/small/futuretinker_spear.json";
        helper.assertTrue(SpearDataTests.class.getClassLoader().getResource(page) != null,
                "百科页面的文案文件不存在：" + page
                        + "（en_us / zh_cn 各一份，文件名是 <命名空间>_<物品路径>.json）");
        helper.succeed();
    }
}
