package annina.sparkstrength.client.role.partyanimal;

import annina.sparkstrength.entity.PartygoerEntity;
import annina.sparkstrength.network.partyanimal.PartygoerSwallowC2SPacket;
import annina.sparkstrength.role.partyanimal.PartygoerCarry;
import annina.sparkstrength.role.partyanimal.PartygoerState;
import annina.sparkstrength.role.partyanimal.PartygoerTaotieRules;
import annina.sparkstrength.role.taotie.TaotieHeadDaze;
import annina.sparkstrength.role.taotie.TaotieHeadRules;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.agmas.noellesroles.client.NoellesrolesClient;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.agmas.noellesroles.taotie.TaotiePlayerComponent;
import org.jetbrains.annotations.Nullable;

/**
 * Taotie client for swallowing a Partygoer (prediction only; {@code PartygoerTaotieService} re-validates everything):
 * decides whether a press of NoellesRoles' ability key belongs to the Partygoer under the vanilla crosshair, and sends
 * {@code partygoer_swallow}. NoellesRoles' own crosshair only sees players, so without this claim it would swallow
 * whoever stands behind the Partygoer. A server that can't receive the payload leaves NoellesRoles alone.
 * 饕餮吞派对客的客户端（仅预测；{@code PartygoerTaotieService} 重新校验一切）：判断一次 NoellesRoles 技能键按下是否属于原版
 * 准星上的派对客，并发送 {@code partygoer_swallow}。NoellesRoles 自己的准星只看玩家，不认领的话它会吞掉站在派对客身后的人。
 * 服务器无法接收该数据包时不干预 NoellesRoles。
 */
public final class PartygoerTaotieClient {
    private PartygoerTaotieClient() {
    }

    /**
     * Called from the client key gate ({@code ClientKeyGate#clicked}) with the {@code wasPressed} result so far.
     * Returns {@link PartygoerTaotieRules#NO_CLAIM} to leave the press to NoellesRoles; otherwise the press is claimed
     * and the result is the Partygoer's entity id to send, or {@link PartygoerTaotieRules#CLAIM_WITHOUT_SWALLOW} when
     * it shields the press but stands outside the server's feet-to-feet reach. Cheap gates first: key identity,
     * connection, readiness, and only then the crosshair.
     * 由客户端按键闸门（{@code ClientKeyGate#clicked}）以当前的 wasPressed 结果调用。返回 NO_CLAIM 表示把按键留给
     * NoellesRoles；否则按键被认领，结果为要发送的派对客实体 id，或在它遮挡按键但超出服务端脚到脚距离时返回
     * CLAIM_WITHOUT_SWALLOW。廉价判定优先：按键身份、连接、就绪状态，最后才读准星。
     */
    public static int claimPress(@Nullable KeyBinding keyBinding, boolean pressed) {
        if (!pressed || keyBinding == null || keyBinding != NoellesrolesClient.abilityBind) {
            return PartygoerTaotieRules.NO_CLAIM;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.getNetworkHandler() == null) {
            return PartygoerTaotieRules.NO_CLAIM;
        }
        PartygoerEntity[] shielding = new PartygoerEntity[1];
        boolean claimed = PartygoerTaotieRules.claimsPress(true, true,
                ClientPlayNetworking.canSend(PartygoerSwallowC2SPacket.ID),
                () -> isTaotieReady(player),
                () -> (shielding[0] = shieldingPartygoer(client, player)) != null);
        if (!claimed) {
            return PartygoerTaotieRules.NO_CLAIM;
        }
        PartygoerEntity partygoer = shielding[0];
        return PartygoerTaotieRules.withinReach(player.squaredDistanceTo(partygoer),
                TaotiePlayerComponent.SWALLOW_DISTANCE_SQUARED)
                ? partygoer.getId()
                : PartygoerTaotieRules.CLAIM_WITHOUT_SWALLOW;
    }

    /** Sends a claimed swallow; negative claims (shield only) send nothing. / 发送已认领的吞噬；负值（仅遮挡）不发送。 */
    public static void send(int claim) {
        if (claim >= 0 && ClientPlayNetworking.canSend(PartygoerSwallowC2SPacket.ID)) {
            ClientPlayNetworking.send(new PartygoerSwallowC2SPacket(claim));
        }
    }

    private static boolean isTaotieReady(ClientPlayerEntity player) {
        return PartygoerTaotieRules.taotieReady(
                TaotieHeadRules.isTaotie(GameWorldComponent.KEY.get(player.getWorld()).getRole(player)),
                GameFunctions.isPlayerPlayingAndAlive(player) && GameFunctions.isPlayerAliveAndSurvival(player),
                SwallowedPlayerComponent.isPlayerSwallowed(player),
                PartygoerCarry.isCarried(player),
                TaotieHeadDaze.isDazed(player),
                TaotiePlayerComponent.KEY.get(player).getSwallowCooldown());
    }

    /**
     * The Partygoer under vanilla's crosshair (which also hits players, so it is the nearest thing on the ray) within
     * NoellesRoles' eye-to-hit reach. One already inside a Taotie is skipped: it sits hidden in the Taotie's body.
     * 原版准星上的派对客（准星也会命中玩家，因此它是射线上最近的目标），且在 NoellesRoles 的眼到命中点距离内。已在饕餮肚里的
     * 派对客被跳过：它隐身待在饕餮身体里。
     */
    private static @Nullable PartygoerEntity shieldingPartygoer(MinecraftClient client, ClientPlayerEntity player) {
        HitResult hit = client.crosshairTarget;
        if (!(hit instanceof EntityHitResult entityHit)
                || !(entityHit.getEntity() instanceof PartygoerEntity partygoer)
                || !partygoer.isAlive()
                || partygoer.isRemoved()
                || partygoer.partyState() == PartygoerState.SWALLOWED) {
            return null;
        }
        return PartygoerTaotieRules.shieldsPress(player.getEyePos().distanceTo(entityHit.getPos())) ? partygoer : null;
    }
}
