package annina.sparkstrength.role.pathogen;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The Pathogen team (Q7): every player whose real role is the Pathogen, original or converted.
 * 病原体阵营（Q7）：真实身份为病原体的所有玩家，无论原生还是转化。
 */
public final class PathogenTeamService {
    private PathogenTeamService() {
    }

    /**
     * The other online Pathogens of the winner's round, dead ones included; empty when the winner is no Pathogen.
     * Wathe's role map is read raw ({@code getAllWithRole}), so an acting overlay never adds anyone.
     * 胜者所在对局中其他在线病原体（含已死亡者）；胜者不是病原体时为空。直接读取 Wathe 身份表（{@code getAllWithRole}），
     * 因此扮演覆盖层不会加入任何人。
     */
    public static List<ServerPlayerEntity> coWinners(@Nullable ServerPlayerEntity winner) {
        if (winner == null) {
            return List.of();
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(winner.getServerWorld());
        if (!PathogenRules.isPathogen(game.getRole(winner))) {
            return List.of();
        }
        MinecraftServer server = winner.getServer();
        if (server == null) {
            return List.of();
        }
        List<ServerPlayerEntity> teammates = new ArrayList<>();
        for (UUID id : game.getAllWithRole(Noellesroles.PATHOGEN)) {
            if (id.equals(winner.getUuid())) {
                continue;
            }
            ServerPlayerEntity teammate = server.getPlayerManager().getPlayer(id);
            if (teammate != null) {
                teammates.add(teammate);
            }
        }
        return teammates;
    }
}
