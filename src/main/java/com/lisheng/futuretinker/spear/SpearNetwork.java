package com.lisheng.futuretinker.spear;

import com.lisheng.futuretinker.FutureTinker;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 一个包：客户端「拿着长矛左键空气」时告诉服务端，请它用放大的碰撞箱判定一次戳刺。
 *
 * <p>为什么需要它：1.20.1 的原版在「射线没点到实体」时不会把攻击发给服务端，
 * 而原版长矛的 0.125 碰撞箱放大恰恰就是为了「看起来偏了一点也应该戳到」。
 * 没有这个包，放大只对「已经点中了谁」的情况有效，等于没做。</p>
 */
public final class SpearNetwork {
    private static final String PROTOCOL = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(FutureTinker.id("main"))
            .clientAcceptedVersions(PROTOCOL::equals)
            .serverAcceptedVersions(PROTOCOL::equals)
            .networkProtocolVersion(() -> PROTOCOL)
            .simpleChannel();

    private static int nextId = 0;

    private SpearNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(SwingMessage.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SwingMessage::encode)
                .decoder(SwingMessage::decode)
                .consumerMainThread((message, context) -> message.handle(context))
                .add();
    }

    public static void sendSwing() {
        CHANNEL.sendToServer(new SwingMessage());
    }

    public record SwingMessage() {
        void encode(FriendlyByteBuf buffer) {
        }

        static SwingMessage decode(FriendlyByteBuf buffer) {
            return new SwingMessage();
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            SpearAttack.serverSwing(context.get().getSender());
        }
    }
}
