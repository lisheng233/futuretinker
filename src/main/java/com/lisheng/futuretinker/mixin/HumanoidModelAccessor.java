package com.lisheng.futuretinker.mixin;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link HumanoidModel#rightArm} / {@link HumanoidModel#leftArm} 是 public 字段，
 * 直接用 @Shadow 字段在 refmap 里不会生成映射（生产 jar 里字段是 SRG 名 f_102811_ / f_102812_），
 * 手臂动作会静默失效。用 @Accessor 让 refmap 生成方法映射。
 */
@Mixin(HumanoidModel.class)
public interface HumanoidModelAccessor {
    @Accessor("rightArm")
    ModelPart futuretinker$getRightArm();

    @Accessor("leftArm")
    ModelPart futuretinker$getLeftArm();
}
