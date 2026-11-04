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

/**
 * 狼铠的内容清单：**只有一件护甲**。
 *
 * <p>部件与铸模不自己做——复用匠魂现成的部件（胸甲板材 + maille），护甲定义见
 * {@code data/futuretinker/tinkering/tool_definitions/wolf_armor_chestplate.json}。
 * 这样也避开了「插件部件贴图缺 {@code <基础图>_<材料>} 变体」的坑
 * （匠魂只为自己的贴图生成那些变体，插件部件用 material 加载器必然紫黑）。</p>
 *
 * <p><b>物品为什么不在字段里 new：</b>`Item` 构造函数会向注册表写 intrusive holder，
 * 模组构造阶段注册表已冻结，只能在 {@link RegisterEvent} 的 supplier 里创建。</p>
 */
public final class WolfArmorContent {
    public static final ModifiableArmorMaterial ARMOR_MATERIAL = ModifiableArmorMaterial.create(
            FutureTinker.id("wolf_armor"), SoundEvents.ARMOR_EQUIP_LEATHER, ArmorItem.Type.CHESTPLATE);

    public static final String ARMOR_ID = "wolf_armor";

    private static Item wolfArmor;

    private WolfArmorContent() {}

    static void registerItems(RegisterEvent event) {
        if (!Registries.ITEM.equals(event.getRegistryKey())) {
            return;
        }
        event.register(Registries.ITEM, FutureTinker.id(ARMOR_ID), () -> wolfArmor = new WolfArmorItem(
                ARMOR_MATERIAL, ArmorItem.Type.CHESTPLATE, new Item.Properties().stacksTo(1)));
    }

    public static Item wolfArmor() {
        return Objects.requireNonNull(wolfArmor,
                "Future Tinker: 狼铠物品还没注册（模块被关了，或在注册阶段之前访问了它）");
    }

    /** 护甲定义 id（= 材质 id + 槽位名），测试与调试用。 */
    public static String armorDefinitionId() {
        return ARMOR_MATERIAL.getArmorDefinition(ArmorItem.Type.CHESTPLATE).getId().toString();
    }

    public static boolean isWolfArmor(ItemStack stack) {
        return !stack.isEmpty() && stack.is(wolfArmor());
    }

    public static boolean isModifiable(ItemStack stack) {
        return stack.getItem() instanceof IModifiable;
    }
}
