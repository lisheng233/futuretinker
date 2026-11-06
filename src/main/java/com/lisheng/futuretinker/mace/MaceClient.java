package com.lisheng.futuretinker.mace;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfo;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfoLoader;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

/** 狼牙棒的客户端上色：棒头层用矛头材料、手柄层用手柄材料；棒头部件图标按材料染。 */
@OnlyIn(Dist.CLIENT)
public final class MaceClient {
    private MaceClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(MaceClient::onRegisterColors);
    }

    private static void onRegisterColors(RegisterColorHandlersEvent.Item event) {
        event.register(MaceClient::toolColor, MaceContent.mace());
        event.register((stack, index) -> index == 0 ? partColor(stack) : -1, MaceContent.maceHead());
    }

    private static int toolColor(ItemStack stack, int index) {
        if (index < 0 || index > 1) {
            return -1;
        }
        MaterialNBT materials = ToolStack.from(stack).getMaterials();
        if (index >= materials.size()) {
            return 0xFFFFFFFF;
        }
        return colorOf(materials.get(index).getId());
    }

    private static int partColor(ItemStack stack) {
        MaterialVariantId material = IMaterialItem.getMaterialFromStack(stack);
        return material == null ? 0xFFFFFFFF : colorOf(material);
    }

    private static int colorOf(MaterialVariantId material) {
        return MaterialRenderInfoLoader.INSTANCE.getRenderInfo(material)
                .map(MaterialRenderInfo::vertexColor)
                .orElse(0xFFFFFFFF);
    }
}
