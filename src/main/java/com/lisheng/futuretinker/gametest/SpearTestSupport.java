package com.lisheng.futuretinker.gametest;

import com.lisheng.futuretinker.spear.SpearContent;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;

/** 测试里造长矛：材料顺序必须与护甲定义一致（矛头 / 手柄 / 弓臂 / 握把）。 */
final class SpearTestSupport {
    private SpearTestSupport() {}

    static IMaterial material(String id) {
        return MaterialRegistry.getMaterial(new MaterialId(id));
    }

    static ItemStack spear(IMaterial head, IMaterial handle, IMaterial limb, IMaterial grip) {
        return ToolBuildHandler.buildItemFromMaterials((IModifiable) SpearContent.spear(),
                MaterialNBT.of(head, handle, limb, grip));
    }

    static ItemStack ironSpear() {
        IMaterial iron = material("tconstruct:iron");
        return spear(iron, iron, iron, iron);
    }
}
