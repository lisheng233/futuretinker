package com.lisheng.futuretinker.wolfarmor;

import com.lisheng.futuretinker.FutureTinker;
import net.minecraft.client.model.WolfModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 狼铠的模型：**同一套狼骨骼，每个箱子各向外放大一圈**。
 *
 * <p>为什么不是「把狼的模型整体 scale 一下」：缩放是绕一个点做的，绕脚底放大会让上半身往
 * 世界里侧缩（模型 y 轴朝下，见 {@code LivingEntityRenderer} 的 {@code scale(-1,-1,1)}），
 * 背就露出来了；绕躯干中心放大会让两条腿的内侧面朝*远离*身体的方向移动，腿内侧照样露狼皮。
 * 只有逐箱 inflate（{@code CubeDeformation}）才是真正的「各面向外长」，一个方向都不漏。</p>
 *
 * <p>动画不用自己写：这是一个 {@link WolfModel}，渲染时把同一组动画参数（limbSwing 等）
 * 喂给它的 {@code prepareMobModel}/{@code setupAnim}，姿态与本体逐帧一致——腿、尾巴、坐下、
 * 幼狼缩放都自动对上。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class WolfArmorModel {
    public static final ModelLayerLocation LAYER =
            new ModelLayerLocation(FutureTinker.id("wolf_armor"), "main");

    /** 躯干/尾巴：+0.75 像素，铠甲明显鼓出本体一圈，但还没到「胖成球」。 */
    private static final CubeDeformation BODY = new CubeDeformation(0.75F);
    /**
     * 腿：两条腿之间只有 1 像素空隙，各放大 0.5 就会让两条腿的甲面在缝里互相穿插；
     * 0.4 既能盖住狼腿，又留 0.2 的缝。
     */
    private static final CubeDeformation LEG = new CubeDeformation(0.4F);

    private WolfArmorModel() {}

    public static WolfModel<Wolf> bake(EntityModelSet models) {
        return new WolfModel<>(models.bakeLayer(LAYER));
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // 头部不放大：贴图里头部矩形是全透明的（露出真脸），放大没有意义
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(),
                PartPose.offset(-1.0F, 13.5F, -7.0F));
        head.addOrReplaceChild("real_head", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.0F, -3.0F, -2.0F, 6.0F, 6.0F, 4.0F)
                        .texOffs(16, 14).addBox(-2.0F, -5.0F, 0.0F, 2.0F, 2.0F, 1.0F)
                        .texOffs(16, 14).addBox(2.0F, -5.0F, 0.0F, 2.0F, 2.0F, 1.0F)
                        .texOffs(0, 10).addBox(-0.5F, -0.001F, -5.0F, 3.0F, 3.0F, 4.0F),
                PartPose.ZERO);

        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(18, 14).addBox(-3.0F, -2.0F, -3.0F, 6.0F, 9.0F, 6.0F, BODY),
                PartPose.offsetAndRotation(0.0F, 14.0F, 2.0F, (float) Math.PI / 2.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("upper_body", CubeListBuilder.create()
                        .texOffs(21, 0).addBox(-3.0F, -3.0F, -3.0F, 8.0F, 6.0F, 7.0F, BODY),
                PartPose.offsetAndRotation(-1.0F, 14.0F, -3.0F, (float) Math.PI / 2.0F, 0.0F, 0.0F));

        CubeListBuilder leg = CubeListBuilder.create()
                .texOffs(0, 18).addBox(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, LEG);
        root.addOrReplaceChild("right_hind_leg", leg, PartPose.offset(-2.5F, 16.0F, 7.0F));
        root.addOrReplaceChild("left_hind_leg", leg, PartPose.offset(0.5F, 16.0F, 7.0F));
        root.addOrReplaceChild("right_front_leg", leg, PartPose.offset(-2.5F, 16.0F, -4.0F));
        root.addOrReplaceChild("left_front_leg", leg, PartPose.offset(0.5F, 16.0F, -4.0F));

        PartDefinition tail = root.addOrReplaceChild("tail", CubeListBuilder.create(),
                PartPose.offsetAndRotation(-1.0F, 12.0F, 8.0F, (float) Math.PI / 5.0F, 0.0F, 0.0F));
        tail.addOrReplaceChild("real_tail", CubeListBuilder.create()
                        .texOffs(9, 18).addBox(0.0F, 0.0F, -1.0F, 2.0F, 8.0F, 2.0F, LEG),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 32);
    }
}
