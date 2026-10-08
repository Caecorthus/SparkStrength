package annina.sparkstrength.network;

import annina.sparkstrength.role.serialkiller.SerialPistolShotService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;

/** 连环手枪专用开火包，携带手位以支持主手/副手各自独立冷却。 */
public record SerialPistolShootC2SPayload(int targetId, Hand hand) implements CustomPayload {
    public static final Id<SerialPistolShootC2SPayload> ID = new Id<>(Identifier.of("sparkstrength", "serial_pistol_shoot"));
    public static final PacketCodec<RegistryByteBuf, SerialPistolShootC2SPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, SerialPistolShootC2SPayload::targetId,
            
            PacketCodecs.VAR_INT.xmap(i -> i == 1 ? Hand.OFF_HAND : Hand.MAIN_HAND, hand -> hand == Hand.OFF_HAND ? 1 : 0),
            SerialPistolShootC2SPayload::hand,
            (targetId, hand) -> new SerialPistolShootC2SPayload(targetId, hand)
    );

    @Override public Id<? extends CustomPayload> getId() { return ID; }

    /**
     * Thin receiver: all authority lives in {@link SerialPistolShotService}. SparkWitch deny-lists key on this id.
     * 轻量接收器：全部服务端校验在 SerialPistolShotService 中；SparkWitch 的拒绝名单以此 id 为准。
     */
    public static void receive(SerialPistolShootC2SPayload payload, ServerPlayNetworking.Context context) {
        SerialPistolShotService.handle(context.player(), payload.targetId(), payload.hand());
    }
}
