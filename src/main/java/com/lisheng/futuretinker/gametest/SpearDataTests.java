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
 * 长矛的数据形状：工具定义真的加载了、部件真的是 4 个、装配配方真的存在。
 *
 * <p>长矛的部件不是随便挑的：它照抄匠魂 javelin 的四个部件，模型贴图也是匠魂自己的
 * （{@code tconstruct:item/tool/javelin/*}）。这条测试保证「定义里写的部件」和
 * 「模型按 index 认的部件」是同一套顺序——顺序错了不会报错，只会渲染成一把错位的矛。</p>
 */
@GameTestHolder(FutureTinker.MOD_ID)
public final class SpearDataTests {
    private static final List<String> EXPECTED_PARTS = List.of(
            "tconstruct:small_blade",
            "tconstruct:tool_handle",
            "tconstruct:bow_limb",
            "tconstruct:bow_grip");

    private SpearDataTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void definitionHasFourOrderedParts(GameTestHelper helper) {
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
                        + "（顺序决定材料下标：0 矛头 / 1 手柄 / 2 弓臂 / 3 握把）");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void buildingRecipeIsLoaded(GameTestHelper helper) {
        var id = FutureTinker.id("tools/building/spear");
        helper.assertTrue(helper.getLevel().getRecipeManager().byKey(id).isPresent(),
                "配方没有加载：" + id + "（data/futuretinker/recipes/tools/building/spear.json）");
        helper.succeed();
    }
}
