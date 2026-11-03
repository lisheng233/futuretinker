package com.lisheng.futuretinker.gametest;

import com.lisheng.futuretinker.FutureTinker;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 配方是否真的被数据包加载了。
 *
 * <p>为什么值得单独一条：配方是纯 JSON，写错文件名/写错 recipe type/引用不存在的物品时，
 * 游戏只会「默默没有这条配方」，没有任何报错。这条测试让「狼铠能造出来」变成一个会失败的断言。</p>
 */
@GameTestHolder(FutureTinker.MOD_ID)
public final class WolfArmorDataTests {
    private WolfArmorDataTests() {}

    private static final List<String> EXPECTED_RECIPES = List.of(
            "tools/building/wolf_armor",
            "tools/parts/builder/wolf_armor_plating",
            "tools/parts/builder/wolf_armor_lining",
            "tools/parts/builder/wolf_armor_binding");

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
}
