package annina.sparkstrength.network.spiritualist;

import annina.sparkstrength.role.spiritualist.SpiritPossessionRules;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/**
 * Possess the Wraith with this entity id (role skill 2 while aiming at it, from the body or the spirit). The server
 * re-validates role, round, liveness, locks, cooldown, the target and the reach; the id is in the daze deny-list and in
 * SparkWitch's skill deny-lists.
 * 附身该实体 id 的冤魂（在肉身或灵魂状态下对准冤魂按职业技能 2）。服务端重新校验身份、对局、存活、各类锁定、冷却、目标与距离；
 * 该 id 已列入眩晕拦截表与 SparkWitch 的技能拦截名单。
 */
public record SpiritPossessC2SPacket(int targetEntityId) implements CustomPayload {
    public static final Id<SpiritPossessC2SPacket> ID = new Id<>(SpiritPossessionRules.POSSESS_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SpiritPossessC2SPacket> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, SpiritPossessC2SPacket::targetEntityId,
            SpiritPossessC2SPacket::new
    );

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
