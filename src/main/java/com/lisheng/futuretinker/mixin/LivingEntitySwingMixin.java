package com.lisheng.futuretinker.mixin;

import com.lisheng.futuretinker.spear.SpearContent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.stat.ToolStats;

/**
 * 原版 1.21.11 的长矛有 {@code swing_animation} 组件，挥击时长跟着武器攻速走
 * （铁的 0.95 秒 ≈ 19 tick）。1.20.1 固定 6 tick，戳刺会快得看不清，而且
 * 第三人称的动作曲线是按 19 tick 的进度算的，所以这里把矛的挥击时长改成
 * {@code 20 / 攻速}。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {
    @Inject(method = "getCurrentSwingDuration", at = @At("HEAD"), cancellable = true)
    private void futuretinker$spearSwingDuration(CallbackInfoReturnable<Integer> callback) {
        if ((Object) this instanceof Player player) {
            ItemStack stack = player.getMainHandItem();
            if (!SpearContent.isSpear(stack)) {
                return;
            }
            Object speed = ToolStack.from(stack).getStats().get(ToolStats.ATTACK_SPEED);
            float attackSpeed = speed instanceof Number number ? number.floatValue() : 1.0F;
            callback.setReturnValue(Math.max(1, Math.round(20.0F / Math.max(attackSpeed, 0.1F))));
        }
    }
}
