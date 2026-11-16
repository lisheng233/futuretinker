package com.lisheng.futuretinker.mace;

import com.lisheng.futuretinker.FutureTinker;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegisterEvent;
import slimeknights.tconstruct.library.tools.definition.ToolDefinition;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;
import slimeknights.tconstruct.library.tools.part.PartCastItem;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import slimeknights.tconstruct.tools.stats.HeadMaterialStats;

/** 狼牙棒的物品与 effect：棒头（新部件）+ 三种铸模 + 坚韧手柄，外加一个「流血」效果。 */
public final class MaceContent {
    public static final String ID = "mace";
    public static final String HEAD_ID = "mace_head";
    public static final String BLEED_ID = "bleed";

    public static final ToolDefinition DEFINITION = ToolDefinition.create(FutureTinker.id(ID));

    private static Item mace;
    private static Item maceHead;
    private static Item maceHeadCast;
    private static Item maceHeadSandCast;
    private static Item maceHeadRedSandCast;
    private static MobEffect bleed;

    private MaceContent() {}

    static void registerItems(RegisterEvent event) {
        if (!Registries.ITEM.equals(event.getRegistryKey())) {
            return;
        }
        event.register(Registries.ITEM, FutureTinker.id(ID),
                () -> mace = new MaceItem(new Item.Properties().stacksTo(1), DEFINITION));
        event.register(Registries.ITEM, FutureTinker.id(HEAD_ID),
                () -> maceHead = new MaceHeadItem(new Item.Properties()));
        // 三种铸模，走匠魂的 PartCastItem（金可反复用，沙子/红沙一次性）
        event.register(Registries.ITEM, FutureTinker.id(HEAD_ID + "_cast"),
                () -> maceHeadCast = new PartCastItem(new Item.Properties(), MaceContent::maceHeadPart));
        event.register(Registries.ITEM, FutureTinker.id(HEAD_ID + "_sand_cast"),
                () -> maceHeadSandCast = new PartCastItem(new Item.Properties(), MaceContent::maceHeadPart));
        event.register(Registries.ITEM, FutureTinker.id(HEAD_ID + "_red_sand_cast"),
                () -> maceHeadRedSandCast = new PartCastItem(new Item.Properties(), MaceContent::maceHeadPart));
    }

    static void registerEffects(RegisterEvent event) {
        if (!Registries.MOB_EFFECT.equals(event.getRegistryKey())) {
            return;
        }
        event.register(Registries.MOB_EFFECT, FutureTinker.id(BLEED_ID), () -> bleed = new BleedEffect());
    }

    public static Item mace() {
        return Objects.requireNonNull(mace, "Future Tinker: 狼牙棒还没注册");
    }

    public static Item maceHead() {
        return Objects.requireNonNull(maceHead, "Future Tinker: 棒头还没注册");
    }

    public static IMaterialItem maceHeadPart() {
        return (IMaterialItem) maceHead();
    }

    public static List<Item> casts() {
        return List.of(Objects.requireNonNull(maceHeadCast), Objects.requireNonNull(maceHeadSandCast),
                Objects.requireNonNull(maceHeadRedSandCast));
    }

    public static MobEffect bleed() {
        return Objects.requireNonNull(bleed, "Future Tinker: 流血效果还没注册");
    }

    public static boolean isMace(ItemStack stack) {
        return !stack.isEmpty() && stack.is(mace());
    }

    public static List<Item> items() {
        return List.of(mace(), maceHead(), Objects.requireNonNull(maceHeadCast),
                Objects.requireNonNull(maceHeadSandCast), Objects.requireNonNull(maceHeadRedSandCast));
    }
}
