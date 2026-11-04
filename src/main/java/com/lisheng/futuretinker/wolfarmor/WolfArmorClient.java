package com.lisheng.futuretinker.wolfarmor;

import com.lisheng.futuretinker.FutureTinker;
import net.minecraft.client.renderer.entity.WolfRenderer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfo;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfoLoader;
import slimeknights.tconstruct.library.materials.definition.MaterialVariant;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 狼铠的客户端表现：图标按材料上色 + 穿在狼身上的渲染层。
 *
 * <p><b>为什么挂在模组总线上：</b>{@link RegisterColorHandlersEvent} 与
 * {@link EntityRenderersEvent.AddLayers} 都是**模组总线**事件（Forge 用
 * {@code ModLoader.postEvent} 发的，见 {@code net.minecraftforge.client.ColorResolverManager}）。
 * 第一版挂到了 {@code MinecraftForge.EVENT_BUS}，监听器一次都没被调用——图标永远是灰度原图，
 * 这也就是「颜色还是没改」的原因。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class WolfArmorClient {
    private WolfArmorClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(WolfArmorClient::onRegisterColors);
        modBus.addListener(WolfArmorClient::onAddLayers);
    }

    /**
     * 狼铠的颜色 = 首个材料（胸甲板材）的匠魂渲染色。
     *
     * <p>{@code MaterialRenderInfo#vertexColor()} 就是材料 JSON 里的 {@code color} 字段，
     * 匠魂渲染部件时用的也是它，所以铠甲和部件不会出现两套颜色。</p>
     */
    public static int materialColor(ItemStack stack) {
        if (!WolfArmorContent.isWolfArmor(stack)) {
            return 0xFFFFFFFF;
        }
        ToolStack tool = ToolStack.from(stack);
        if (tool.getMaterials().isEmpty()) {
            // 创造模式标签页图标、JEI 里的空壳物品：没有材料，别让取色崩掉
            return 0xFFFFFFFF;
        }
        MaterialVariant material = tool.getMaterials().get(0);
        return MaterialRenderInfoLoader.INSTANCE.getRenderInfo(material.getVariant())
                .map(MaterialRenderInfo::vertexColor)
                .orElse(0xFFFFFFFF);
    }

    private static void onRegisterColors(RegisterColorHandlersEvent.Item event) {
        // 贴图是灰度遮罩，颜色全在这里给（item/generated 的 layer0 就是 tintindex 0）
        event.register((stack, index) -> index == 0 ? materialColor(stack) : -1,
                WolfArmorContent.wolfArmor());
        FutureTinker.LOGGER.info("Future Tinker: 狼铠图标颜色处理器已注册（模组总线）");
    }

    private static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        WolfRenderer renderer = event.getRenderer(EntityType.WOLF);
        if (renderer == null) {
            FutureTinker.LOGGER.warn("Future Tinker: 没找到 WolfRenderer，狼铠不会显示在狼身上");
            return;
        }
        renderer.addLayer(new WolfArmorLayer(renderer));
        FutureTinker.LOGGER.info("Future Tinker: 狼铠渲染层已挂到 WolfRenderer");
    }
}
