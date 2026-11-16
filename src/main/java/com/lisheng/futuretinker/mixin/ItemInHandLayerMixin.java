package com.lisheng.futuretinker.mixin;

import com.lisheng.futuretinker.spear.SpearAnimation;
import com.lisheng.futuretinker.spear.SpearClient;
import com.lisheng.futuretinker.spear.SpearClientState;
import com.lisheng.futuretinker.spear.SpearContent;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第三人称的持矛动作：在物品渲染前（hand transform 之后）套用原版 1.21.11 的
 * {@code SpearAnimations.thirdPersonUseItem / thirdPersonAttackItem}。
 *
 * <p>作用对象是 {@code Player}，状态来自 {@link SpearClientState}（本地玩家直接读输入，
 * 其他玩家读服务端广播）。</p>
 */
@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin {
    @Inject(method = "renderArmWithItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem("
                            + "Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;"
                            + "Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void futuretinker$spearThirdPerson(LivingEntity entity, ItemStack stack,
            ItemDisplayContext context, HumanoidArm arm, PoseStack poseStack,
            MultiBufferSource buffer, int light, CallbackInfo callback) {
        if (!(entity instanceof Player player) || !SpearContent.isSpear(stack)) {
            return;
        }
        float partialTicks = Minecraft.getInstance().getFrameTime();
        if (SpearClientState.isCharging(player)) {
            SpearAnimation.thirdPersonUse(poseStack, stack,
                    SpearClientState.chargeTicks(player, partialTicks), arm);
        } else {
            SpearAnimation.thirdPersonAttack(poseStack, player.getAttackAnim(partialTicks), arm);
        }
    }

    /** 第三人称也显式用 32×32 in-hand 模型，GUI 仍用图标模型。 */
    @Redirect(method = "renderArmWithItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem("
                            + "Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;"
                            + "Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;"
                            + "Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void futuretinker$renderSpearHand(ItemInHandRenderer renderer, LivingEntity entity, ItemStack stack,
            ItemDisplayContext context, boolean leftHand, PoseStack poseStack,
            MultiBufferSource buffer, int light) {
        if (entity instanceof Player && SpearContent.isSpear(stack)) {
            SpearClient.renderHandModel(entity, stack, context, leftHand, poseStack, buffer, light);
        } else {
            renderer.renderItem(entity, stack, context, leftHand, poseStack, buffer, light);
        }
    }
}
