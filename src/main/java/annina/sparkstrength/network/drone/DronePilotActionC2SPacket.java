package annina.sparkstrength.network.drone;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Pilot role-skill action (currently only {@link #FIRE}); the server rejects any other byte. Wire values are a stable
 * packet contract. Leaving the drone is the separate {@link DronePilotExitC2SPacket}, so add-ons that block role-skill
 * payloads by id (SparkWitch Fear) can block this one without trapping the pilot.
 * 驾驶者职业技能动作（目前只有 FIRE），服务器拒绝其他任何字节。wire 值是稳定的网络包契约。离开无人机使用独立的
 * DronePilotExitC2SPacket，因此按 id 拦截职业技能包的附属模组（SparkWitch 恐惧）可以拦截本包而不会把驾驶者困住。
 */
public record DronePilotActionC2SPacket(int entityId, byte action) implements CustomPayload {
    /** Left click: grenade drone drops its M67, bomb drone detonates. / 左键：投弹无人机投下 M67，炸弹无人机引爆。 */
    public static final byte FIRE = 0;

    public static final Id<DronePilotActionC2SPacket> ID = new Id<>(SparkStrength.id("drone_pilot_action"));
    public static final PacketCodec<RegistryByteBuf, DronePilotActionC2SPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, DronePilotActionC2SPacket::entityId,
            PacketCodecs.BYTE, DronePilotActionC2SPacket::action,
            DronePilotActionC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
