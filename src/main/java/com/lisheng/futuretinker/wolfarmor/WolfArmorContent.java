package com.lisheng.futuretinker.wolfarmor;

import com.lisheng.futuretinker.FutureTinker;
import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.RegisterEvent;
import slimeknights.tconstruct.library.tools.definition.ModifiableArmorMaterial;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.item.armor.ModifiableArmorItem;
import slimeknights.tconstruct.library.tools.part.ToolPartItem;
import slimeknights.tconstruct.tools.stats.PlatingMaterialStats;
import slimeknights.tconstruct.tools.stats.StatlessMaterialStats;

/**
 * 狼铠的内容清单：三个部件 + 一件护甲变种。
 *
 * <p>定位（PLAN 模块一）：狼铠是**护甲的变种**，走匠魂护甲线，不是工具线。
 * 它是 {@link ModifiableArmorItem}（`extends ArmorItem implements IModifiableDisplay`），
 * 材料强度取自 {@link PlatingMaterialStats#CHESTPLATE}（胸甲板材），
 * 装配/修复/强化走匠魂既有流程，不另造一套。</p>
 *
 * <p><b>物品为什么不在字段里 new：</b>`Item` 的构造函数会向注册表写一个 intrusive holder
 * （`Item.&lt;init&gt;` → `createIntrusiveHolder`），而模组构造阶段注册表已经冻结，
 * 提前 new 会抛 `Registry is already frozen`（实测踩过）。所以物品只在
 * {@link #registerItems(RegisterEvent)} 的 supplier 里创建，字段在这里接住。</p>
 */
public final class WolfArmorContent {
    /** 护甲材质：一个「槽位 → 护甲定义」的映射表，这里只有胸甲槽。创建它不碰注册表。 */
    public static final ModifiableArmorMaterial ARMOR_MATERIAL = ModifiableArmorMaterial.create(
            FutureTinker.id("wolf_armor"), SoundEvents.ARMOR_EQUIP_LEATHER, ArmorItem.Type.CHESTPLATE);

    public static final String PLATING_ID = "wolf_armor_plating";
    public static final String LINING_ID = "wolf_armor_lining";
    public static final String BINDING_ID = "wolf_armor_binding";
    public static final String ARMOR_ID = "wolf_armor";

    private static Item plating;
    private static Item lining;
    private static Item binding;
    private static Item wolfArmor;

    private WolfArmorContent() {}

    /** 注册阶段调用：物品必须在这里创建（原因见类注释）。 */
    static void registerItems(RegisterEvent event) {
        if (!Registries.ITEM.equals(event.getRegistryKey())) {
            return;
        }
        event.register(Registries.ITEM, FutureTinker.id(PLATING_ID), () -> plating = new ToolPartItem(
                new Item.Properties(), PlatingMaterialStats.CHESTPLATE.getId()));
        // 无统计的部件用 getIdentifier()（MaterialStatType 用的才是 getId()，两者不是同一个方法；
        // 匠魂自己的 maille / binding 部件也是这么写的）。
        event.register(Registries.ITEM, FutureTinker.id(LINING_ID), () -> lining = new ToolPartItem(
                new Item.Properties(), StatlessMaterialStats.MAILLE.getIdentifier()));
        event.register(Registries.ITEM, FutureTinker.id(BINDING_ID), () -> binding = new ToolPartItem(
                new Item.Properties(), StatlessMaterialStats.BINDING.getIdentifier()));
        event.register(Registries.ITEM, FutureTinker.id(ARMOR_ID), () -> wolfArmor = new ModifiableArmorItem(
                ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1)));
    }

    public static Item plating() {
        return require(plating, PLATING_ID);
    }

    public static Item lining() {
        return require(lining, LINING_ID);
    }

    public static Item binding() {
        return require(binding, BINDING_ID);
    }

    public static Item wolfArmor() {
        return require(wolfArmor, ARMOR_ID);
    }

    /** 护甲定义 id（= 材质 id + 槽位名），测试与调试用。 */
    public static String armorDefinitionId() {
        return ARMOR_MATERIAL.getArmorDefinition(ArmorItem.Type.CHESTPLATE).getId().toString();
    }

    /** 这件物品是不是本模组的狼铠。 */
    public static boolean isWolfArmor(ItemStack stack) {
        return !stack.isEmpty() && stack.is(require(wolfArmor, ARMOR_ID));
    }

    /** 是不是匠魂的可改造物品。 */
    public static boolean isModifiable(ItemStack stack) {
        return stack.getItem() instanceof IModifiable;
    }

    private static Item require(Item item, String name) {
        return Objects.requireNonNull(item,
                "Future Tinker: 物品 " + name + " 还没注册（模块被关了，或在注册阶段之前访问了它）");
    }
}
