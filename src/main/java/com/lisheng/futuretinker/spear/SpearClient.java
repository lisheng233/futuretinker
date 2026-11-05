package com.lisheng.futuretinker.spear;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfo;
import slimeknights.tconstruct.library.client.materials.MaterialRenderInfoLoader;
import slimeknights.tconstruct.library.materials.definition.MaterialVariantId;
import slimeknights.tconstruct.library.tools.nbt.ToolStack;
import slimeknights.tconstruct.library.tools.nbt.MaterialNBT;
import slimeknights.tconstruct.library.tools.part.IMaterialItem;

/**
 * 长矛的客户端表现：图标按材料上色。
 *
 * <p>三张贴图分别是矛头 / 矛杆 / 绑定结，对应材料表下标 0/1/2
 * （{@code item/generated} 的第 i 层 tintindex 就是 i，见 {@code ItemModelGenerator}），
 * 所以一根长矛能同时有三种材料色，和匠魂工具一样。</p>
 *
 * <p>颜色取匠魂材料渲染色（{@code MaterialRenderInfo#vertexColor()}）。事件挂在**模组总线**上——
 * {@code RegisterColorHandlersEvent} 是模组总线事件，挂错总线会静默失效（狼铠那轮踩过）。</p>
 */
@OnlyIn(Dist.CLIENT)
public final class SpearClient {
    private SpearClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(SpearClient::onRegisterColors);
        // 冲锋输入（本地计时 + 发包）与「压低动画」的模型 predicate
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(SpearClientInput::onClientTick);
        net.minecraft.client.renderer.item.ItemProperties.register(SpearContent.spear(),
                new net.minecraft.resources.ResourceLocation("futuretinker", "spear_charge"),
                (stack, level, entity, seed) -> SpearClientInput.chargeTicks());
        // 戳刺的前冲：原版 1.21 的矛是「刺」而不是「挥」，1.20.1 没有物品动画接口，
        // 所以用攻击动画进度（LivingEntity#attackAnim）切一个前冲的 display 模型出来。
        net.minecraft.client.renderer.item.ItemProperties.register(SpearContent.spear(),
                new net.minecraft.resources.ResourceLocation("futuretinker", "spear_attack"),
                (stack, level, entity, seed) -> entity == null ? 0.0F : entity.getAttackAnim(0.0F));
    }

    private static void onRegisterColors(RegisterColorHandlersEvent.Item event) {
        event.register(SpearClient::toolColor, SpearContent.spear());
        event.register((stack, index) -> index == 0 ? partColor(stack) : -1, SpearContent.spearHead());
    }

    /** 长矛：层 0 = 矛头材料，层 1 = 手柄材料，层 2 = 绑定结材料。 */
    private static int toolColor(ItemStack stack, int index) {
        if (index < 0 || index > 2) {
            return -1;
        }
        MaterialNBT materials = ToolStack.from(stack).getMaterials();
        if (index >= materials.size()) {
            return 0xFFFFFFFF;
        }
        return colorOf(materials.get(index).getId());
    }

    private static int partColor(ItemStack stack) {
        MaterialVariantId material = IMaterialItem.getMaterialFromStack(stack);
        return material == null ? 0xFFFFFFFF : colorOf(material);
    }

    private static int colorOf(MaterialVariantId material) {
        return MaterialRenderInfoLoader.INSTANCE.getRenderInfo(material)
                .map(MaterialRenderInfo::vertexColor)
                .orElse(0xFFFFFFFF);
    }
}
