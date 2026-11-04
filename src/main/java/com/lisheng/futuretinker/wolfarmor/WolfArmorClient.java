package com.lisheng.futuretinker.wolfarmor;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.common.MinecraftForge;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfoLoader;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 客户端：狼铠图标按**材料颜色**上色（部件用的是匠魂材质管线，狼铠是普通物品模型，
 * 所以用原版的物品颜色钩子补上同一件事）。
 *
 * <p>颜色来源是匠魂自己的材料渲染信息（{@code MaterialRenderInfo#vertexColor()}），
 * 和匠魂部件/工具取的是同一个色，不会出现「部件是铁色、铠甲却是灰色」。
 * 贴图是灰度遮罩（{@code textures/item/wolf_armor.png}），颜色完全由这里决定。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class WolfArmorClient {
    private WolfArmorClient() {}

    public static void register() {
        MinecraftForge.EVENT_BUS.addListener(WolfArmorClient::onRegisterColors);
    }

    private static void onRegisterColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, index) -> {
            if (index != 0) {
                return -1;
            }
            ToolStack tool = ToolStack.from(stack);
            MaterialVariant material = tool.getMaterials().get(0);
            return MaterialRenderInfoLoader.INSTANCE.getRenderInfo(material.getVariant())
                    .map(info -> info.vertexColor())
                    .orElse(0xFFFFFFFF);
        }, WolfArmorContent.wolfArmor());
    }
}
