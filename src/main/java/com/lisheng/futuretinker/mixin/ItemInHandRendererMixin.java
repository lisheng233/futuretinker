package com.lisheng.futuretinker.mixin;

import com.lisheng.futuretinker.spear.SpearAnimation;
import com.lisheng.futuretinker.spear.SpearClientInput;
import com.lisheng.futuretinker.spear.SpearContent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第一人称的持矛动作。数学来自原版 1.21.11 的 {@code SpearAnimations}，见
 * {@link SpearAnimation}。
 *
 * <p>为什么必须 mixin：1.20.1 的物品「使用动画」只有 {@code UseAnim} 那几种硬编码分支，
 * 没有给模组留接口；物品模型的 {@code display} 变换只能整体挪动，做不出过程动画。</p>
 *
 * <p>注入点：{@code renderArmWithItem} 的 HEAD，只接管【主手拿矛】且【正在压低或正在挥击】的帧，
 * 其余情况原样交回原版。目标方法用 SRG 名（{@code m_109371_}），生产环境就是这套名字。</p>
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Shadow
    protected abstract void applyItemArmTransform(PoseStack poseStack, HumanoidArm arm, float equipProgress);

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void futuretinker$spearPose(AbstractClientPlayer player, float partialTicks, float pitch,
                                        InteractionHand hand, float swingProgress, ItemStack stack,
                                        float equipProgress, PoseStack poseStack, MultiBufferSource buffer,
                                        int light, CallbackInfo callback) {
        if (hand != InteractionHand.MAIN_HAND || !SpearContent.isSpear(stack)) {
            return;
        }
        boolean charging = SpearClientInput.isCharging();
        if (!charging && swingProgress <= 0.0F) {
            return;
        }

        HumanoidArm arm = player.getMainArm();
        poseStack.pushPose();
        if (charging) {
            // 原版 1.21.11 的持矛使用动画以自己的基准平移开始；1.20.1 的
            // applyItemArmTransform 还会再减 equipProgress * 0.6，照抄会把整套动作压低 0.6 格。
            int side = arm == HumanoidArm.RIGHT ? 1 : -1;
            poseStack.translate(side * 0.56F, -0.52F, -0.72F);
            SpearAnimation.firstPersonUse(poseStack,
                    SpearClientInput.chargeTicks() + partialTicks, arm, stack);
        } else {
            this.applyItemArmTransform(poseStack, arm, equipProgress);
            SpearAnimation.firstPersonAttack(swingProgress, poseStack, arm);
        }
        boolean right = arm == HumanoidArm.RIGHT;
        com.lisheng.futuretinker.spear.SpearClient.renderHandModel(player, stack,
                right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                !right, poseStack, buffer, light);
        poseStack.popPose();
        callback.cancel();
    }

    /**
     * 按住右键时原版每 4 tick 会重新调用一次 {@code use}，每次 {@code itemUsed} 都把持物高度
     * 归零再弹回，表现就是矛一直上下抖。矛的压低/戳刺全部由上面的 mixin 画，取消这个重置。
     */
    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
    private void futuretinker$spearDoesNotBob(InteractionHand hand, CallbackInfo callback) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (hand == InteractionHand.MAIN_HAND && player != null
                && SpearContent.isSpear(player.getMainHandItem())) {
            callback.cancel();
        }
    }
}
