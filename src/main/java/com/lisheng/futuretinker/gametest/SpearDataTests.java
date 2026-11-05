package com.lisheng.futuretinker.gametest;

import com.lisheng.futuretinker.FutureTinker;
import com.lisheng.futuretinker.spear.SpearContent;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.ToolDefinitionLoader;
import slimeknights.tconstruct.library.tools.definition.module.material.ToolPartsHook;
import slimeknights.tconstruct.library.tools.part.IToolPart;

/**
 * 长矛的数据形状：工具定义真的加载了、部件真的是「矛头 + 坚韧手柄 + 坚韧绑定结」、
 * 矛头的部件配方与装配配方都真的存在。
 */
@GameTestHolder(FutureTinker.MOD_ID)
public final class SpearDataTests {
    private static final List<String> EXPECTED_PARTS = List.of(
            "futuretinker:spear_head",
            "tconstruct:tough_handle",
            "tconstruct:tough_binding");

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
        List<String> expected = List.of("tools/building/spear", "tools/parts/builder/spear_head");
        for (String path : expected) {
            var id = FutureTinker.id(path);
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id).isPresent(),
                    "配方没有加载：" + id + "（data/futuretinker/recipes/" + path + ".json）");
        }
        helper.succeed();
    }
}
