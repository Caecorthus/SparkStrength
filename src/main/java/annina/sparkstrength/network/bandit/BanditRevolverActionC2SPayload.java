package annina.sparkstrength.network.bandit;

import annina.sparkstrength.role.bandit.BanditRevolverService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * A workbench or ability-key action on the Bandit's revolver: fit or take off a part, push or take out rounds
 * ({@link BanditRevolverService.Action}). Inventory bookkeeping only, like a slot click; never a shot.
 * 对强盗改装左轮的改装界面/技能键操作：安装或卸下配件、塞入或取出子弹（见 {@link BanditRevolverService.Action}）。
 * 仅是物品栏整理，与点击格子同类；绝不是开火。
 */
public record BanditRevolverActionC2SPayload(int action, int arg) implements CustomPayload {
    public static final Id<BanditRevolverActionC2SPayload> ID =
            new Id<>(Identifier.of("sparkstrength", "bandit_revolver_action"));
    public static final PacketCodec<RegistryByteBuf, BanditRevolverActionC2SPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, BanditRevolverActionC2SPayload::action,
            PacketCodecs.VAR_INT, BanditRevolverActionC2SPayload::arg,
            BanditRevolverActionC2SPayload::new
    );

    public BanditRevolverActionC2SPayload(BanditRevolverService.Action action, int arg) {
        this(action.ordinal(), arg);
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public static void receive(BanditRevolverActionC2SPayload payload, ServerPlayNetworking.Context context) {
        BanditRevolverService.handleAction(context.player(), payload.action(), payload.arg());
    }
}
