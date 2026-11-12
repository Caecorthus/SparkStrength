package annina.sparkstrength.network.bandit;

import annina.sparkstrength.role.bandit.BanditRevolverShotService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * A shot of the Bandit's moddable revolver; {@code targetId} is the entity the client's ray picked, or -1. Thin
 * receiver: all authority lives in {@link BanditRevolverShotService}. Deny lists key on this id.
 * 强盗改装左轮的一次开火；{@code targetId} 为客户端射线选中的实体，没有则为 -1。轻量接收器：全部服务端校验在
 * {@link BanditRevolverShotService} 中；拒绝名单以此 id 为准。
 */
public record BanditRevolverShootC2SPayload(int targetId) implements CustomPayload {
    public static final Id<BanditRevolverShootC2SPayload> ID =
            new Id<>(Identifier.of("sparkstrength", "bandit_revolver_shoot"));
    public static final PacketCodec<RegistryByteBuf, BanditRevolverShootC2SPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, BanditRevolverShootC2SPayload::targetId,
            BanditRevolverShootC2SPayload::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void receive(BanditRevolverShootC2SPayload payload, ServerPlayNetworking.Context context) {
        BanditRevolverShotService.handle(context.player(), payload.targetId());
    }
}
