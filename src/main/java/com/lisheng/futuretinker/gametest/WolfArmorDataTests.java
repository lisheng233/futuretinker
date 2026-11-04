package com.lisheng.futuretinker.gametest;

import com.lisheng.futuretinker.FutureTinker;
import com.lisheng.futuretinker.wolfarmor.WolfArmorContent;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.definition.ToolDefinitionLoader;
import slimeknights.tconstruct.library.tools.definition.module.material.ToolPartsHook;
import slimeknights.tconstruct.library.tools.part.IToolPart;

/**
 * 配方是否真的被数据包加载了，以及护甲定义里到底要几个部件。
 *
 * <p>为什么值得单独一条：配方是纯 JSON，写错文件名/写错 recipe type/引用不存在的物品时，
 * 游戏只会「默默没有这条配方」，没有任何报错。这条测试让「狼铠能造出来」变成一个会失败的断言。</p>
 *
 * <p>第二条断言的是「贵」这件事本身：护甲定义要求 4 个部件，其中两块是匠魂的大板
 * （{@code tconstruct:large_plate}，每块 4 点材料，比板材还贵）。这条如果只写在 JSON 里，
 * 少写一个部件也不会有人发现——大板只是被静默忽略，成品照样能做，只是便宜了。</p>
 */
@GameTestHolder(FutureTinker.MOD_ID)
public final class WolfArmorDataTests {
    private WolfArmorDataTests() {}

    private static final List<String> EXPECTED_RECIPES = List.of("tools/building/wolf_armor");
    private static final ResourceLocation LARGE_PLATE = new ResourceLocation("tconstruct", "large_plate");

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void recipesAreLoaded(GameTestHelper helper) {
        var recipeManager = helper.getLevel().getRecipeManager();
        for (String path : EXPECTED_RECIPES) {
            var id = FutureTinker.id(path);
            helper.assertTrue(recipeManager.byKey(id).isPresent(),
                    "配方没有加载：" + id + "（文件应位于 data/futuretinker/recipes/" + path + ".json，" +
                            "recipe type 必须存在且引用的物品必须已注册）");
        }
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void wolfArmorRequiresTwoLargePlates(GameTestHelper helper) {
        String definitionId = WolfArmorContent.armorDefinitionId();
        ToolDefinition definition = ToolDefinitionLoader.getInstance().getRegisteredToolDefinitions().stream()
                .filter(candidate -> candidate.getId().toString().equals(definitionId))
                .findFirst()
                .orElse(null);
        helper.assertTrue(definition != null,
                "找不到狼铠的护甲定义：" + definitionId + "（data/futuretinker/tinkering/tool_definitions/ 下的文件没加载？）");

        List<IToolPart> parts = ToolPartsHook.parts(definition);
        long largePlates = parts.stream()
                .filter(part -> LARGE_PLATE.equals(ForgeRegistries.ITEMS.getKey(part.asItem())))
                .count();
        helper.assertTrue(parts.size() == 4,
                "狼铠应该由 4 个部件拼成（胸甲板材 + maille + 两块大板），实际是 " + parts.size() + " 个：" + names(parts));
        helper.assertTrue(largePlates == 2,
                "狼铠应该需要两块 " + LARGE_PLATE + "，实际 " + largePlates + " 块：" + names(parts));
        helper.succeed();
    }

    private static List<String> names(List<IToolPart> parts) {
        return parts.stream().map(part -> String.valueOf(ForgeRegistries.ITEMS.getKey(part.asItem()))).toList();
    }
}
