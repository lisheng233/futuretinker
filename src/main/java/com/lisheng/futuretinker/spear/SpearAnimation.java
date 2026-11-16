package com.lisheng.futuretinker.spear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.joml.Quaternionf;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;

/**
 * 原版 1.21.11 {@code SpearAnimations} 的移植：压低、疲劳摇晃、放下、回抬与戳刺。
 *
 * <p>原版的动画由 {@code KineticWeapon} 组件的几个阶段阈值驱动；这里换成
 * {@link SpearCharge.Timings} 的四段（激活 / engaged / tired / disengaged）。
 * 公式与常数照抄原版，见 1.21.11 的 {@code SpearAnimations.UseParams} /
 * {@code firstPersonUse} / {@code firstPersonAttack} / {@code thirdPersonUseItem} /
 * {@code thirdPersonAttackItem}。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class SpearAnimation {
    private static final float DEG = (float) (Math.PI / 180.0);
    private static final float PI = (float) Math.PI;

    private SpearAnimation() {}

    /** 原版 UseParams：压低/抬起、摇晃、放下、回抬各自的 0~1 进度。 */
    public static final class Params {
        public final float raise;
        public final float raiseStart;
        public final float raiseMiddle;
        public final float raiseEnd;
        public final float sway;
        public final float lower;
        public final float raiseBack;
        public final float swayIntensity;
        public final float swaySlow;
        public final float swayFast;

        private Params(float raise, float raiseStart, float raiseMiddle, float raiseEnd,
                float sway, float lower, float raiseBack, float swayIntensity,
                float swaySlow, float swayFast) {
            this.raise = raise;
            this.raiseStart = raiseStart;
            this.raiseMiddle = raiseMiddle;
            this.raiseEnd = raiseEnd;
            this.sway = sway;
            this.lower = lower;
            this.raiseBack = raiseBack;
            this.swayIntensity = swayIntensity;
            this.swaySlow = swaySlow;
            this.swayFast = swayFast;
        }
    }

    public static Params params(IToolStackView tool, float ticks) {
        SpearCharge.Timings timings = SpearCharge.timings(tool);
        float delay = timings.activation();
        float engagedEnd = delay + timings.engaged();
        float tiredEnd = engagedEnd + timings.tired();
        float total = tiredEnd + timings.disengaged();
        float raise = progress(ticks, 0.0F, delay);
        float raiseStart = progress(raise, 0.0F, 0.5F);
        float raiseMiddle = progress(raise, 0.5F, 0.8F);
        float raiseEnd = progress(raise, 0.8F, 1.0F);
        // 原版：sway 从「失去挑落」前 20 tick 开始，到「失去击退」前 40 tick 结束。
        float sway = progress(ticks, engagedEnd - 20.0F, tiredEnd - 40.0F);
        float lower = easeOutCubic(easeInOutElastic(progress(ticks - 20.0F, tiredEnd - 40.0F, tiredEnd)));
        float raiseBack = progress(ticks, total - 5.0F, total);
        float intensity = 2.0F * easeOutCirc(sway) - 2.0F * easeInCirc(raiseBack);
        float slow = Mth.sin(ticks * 19.0F * DEG) * intensity;
        float fast = Mth.sin(ticks * 30.0F * DEG) * intensity;
        return new Params(raise, raiseStart, raiseMiddle, raiseEnd, sway, lower,
                raiseBack, intensity, slow, fast);
    }

    /**
     * 第一人称：按住右键压低。
     *
     * <p>逐行照抄 1.21.11 {@code SpearAnimations.firstPersonUse}：先绕 X 轴把矛压平，
     * 再绕 -Y 轴把矛头收到准星附近。此前把第二段写成了 Z 轴滚转，导致右键全程竖直；
     * 原版的命中反馈下压没有对应状态，省略。</p>
     */
    public static void firstPersonUse(PoseStack poseStack, float ticks, HumanoidArm arm, ItemStack stack) {
        Params p = params(ToolStack.from(stack), ticks);
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.translate(
                side * (p.raise * 0.15F - p.lower * 0.05F - p.sway * 0.1F + p.swayFast * 0.005F),
                p.raise * -0.075F + p.raiseMiddle * 0.075F + p.swayFast * 0.01F,
                p.raiseStart * 0.05F - p.lower * 0.05F + p.swaySlow * 0.005F);
        // 第一段抬起时整根矛再往玩家手侧收一点、并往前送一点
        poseStack.translate(side * 0.08F * p.raise, 0.0F, -0.06F * p.raise);
        rotateAround(poseStack, Axis.XP.rotationDegrees(
                        -65.0F * easeInOutBack(p.raise) - 35.0F * p.lower
                                + 100.0F * p.raiseBack - 0.5F * p.swayFast),
                0.0F, 0.1F, 0.0F);
        // 原版用 Axis.YN（绕 -Y），角度按左右手取反；写成 Z 轴滚转就会一直竖直
        rotateAround(poseStack, Axis.YN.rotationDegrees(
                        side * (-90.0F * progress(p.raise, 0.5F, 0.55F)
                                + 90.0F * p.sway + 2.0F * p.swaySlow)),
                side * 0.15F, 0.0F, 0.0F);
    }

    /** 第一人称戳刺：逐行照抄 1.21.11 的 {@code firstPersonAttack}，不做额外翻滚。 */
    public static void firstPersonAttack(float swing, PoseStack poseStack, HumanoidArm arm) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float p1 = easeInOutSine(progress(swing, 0.0F, 0.05F));
        float p2 = easeOutBack(progress(swing, 0.05F, 0.2F));
        float p3 = easeInOutExpo(progress(swing, 0.4F, 1.0F));
        poseStack.translate(
                side * 0.1F * (p1 - p2),
                -0.075F * (p1 - p3),
                0.65F * (p1 - p2));
        poseStack.mulPose(Axis.YP.rotationDegrees(-70.0F * (p1 - p3)));
        poseStack.translate(0.0F, 0.0F, -0.25F * (p3 - p2));
    }

    /** 第三人称：手持物品的压低/摇晃/放下。调用点在物品渲染之前（已经做完 hand transform）。 */
    public static void thirdPersonUse(PoseStack poseStack, ItemStack stack, float ticks,
            HumanoidArm arm) {
        ToolStack tool = ToolStack.from(stack);
        SpearCharge.Timings timings = SpearCharge.timings(tool);
        Params p = params(tool, ticks);
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float normal = Mth.clamp(ticks / (float) timings.total(), 0.0F, 1.0F);
        float p6 = easeInQuad(progress(normal, 0.05F, 0.2F));
        float p7 = easeInOutExpo(progress(normal, 0.4F, 1.0F));
        float raise = 1.0F - easeOutBack(1.0F - p.raise);
        // 1.21.11 KineticWeapon.forward_movement：压低时整根矛往屏幕里送
        poseStack.translate(0.0F, 0.0F, -SpearCharge.FORWARD_MOVEMENT * (raise - p.raiseBack));
        rotateAround(poseStack, Axis.XP.rotationDegrees(
                        70.0F * (p.raise - p.raiseBack) - 40.0F * (p6 - p7)),
                0.0F, -0.03125F, 0.125F);
        rotateAround(poseStack, Axis.YP.rotationDegrees(
                        side * 90.0F * (p.raise - p.sway + 3.0F * p7 + p6)),
                0.0F, 0.0F, 0.125F);
    }

    /** 第三人称戳刺：原版 thirdPersonAttackItem。{@code attack} 是 0~1 的挥击进度。 */
    public static void thirdPersonAttack(PoseStack poseStack, float attack, HumanoidArm arm) {
        if (attack <= 0.0F) {
            return;
        }
        float p6 = easeInQuad(progress(attack, 0.05F, 0.2F));
        float p7 = easeInOutExpo(progress(attack, 0.4F, 1.0F));
        rotateAround(poseStack, Axis.XP.rotationDegrees(70.0F * (p6 - p7)),
                0.0F, -0.125F, 0.125F);
        poseStack.translate(0.0F, SpearCharge.FORWARD_MOVEMENT * (p6 - p7), 0.0F);
    }

    /** 第三人称手臂：把主手抬到向前的持矛姿势，并按原版加疲劳摇晃。 */
    public static void applyArmPose(Player player, ModelPart rightArm, ModelPart leftArm) {
        if (!SpearClientState.isCharging(player)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!SpearContent.isSpear(stack)) {
            return;
        }
        boolean rightHanded = player.getMainArm() == HumanoidArm.RIGHT;
        ModelPart main = rightHanded ? rightArm : leftArm;
        ModelPart off = rightHanded ? leftArm : rightArm;
        int side = rightHanded ? 1 : -1;
        main.xRot = -0.1F * side + off.xRot;
        main.yRot = -PI / 2.0F + off.yRot + 0.8F;
        main.yRot -= 0.9599311F;
        main.xRot = Mth.clamp(main.xRot, -60.0F * DEG, 60.0F * DEG);
        main.yRot = Mth.clamp(main.yRot, -120.0F * DEG, 30.0F * DEG);

        Params p = params(ToolStack.from(stack), SpearClientState.chargeTicks(player));
        main.xRot += -side * p.swayFast * p.swayIntensity * DEG;
        main.zRot += -side * p.swaySlow * p.swayIntensity * DEG * 0.5F;
        main.yRot += DEG * (-40.0F * p.raiseStart + 30.0F * p.raiseMiddle
                - 20.0F * p.raiseEnd + 20.0F * p.lower + 10.0F * p.raiseBack
                + 0.6F * p.swaySlow * p.swayIntensity);
    }

    // ===== helper =====

    private static float progress(float value, float start, float end) {
        return Mth.clamp(Mth.inverseLerp(value, start, end), 0.0F, 1.0F);
    }

    private static void rotateAround(PoseStack poseStack, Quaternionf rotation,
            float x, float y, float z) {
        poseStack.translate(x, y, z);
        poseStack.mulPose(rotation);
        poseStack.translate(-x, -y, -z);
    }

    private static float easeInQuad(float t) {
        return t * t;
    }

    private static float easeOutCubic(float t) {
        float u = 1.0F - t;
        return 1.0F - u * u * u;
    }

    private static float easeInCirc(float t) {
        return 1.0F - (float) Math.sqrt(1.0F - t * t);
    }

    private static float easeOutCirc(float t) {
        float u = t - 1.0F;
        return (float) Math.sqrt(1.0F - u * u);
    }

    private static float easeInOutBack(float t) {
        float c2 = 2.5949094F;
        if (t < 0.5F) {
            float u = 2.0F * t;
            return u * u * ((c2 + 1.0F) * u - c2) / 2.0F;
        }
        float u = 2.0F * t - 2.0F;
        return (u * u * ((c2 + 1.0F) * u + c2) + 2.0F) / 2.0F;
    }

    private static float easeInOutSine(float t) {
        return -(Mth.cos(t * PI) - 1.0F) / 2.0F;
    }

    private static float easeInOutExpo(float t) {
        if (t <= 0.0F) {
            return 0.0F;
        }
        if (t >= 1.0F) {
            return 1.0F;
        }
        return t < 0.5F
                ? (float) (Math.pow(2.0, 20.0 * t - 10.0) / 2.0)
                : (float) ((2.0 - Math.pow(2.0, -20.0 * t + 10.0)) / 2.0);
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158F;
        float c3 = c1 + 1.0F;
        float u = t - 1.0F;
        return 1.0F + c3 * u * u * u + c1 * u * u;
    }

    private static float easeInOutElastic(float t) {
        if (t <= 0.0F) {
            return 0.0F;
        }
        if (t >= 1.0F) {
            return 1.0F;
        }
        float c5 = 2.0F * PI / 3.0F;
        return t < 0.5F
                ? (float) (-(Math.pow(2.0, 20.0 * t - 10.0)
                        * Mth.sin((20.0F * t - 11.125F) * c5)) / 2.0)
                : (float) ((Math.pow(2.0, -20.0 * t + 10.0)
                        * Mth.sin((20.0F * t - 11.125F) * c5)) / 2.0 + 1.0);
    }
}
