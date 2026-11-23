package com.lisheng.futuretinker.gametest;

import com.lisheng.futuretinker.FutureTinker;
import com.lisheng.futuretinker.mace.MaceContent;
import com.lisheng.futuretinker.spear.SpearContent;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.recipe.casting.ICastingContainer;
import slimeknights.tconstruct.library.recipe.casting.ICastingRecipe;
import slimeknights.tconstruct.library.recipe.casting.material.CompositeCastingRecipe;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

/** 金 / 沙子 / 红沙三种铸模都要能从流体浇出矛头和棒头。 */
@GameTestHolder(FutureTinker.MOD_ID)
public final class CastCastingTests {
    private CastCastingTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void allThreeCastsPourParts(GameTestHelper helper) {
        checkPart(helper, "spear_head", SpearContent.spearHead(), SpearContent.casts(), 2);
        checkPart(helper, "mace_head", MaceContent.maceHead(), MaceContent.casts(), 3);
        helper.succeed();
    }

    private static void checkPart(GameTestHelper helper, String part, Item resultItem,
                                  List<Item> casts, int itemCost) {
        RecipeManager recipes = helper.getLevel().getRecipeManager();
        ICastingRecipe gold = recipe(helper, recipes, part, "gold_cast");
        ICastingRecipe sand = recipe(helper, recipes, part, "sand_cast");
        ICastingRecipe composite = recipe(helper, recipes, part, "composite");
        helper.assertTrue(composite instanceof CompositeCastingRecipe,
                part + " 的复合浇筑配方类型不对：" + composite.getClass());

        ICastingContainer goldContainer = container(casts.get(0));
        helper.assertTrue(gold.matches(goldContainer, helper.getLevel()),
                part + " 的金铸模没有匹配浇筑配方（检查 tconstruct:casts/multi_use/" + part + " 标签）");
        helper.assertTrue(!gold.isConsumed(), part + " 的金铸模应该是可反复使用的");
        helper.assertTrue(gold.getFluidAmount(goldContainer) == itemCost * 90,
                part + " 的浇筑流体量不对：" + gold.getFluidAmount(goldContainer)
                        + "，期望 " + itemCost * 90);
        assertMaterial(helper, gold.assemble(goldContainer, helper.getLevel().registryAccess()),
                resultItem, part + " 金铸模浇筑结果");

        for (Item sandCast : List.of(casts.get(1), casts.get(2))) {
            ICastingContainer sandContainer = container(sandCast);
            helper.assertTrue(sand.matches(sandContainer, helper.getLevel()),
                    part + " 的沙子/红沙铸模没有匹配浇筑配方（检查 tconstruct:casts/single_use/"
                            + part + " 标签）");
            helper.assertTrue(sand.isConsumed(), part + " 的沙子/红沙铸模应该是一次性的");
            assertMaterial(helper, sand.assemble(sandContainer, helper.getLevel().registryAccess()),
                    resultItem, part + " 沙子铸模浇筑结果");
        }

        assertTag(helper, casts.get(0), "tconstruct:casts/multi_use/" + part);
        assertTag(helper, casts.get(1), "tconstruct:casts/single_use/" + part);
        assertTag(helper, casts.get(2), "tconstruct:casts/single_use/" + part);
        assertTag(helper, casts.get(0), "tconstruct:casts/gold");
        assertTag(helper, casts.get(1), "tconstruct:casts/sand");
        assertTag(helper, casts.get(2), "tconstruct:casts/red_sand");
        assertTag(helper, casts.get(0), "tconstruct:casts/multi_use");
        assertTag(helper, casts.get(1), "tconstruct:casts/single_use");
    }

    private static ICastingRecipe recipe(GameTestHelper helper, RecipeManager recipes,
                                         String part, String suffix) {
        ResourceLocation id = FutureTinker.id("tools/parts/casting/" + part + "_" + suffix);
        var recipe = recipes.byKey(id).orElse(null);
        helper.assertTrue(recipe instanceof ICastingRecipe,
                "浇筑配方没加载或类型不对：" + id + "（实际 " + recipe + "）");
        return (ICastingRecipe) recipe;
    }

    private static ICastingContainer container(Item cast) {
        return new ICastingContainer() {
            @Override
            public ItemStack getStack() {
                return new ItemStack(cast);
            }

            @Override
            public Fluid getFluid() {
                return TinkerFluids.moltenIron.get();
            }
        };
    }

    private static void assertMaterial(GameTestHelper helper, ItemStack stack, Item expected,
                                       String what) {
        helper.assertTrue(stack.is(expected),
                what + " 不是 " + expected + "，实际 " + stack);
        helper.assertTrue(IMaterialItem.getMaterialFromStack(stack).getId().toString()
                        .equals("tconstruct:iron"),
                what + " 没有带上铁材料：" + IMaterialItem.getMaterialFromStack(stack));
    }

    private static void assertTag(GameTestHelper helper, Item item, String tag) {
        var key = ItemTags.create(new ResourceLocation(tag));
        helper.assertTrue(new ItemStack(item).is(key),
                item + " 不在 " + tag + " 里");
    }
}