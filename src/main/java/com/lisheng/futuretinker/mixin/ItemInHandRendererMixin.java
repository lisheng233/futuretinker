package com.lisheng.futuretinker.mixin;

import com.lisheng.futuretinker.spear.SpearClientInput;
import com.lisheng.futuretinker.spear.SpearContent;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
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
 * 第一人称的持矛姿势：压低（冲锋）与前刺（戳刺）。
 *
 * <p>为什么必须 mixin：1.20.1 的物品「使用动画」只有 {@link net.minecraft.world.item.UseAnim}
 * 那几种硬编码分支（三叉戟那套就是 {@code SPEAR}），没有给模组留接口；物品模型的
 * {@code display} 变换只能整体挪动，做不出「压低 + 旋转」的过程，也无法在攻击挥击里插一帧前刺。</p>
 *
 * <p>注入点：{@code ItemInHandRenderer#renderArmWithItem} 的 HEAD，只在【拿着我们的矛】且
 * 【正在压低或正在挥击】时接管，其余情况原样交回原版。目标方法用的是 SRG 名
 * （{@code m_109371_}），生产环境里 Forge 就是这套名字。</p>
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Shadow
    protected abstract void m_109382_(PoseStack poseStack, HumanoidArm arm, float equipProgress);

    @Shadow
    public abstract void m_269530_(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                   boolean leftHand, PoseStack poseStack, MultiBufferSource buffer, int light);

    @Inject(method = "m_109371_", at = @At("HEAD"), cancellable = true)
    private void futuretinker$spearPose(AbstractClientPlayer player, float partialTicks, float pitch,
                                        InteractionHand hand, float swingProgress, ItemStack stack,
                                        float equipProgress, PoseStack poseStack, MultiBufferSource buffer,
                                        int light, CallbackInfo callback) {
        if (hand != InteractionHand.MAIN_HAND || !SpearContent.isSpear(stack)) {
            return;
        }
        boolean charging = SpearClientInput.isCharging();
        float thrust = player.getAttackAnim(partialTicks);
        if (!charging && thrust <= 0.0F) {
            return;
        }

        poseStack.pushPose();
        this.m_109382_(poseStack, player.getMainArm(), equipProgress);
        if (charging) {
            // 原版节奏：先转（6 tick 翻平），再下压（10 tick），压完就定住不动
            float ticks = SpearClientInput.chargeTicks() + partialTicks;
            float rotate = Math.min(1.0F, Math.max(0.0F, ticks / 6.0F));
            float lower = Math.min(1.0F, Math.max(0.0F, (ticks - 6.0F) / 10.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F * rotate));
            poseStack.mulPose(Axis.XP.rotationDegrees(-25.0F * lower));
            poseStack.translate(0.0F, -0.08F * lower, 0.0F);
        } else {
            // 戳刺：向前一顶（不再整体旋转，避免矛头扎进近平面看不见）
            poseStack.translate(0.0F, 0.0F, 0.45F * thrust);
            poseStack.mulPose(Axis.XP.rotationDegrees(-10.0F * thrust));
        }
        this.m_269530_(player, stack, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, false, poseStack, buffer, light);
        poseStack.popPose();
        callback.cancel();
    }
}
