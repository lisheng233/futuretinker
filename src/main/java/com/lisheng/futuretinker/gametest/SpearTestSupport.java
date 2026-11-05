package com.lisheng.futuretinker.gametest;

import com.lisheng.futuretinker.spear.SpearContent;
import java.util.UUID;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import slimeknights.tconstruct.library.materials.MaterialRegistry;
import slimeknights.tconstruct.library.materials.definition.IMaterial;
import slimeknights.tconstruct.library.materials.definition.MaterialId;
import slimeknights.tconstruct.library.tools.helper.ToolBuildHandler;
import slimeknights.tconstruct.library.tools.item.IModifiable;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;

/**
 * 测试辅助：造长矛、造材料、把攻击冷却压到「满蓄力」。
 *
 * <p>长矛的部件顺序是 {@code 矛头 / 坚韧手柄 / 坚韧绑定结}，
 * 所以材料表下标 0 = 矛头、1 = 手柄、2 = 绑定结。</p>
 */
final class SpearTestSupport {
    private SpearTestSupport() {}

    static IMaterial material(String id) {
        return MaterialRegistry.getMaterial(new MaterialId(id));
    }

    static ItemStack spear(IMaterial head, IMaterial handle, IMaterial binding) {
        return ToolBuildHandler.buildItemFromMaterials((IModifiable) SpearContent.spear(),
                MaterialNBT.of(head, handle, binding));
    }

    static ItemStack ironSpear() {
        IMaterial iron = material("tconstruct:iron");
        return spear(iron, iron, iron);
    }

    /**
     * 把攻击速度拉高，冷却变成 1 tick，于是当前刻度就算「满蓄力」。
     *
     * <p>{@code attackStrengthTicker} 是 protected，测试里没法直接设，
     * 所以从「冷却长度」这一侧下手。</p>
     */
    static void forceFullCharge(Player player) {
        AttributeInstance attackSpeed = player.getAttribute(Attributes.ATTACK_SPEED);
        if (attackSpeed == null) {
            throw new IllegalStateException("玩家没有 attack_speed 属性");
        }
        attackSpeed.addTransientModifier(new AttributeModifier(
                UUID.fromString("6a1f2d3c-4b5e-4f60-8a71-9c2d3e4f5a6b"), "spear_test_charge", 40.0D,
                AttributeModifier.Operation.ADDITION));
    }
}
