package annina.sparkstrength.network.spiritualist;

import annina.sparkstrength.role.spiritualist.SpiritPossessionRules;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

/**
 * Leave the possession (sneak, role skill 2 again, or the client lost its view). Kept apart from
 * {@link SpiritPossessC2SPacket} on purpose: add-ons that block role-skill payloads by id may block starting, but
 * leaving must always get through.
 * 结束附身（潜行、再按职业技能 2，或客户端丢失画面）。刻意与 SpiritPossessC2SPacket 分开：按 id 拦截职业技能包的附属模组
 * 可以拦截开始附身，但退出必须始终能送达。
 */
public record SpiritPossessExitC2SPacket() implements CustomPayload {
    public static final Id<SpiritPossessExitC2SPacket> ID = new Id<>(SpiritPossessionRules.EXIT_PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, SpiritPossessExitC2SPacket> CODEC =
            PacketCodec.unit(new SpiritPossessExitC2SPacket());

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
