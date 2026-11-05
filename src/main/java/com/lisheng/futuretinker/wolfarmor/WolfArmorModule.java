package com.lisheng.futuretinker.wolfarmor;

import com.lisheng.futuretinker.FutureTinker;
import com.lisheng.futuretinker.module.CreativeTab;
import com.lisheng.futuretinker.module.FtModule;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegisterEvent;

/**
 * 匠魂化狼铠（模块一）。
 *
 * <p>定位是**护甲的变种**：一件 {@code ModifiableArmorItem}，材料强度来自胸甲板材统计，
 * 装/脱靠狼的护甲槽，装配与修复走匠魂既有流程。部件与护甲的定义、数据形状见
 * {@link WolfArmorContent}。</p>
 */
public final class WolfArmorModule implements FtModule {
    public static final String ID = "wolfarmor";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void attach(IEventBus modBus) {
        // 交互是 forge 总线上的事件；模块被关掉时这个方法根本不会被调用，所以不用再判开关。
        // 两个事件都监听：真客户端点实体派发 EntityInteractSpecific，服务端交互派发 EntityInteract。
        MinecraftForge.EVENT_BUS.addListener(WolfArmorInteraction::onEntityInteract);
        MinecraftForge.EVENT_BUS.addListener(WolfArmorInteraction::onEntityInteractSpecific);
        // 死亡掉落：实测默认情况下铠甲会直接消失（见 WolfArmorDrops 注释）。
        MinecraftForge.EVENT_BUS.addListener(WolfArmorDrops::onLivingDrops);
        // 受击：用狼铠耐久顶掉护甲减免后的非魔法伤害（见 WolfArmorCombat）。
        MinecraftForge.EVENT_BUS.addListener(WolfArmorCombat::onLivingDamage);
        // 客户端：图标按材料上色 + 穿在狼身上的渲染层（服务器端不会加载这个类）
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> WolfArmorClient.register(modBus));
        FutureTinker.LOGGER.info("Future Tinker: 狼铠护甲定义 id = {}（对应 data/futuretinker/tinkering/tool_definitions/*.json）",
                WolfArmorContent.armorDefinitionId());
    }

    @Override
    public void registerRegistries(RegisterEvent event) {
        // 物品在 RegisterEvent 里创建（Item 构造函数会写注册表，构造阶段注册表已冻结）。
        WolfArmorContent.registerItems(event);
        if (Registries.ITEM.equals(event.getRegistryKey())) {
            // 唯一的「未来匠魂」标签页由 ModuleRegistry 统一建，这里只把物品交上去
            CreativeTab.add(WolfArmorContent::wolfArmor);
        }
    }

    @Override
    public List<ResourceLocation> contentItems() {
        return List.of(FutureTinker.id(WolfArmorContent.ARMOR_ID));
    }
}
