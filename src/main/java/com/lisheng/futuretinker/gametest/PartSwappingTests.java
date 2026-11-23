package com.lisheng.futuretinker.gametest;

import com.lisheng.futuretinker.FutureTinker;
import com.lisheng.futuretinker.mace.MaceContent;
import com.lisheng.futuretinker.spear.SpearContent;
import com.lisheng.futuretinker.wolfarmor.WolfArmorContent;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import slimeknights.tconstruct.fluids.TinkerFluids;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.recipe.casting.ICastingContainer;
import slimeknights.tconstruct.library.recipe.casting.material.PartSwapCastingRecipe;
import slimeknights.tconstruct.library.recipe.material.MaterialRecipe;
import slimeknights.tconstruct.library.recipe.tinkerstation.ITinkerStationContainer;
import slimeknights.tconstruct.tables.recipe.TinkerStationPartSwapping;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

/** 换部件 + 创造栏材料罗列。 */
@GameTestHolder(FutureTinker.MOD_ID)
public final class PartSwappingTests {
    private static final TagKey<Item> MULTIPART =
            ItemTags.create(new ResourceLocation("tconstruct:modifiable/multipart"));

    private PartSwappingTests() {}

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void toolsCanSwapParts(GameTestHelper helper) {
        assertIn(helper, SpearContent.spear(), "矛");
        assertIn(helper, MaceContent.mace(), "狼牙棒");
        assertIn(helper, WolfArmorContent.wolfArmor(), "狼铠");

        var recipe = helper.getLevel().getRecipeManager()
                .byKey(FutureTinker.id("tools/armor/wolf_armor_swapping")).orElse(null);
        helper.assertTrue(recipe instanceof PartSwapCastingRecipe,
                "狼铠的浇筑换板配方没加载：" + recipe);

        var stationRecipe = helper.getLevel().getRecipeManager()
                .byKey(new ResourceLocation("tconstruct", "tables/tinker_station_part_swapping"))
                .orElse(null);
        helper.assertTrue(stationRecipe instanceof TinkerStationPartSwapping,
                "匠魂的工匠砧换部件配方没加载：" + stationRecipe);
        TinkerStationPartSwapping swapping = (TinkerStationPartSwapping) stationRecipe;

        ItemStack spear = SpearTestSupport.spear(
                MaterialRegistry.getMaterial(new MaterialId("tconstruct:iron")),
                MaterialRegistry.getMaterial(new MaterialId("tconstruct:iron")),
                MaterialRegistry.getMaterial(new MaterialId("tconstruct:iron")));
        helper.assertTrue(swapping.matches(new Station(spear, SpearContent.spearHead()), helper.getLevel()),
                "矛在工匠砧上换不了矛头部件");

        ItemStack mace = ToolBuildHandler.buildItemFromMaterials((IModifiable) MaceContent.mace(),
                MaterialNBT.of(MaterialRegistry.getMaterial(new MaterialId("tconstruct:iron")),
                        MaterialRegistry.getMaterial(new MaterialId("tconstruct:iron"))));
        helper.assertTrue(swapping.matches(new Station(mace, MaceContent.maceHead()), helper.getLevel()),
                "狼牙棒在工匠砧上换不了棒头部件");

        ItemStack armor = ToolBuildHandler.buildItemFromMaterials(
                (IModifiable) WolfArmorContent.wolfArmor(),
                MaterialNBT.of(MaterialRegistry.getMaterial(new MaterialId("tconstruct:iron"))));
        ICastingContainer container = new ICastingContainer() {
            @Override
            public ItemStack getStack() {
                return armor;
            }

            @Override
            public Fluid getFluid() {
                return TinkerFluids.moltenCopper.get();
            }
        };
        helper.assertTrue(((PartSwapCastingRecipe) recipe).matches(container, helper.getLevel()),
                "狼铠 + 熔融铜没有匹配换板配方（index 0 / 胸甲板材 / 6 点材料）");

        Item plating = ForgeRegistries.ITEMS.getValue(new ResourceLocation("tconstruct", "chestplate_plating"));
        helper.assertTrue(plating != null, "找不到 tconstruct:chestplate_plating");
        helper.assertTrue(swapping.matches(new Station(armor, plating), helper.getLevel()),
                "狼铠在工匠砧上换不了胸甲板材");
        helper.succeed();
    }

    @PrefixGameTestTemplate(false)
    @GameTest(templateNamespace = FutureTinker.MOD_ID, template = "empty3x3x3", required = true)
    public static void partsListEveryMaterialVariant(GameTestHelper helper) {
        assertVariants(helper, SpearContent.spearHead(), "矛头");
        assertVariants(helper, MaceContent.maceHead(), "棒头");
        helper.succeed();
    }

    private static final class Station implements ITinkerStationContainer {
        private final ItemStack tool;
        private final ItemStack part;

        private Station(ItemStack tool, Item part) {
            this.tool = tool;
            this.part = new ItemStack(part);
        }

        @Override
        public ItemStack getTinkerableStack() {
            return tool;
        }

        @Override
        public ItemStack getInput(int index) {
            return index == 0 ? part : ItemStack.EMPTY;
        }

        @Override
        public int getInputCount() {
            return 1;
        }

        @Override
        public MaterialRecipe getInputMaterial(int index) {
            return null;
        }
    }

    private static void assertIn(GameTestHelper helper, Item item, String name) {
        helper.assertTrue(new ItemStack(item).is(MULTIPART),
                name + " 不在 tconstruct:modifiable/multipart 里，工匠砧不会允许换部件");
    }

    private static void assertVariants(GameTestHelper helper, Item item, String name) {
        List<ItemStack> variants = new ArrayList<>();
        ((IMaterialItem) item).addVariants(variants::add, "");
        helper.assertTrue(variants.size() > 5,
                name + " 的材料罗列版太少：" + variants.size());
        helper.assertTrue(variants.stream().allMatch(stack -> stack.is(item)
                        && !IMaterialItem.getMaterialFromStack(stack).getId().toString()
                        .equals("tconstruct:unknown")),
                name + " 的材料罗列版里有缺失材料的堆叠");
    }
}