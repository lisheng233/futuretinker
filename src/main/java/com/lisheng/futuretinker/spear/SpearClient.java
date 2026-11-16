package com.lisheng.futuretinker.spear;

import com.lisheng.futuretinker.FutureTinker;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfo;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfoLoader;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

/**
 * 长矛的客户端表现：图标/手持模型按材料上色。
 *
 * <p>三张贴图分别是矛头 / 矛杆 / 绑定结，对应材料表下标 0/1/2
 * （{@code item/generated} 的第 i 层 tintindex 就是 i，见 {@code ItemModelGenerator}），
 * 所以一根长矛能同时有三种材料色，和匠魂工具一样。</p>
 *
 * <p>颜色取匠魂材料渲染色（{@code MaterialRenderInfo#vertexColor()}）。事件挂在**模组总线**上——
 * {@code RegisterColorHandlersEvent} 是模组总线事件，挂错总线会静默失效（狼铠那轮踩过）。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class SpearClient {
    /** 手持时用的 32×32 in-hand 模型；GUI/掉落物仍用 16×16 图标模型。 */
    private static final ModelResourceLocation HAND_MODEL =
            new ModelResourceLocation(FutureTinker.id("spear_in_hand"), "inventory");

    private SpearClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(SpearClient::onRegisterColors);
        // 冲锋输入（本地计时 + 发包）
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(SpearClientInput::onClientTick);
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(
                (net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) ->
                        SpearClientState.clear());
    }

    /**
     * 显式用手持模型渲染长矛。不能靠 ItemProperties/overrides 切模型：GUI 的部分
     * 渲染路径也会把玩家实体传进来，导致「手持」predicate 在物品栏里也为真，库存里
     * 显示成又长又细的手持图。这里只在第一/第三人称的渲染路径调用。
     */
    public static void renderHandModel(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
            boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int light) {
        Minecraft minecraft = Minecraft.getInstance();
        BakedModel model = minecraft.getModelManager().getModel(HAND_MODEL);
        if (model == minecraft.getModelManager().getMissingModel()) {
            minecraft.getItemRenderer().renderStatic(entity, stack, context, leftHand, poseStack,
                    buffer, entity.level(), light, OverlayTexture.NO_OVERLAY,
                    entity.getId() + context.ordinal());
            return;
        }
        minecraft.getItemRenderer().render(stack, context, leftHand, poseStack, buffer, light,
                OverlayTexture.NO_OVERLAY, model);
    }

    private static void onRegisterColors(RegisterColorHandlersEvent.Item event) {
        event.register(SpearClient::toolColor, SpearContent.spear());
        event.register((stack, index) -> index == 0 ? partColor(stack) : -1, SpearContent.spearHead());
    }

    /** 长矛：层 0 = 矛头材料，层 1 = 手柄材料，层 2 = 绑定结材料。 */
    private static int toolColor(ItemStack stack, int index) {
        if (index < 0 || index > 2) {
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
