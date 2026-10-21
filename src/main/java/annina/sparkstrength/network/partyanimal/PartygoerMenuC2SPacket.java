package annina.sparkstrength.network.partyanimal;

import annina.sparkstrength.SparkStrength;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * The Party Animal pressed Role Skill 2: asks the server for the room picker. Empty on purpose; every gate is checked on
 * the server, which answers with {@link PartygoerMenuS2CPacket} or an actionbar refusal.
 * 派对狂按下“职业技能 2”：向服务端请求选房界面。故意不带字段；所有门槛都在服务端检查，服务端回以 PartygoerMenuS2CPacket 或动作栏拒绝提示。
 */
public record PartygoerMenuC2SPacket() implements CustomPayload {
    public static final Identifier PAYLOAD_ID = SparkStrength.id("partygoer_menu");
    public static final Id<PartygoerMenuC2SPacket> ID = new Id<>(PAYLOAD_ID);
    public static final PacketCodec<RegistryByteBuf, PartygoerMenuC2SPacket> CODEC =
            PacketCodec.of(PartygoerMenuC2SPacket::write, PartygoerMenuC2SPacket::read);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public void write(PacketByteBuf buf) {
    }

    public static PartygoerMenuC2SPacket read(PacketByteBuf buf) {
        return new PartygoerMenuC2SPacket();
    }
}
