package annina.sparkstrength.network.drone;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Leave pilot mode and return to the body (right click, or the client dropped its view on its own). Kept apart from
 * {@link DronePilotActionC2SPacket} on purpose: add-ons that block role-skill payloads by id (SparkWitch Fear) may block
 * FIRE, but leaving must always get through.
 * 退出驾驶模式、回到身体（右键，或客户端自行结束画面）。刻意与 DronePilotActionC2SPacket 分开：按 id 拦截职业技能包的附属模组
 * （SparkWitch 恐惧）可以拦截开火，但退出必须始终能送达。
 */
public record DronePilotExitC2SPacket(int entityId) implements CustomPayload {
    public static final Id<DronePilotExitC2SPacket> ID = new Id<>(SparkStrength.id("drone_pilot_exit"));
    public static final PacketCodec<RegistryByteBuf, DronePilotExitC2SPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, DronePilotExitC2SPacket::entityId,
            DronePilotExitC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
