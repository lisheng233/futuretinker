package com.lisheng.futuretinker.wolfarmor;

import com.lisheng.futuretinker.FutureTinker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.EntityModelSet;
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
 * <p>用的是 {@link WolfArmorModel}——一套**逐箱放大**的狼骨骼，所以铠甲是真正包在狼外面一圈，
 * 背、肚子、腿外侧、腿内侧都被盖住；贴图里头部矩形透明，露出狼自己的脸。</p>
 *
 * <p>姿态靠把同一组动画参数喂给铠甲模型复现（{@code prepareMobModel} + {@code setupAnim}），
 * 不是「读本体模型的骨骼再抄」——{@code WolfModel} 的骨骼字段是私有的，抄不到，而动画函数是公开的。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class WolfArmorLayer extends RenderLayer<Wolf, WolfModel<Wolf>> {
    private static final ResourceLocation TEXTURE =
            FutureTinker.id("textures/entity/wolf_armor.png");

    private final WolfModel<Wolf> armorModel;

    public WolfArmorLayer(RenderLayerParent<Wolf, WolfModel<Wolf>> parent, EntityModelSet models) {
        super(parent);
        this.armorModel = WolfArmorModel.bake(models);
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

        WolfModel<Wolf> parent = this.getParentModel();
        armorModel.attackTime = parent.attackTime;
        armorModel.riding = parent.riding;
        armorModel.young = parent.young;
        armorModel.prepareMobModel(wolf, limbSwing, limbSwingAmount, partialTick);
        armorModel.setupAnim(wolf, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);

        int color = WolfArmorClient.materialColor(stack);
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        armorModel.renderToBuffer(poseStack, consumer, packedLight,
                LivingEntityRenderer.getOverlayCoords(wolf, 0.0F), red, green, blue, 1.0F);
    }
}
