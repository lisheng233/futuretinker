package com.lisheng.futuretinker.spear;

import com.lisheng.futuretinker.FutureTinker;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 长矛的网络包。
 *
 * <ul>
 *   <li>{@code SwingMessage}：客户端「拿矛左键空气」时告诉服务端，请它用放大的碰撞箱判定一次戳刺。
 *       1.20.1 原版在「射线没点到实体」时不会把攻击发给服务端，而原版长矛的 0.125 碰撞箱放大
 *       恰恰就是为了「看起来偏了一点也应该戳到」。</li>
 *   <li>{@code ChargeStart} / {@code ChargeStop}：冲锋**不走原版的「使用物品」**。
 *       原版的 {@code isUsingItem()} 会把移动输入乘 0.2（{@code LocalPlayer.aiStep}），
 *       而原版长矛冲锋时是要能跑起来的；另外「使用」还会触发三叉戟那套压低动画。
 *       所以右键只发一个「开始冲锋」，松手发「结束冲锋」，计时在服务端自己走。</li>
 * </ul>
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
        CHANNEL.messageBuilder(ChargeMessage.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ChargeMessage::encode)
                .decoder(ChargeMessage::decode)
                .consumerMainThread((message, context) -> message.handle(context))
                .add();
    }

    public static void sendSwing() {
        CHANNEL.sendToServer(new SwingMessage());
    }

    public static void sendCharge(boolean start) {
        CHANNEL.sendToServer(new ChargeMessage(start));
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

    public record ChargeMessage(boolean start) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeBoolean(start);
        }

        static ChargeMessage decode(FriendlyByteBuf buffer) {
            return new ChargeMessage(buffer.readBoolean());
        }

        void handle(Supplier<NetworkEvent.Context> context) {
            if (start) {
                SpearCharge.start(context.get().getSender());
            } else {
                SpearCharge.stop(context.get().getSender());
            }
        }
    }
}
