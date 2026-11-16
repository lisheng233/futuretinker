package com.lisheng.futuretinker.mixin;

import com.lisheng.futuretinker.spear.SpearAnimation;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第三人称的持矛手臂姿势：原版 1.21.11 {@code SpearAnimations.thirdPersonHandUse} 的移植。
 * 把主手抬到向前平举的持矛位置，并按疲劳阶段摇晃。
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void futuretinker$spearArmPose(LivingEntity entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo callback) {
        if (entity instanceof Player player) {
            HumanoidModelAccessor accessor = (HumanoidModelAccessor) (Object) this;
            SpearAnimation.applyArmPose(player, accessor.futuretinker$getRightArm(),
                    accessor.futuretinker$getLeftArm());
        }
    }
}
