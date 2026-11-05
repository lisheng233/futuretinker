package com.lisheng.futuretinker.spear;

import com.lisheng.futuretinker.FutureTinker;
import com.lisheng.futuretinker.module.CreativeTab;
import com.lisheng.futuretinker.module.FtModule;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.RegisterEvent;

/**
 * 匠魂化长矛（模块二）。
 *
 * <p>复刻高版本原版长矛：</p>
 * <ul>
 *   <li><b>戳刺</b>：4.5 格触及、2 格内打不到、必须满蓄力、没有暴击与冲刺击退、不挖方块、
 *       一次戳多个目标、目标碰撞箱放大 0.125（{@link SpearAttack}）；</li>
 *   <li><b>冲锋</b>：按住右键压低，伤害 = 1 + 倍率 × 相对速度，够快则击退、挑落骑乘（{@link SpearCharge}）；</li>
 *   <li><b>疲劳</b>：engaged → tired → disengaged，窗口长度由手柄材料决定。</li>
 * </ul>
 */
public final class SpearModule implements FtModule {
    public static final String ID = "spear";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void attach(IEventBus modBus) {
        SpearNetwork.register();
        // 戳刺/冲锋的规矩都是 forge 总线上的玩家事件；模块被关掉时这个方法根本不会被调用
        MinecraftForge.EVENT_BUS.addListener(SpearAttack::onAttack);
        MinecraftForge.EVENT_BUS.addListener(SpearAttack::onCritical);
        MinecraftForge.EVENT_BUS.addListener(SpearAttack::onLeftClickBlock);
        MinecraftForge.EVENT_BUS.addListener(SpearAttack::onLeftClickEmpty);
        // 客户端：矛头/长矛图标按材料上色（服务器端不会加载这个类）
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> SpearClient.register(modBus));
        FutureTinker.LOGGER.info("Future Tinker: 长矛已接入（戳刺 + 冲锋 + 疲劳；矛头部件 {}）",
                SpearContent.HEAD_ID);
    }

    @Override
    public void registerRegistries(RegisterEvent event) {
        SpearContent.registerItems(event);
        if (Registries.ITEM.equals(event.getRegistryKey())) {
            // 唯一的「未来匠魂」标签页由 ModuleRegistry 统一建，这里只把物品交上去
            CreativeTab.add(SpearContent::spear);
            CreativeTab.add(SpearContent::spearHead);
            SpearContent.casts().forEach(cast -> CreativeTab.add(() -> cast));
        }
    }

    @Override
    public List<ResourceLocation> contentItems() {
        return List.of(FutureTinker.id(SpearContent.ID), FutureTinker.id(SpearContent.HEAD_ID),
                FutureTinker.id(SpearContent.HEAD_ID + "_cast"),
                FutureTinker.id(SpearContent.HEAD_ID + "_sand_cast"),
                FutureTinker.id(SpearContent.HEAD_ID + "_red_sand_cast"));
    }

    /** 冲锋时压低矛身的声音（原版 {@code item.spear.use} 在 1.20.1 不存在，用最接近的音效）。 */
    static void playLowerSound(Player player) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6F, 0.7F);
    }
}
