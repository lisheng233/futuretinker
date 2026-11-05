package com.lisheng.futuretinker.spear;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfo;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfoLoader;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

/**
 * 长矛部件的客户端表现：矛头图标按材料上色。
 *
 * <p>图标是灰度遮罩，颜色取匠魂材料渲染色（{@code MaterialRenderInfo#vertexColor()}），
 * 和匠魂自己的部件是同一套色。事件挂在**模组总线**上——{@code RegisterColorHandlersEvent}
 * 是模组总线事件，挂错总线会静默失效（狼铠那轮踩过）。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class SpearClient {
    private SpearClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(SpearClient::onRegisterColors);
    }

    private static void onRegisterColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, index) -> index == 0 ? partColor(stack) : -1, SpearContent.spearHead());
    }

    private static int partColor(ItemStack stack) {
        MaterialVariantId material = IMaterialItem.getMaterialFromStack(stack);
        if (material == null) {
            return 0xFFFFFFFF;
        }
        return MaterialRenderInfoLoader.INSTANCE.getRenderInfo(material)
                .map(MaterialRenderInfo::vertexColor)
                .orElse(0xFFFFFFFF);
    }
}
