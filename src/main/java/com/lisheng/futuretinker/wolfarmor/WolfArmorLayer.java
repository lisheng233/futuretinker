package com.lisheng.futuretinker.wolfarmor;

import com.lisheng.futuretinker.FutureTinker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 给穿上狼铠的狼画一层铠甲。
 *
 * <p>做法是**复用狼自己的模型和 UV**，用铠甲贴图把整只模型再画一遍：铠甲像素不透明（盖住狼本体），
 * 头部那几块矩形在贴图里是全透明的（露出真脸）。好处是不需要另写一套模型——腿、尾巴、坐下、
 * 幼狼缩放全部自动跟着狼本体的动画走。</p>
 *
 * <p>没有走 {@code EntityModel} 范式的铠甲模型，是因为 1.20.1 原版没有狼铠模型可抄，
 * 而自建模型的骨骼动画要和 {@code WolfModel} 逐帧同步，错一帧就是穿模。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class WolfArmorLayer extends RenderLayer<Wolf, WolfModel<Wolf>> {
    private static final ResourceLocation TEXTURE =
            FutureTinker.id("textures/entity/wolf_armor.png");
    /** 抬一点，避免和狼本体完全共面（共面只能靠深度相等值取胜，太脆）。 */
    private static final float INFLATE = 1.01F;

    public WolfArmorLayer(RenderLayerParent<Wolf, WolfModel<Wolf>> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, Wolf wolf,
                       float limbSwing, float limbSwingAmount, float partialTick,
                       float ageInTicks, float netHeadYaw, float headPitch) {
        ItemStack stack = wolf.getItemBySlot(EquipmentSlot.CHEST);
        if (!WolfArmorContent.isWolfArmor(stack) || wolf.isInvisible()) {
            // 隐身时连铠甲一起消失（原版狼的项圈层也是这么做的，否则会出现「浮空铠甲」）
            return;
        }
        int color = WolfArmorClient.materialColor(stack);
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        poseStack.pushPose();
        poseStack.scale(INFLATE, INFLATE, INFLATE);
        this.getParentModel().renderToBuffer(poseStack, consumer, packedLight,
                LivingEntityRenderer.getOverlayCoords(wolf, 0.0F), red, green, blue, 1.0F);
        poseStack.popPose();
    }
}
