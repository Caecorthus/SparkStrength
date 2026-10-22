package annina.sparkstrength.role.partyanimal;

import annina.sparkstrength.component.engineer.EngineerStunnedPlayerComponent;
import annina.sparkstrength.component.partyanimal.PartyAnimalSkillComponent;
import annina.sparkstrength.compat.SparkTraitsDroneCompat;
import annina.sparkstrength.network.partyanimal.PartygoerMenuS2CPacket;
import annina.sparkstrength.role.bomber.drone.DroneService;
import annina.sparkstrength.role.economy.KillerTeamEconomyService;
import annina.sparkstrength.role.partyanimal.nav.TrainNavGrid;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.TaskComplete;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server authority for the Party Animal's second skill: the task unlock, the room picker and the summon. The client only
 * reports a key press and the two picked room names; every gate is re-checked here, and refusals are an actionbar line.
 * 派对狂二技能的服务端权威：任务解锁、选房界面与召唤。客户端只上报按键和选中的两个房间名；所有门槛都在此重新检查，拒绝时显示动作栏提示。
 */
public final class PartygoerSkillService {
    /** One picker request, and one summon, per player this often; extra packets are dropped. / 每名玩家每隔这么久只处理一次选房请求与一次召唤。 */
    private static final long REQUEST_INTERVAL_TICKS = 10;
    private static final Map<UUID, Long> LAST_MENU = new HashMap<>();
    private static final Map<UUID, Long> LAST_SUMMON = new HashMap<>();
    private static boolean registered;

    private PartygoerSkillService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        TaskComplete.EVENT.register((player, task) -> onTaskComplete(player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            LAST_MENU.clear();
            LAST_SUMMON.clear();
        });
    }

    /** Drops packets that come faster than one per half second (a modified client spamming searches). / 丢弃过快的请求包。 */
    private static boolean throttled(ServerPlayerEntity player, Map<UUID, Long> last) {
        long now = player.getServerWorld().getTime();
        Long previous = last.put(player.getUuid(), now);
        return previous != null && now >= previous && now - previous < REQUEST_INTERVAL_TICKS;
    }

    /**
     * Wathe gives the FAKE-mood Party Animal tasks too; only tasks done while alive as the Party Animal count.
     * Wathe 同样会给假理智的派对狂派任务；只有作为存活的派对狂完成的任务才计入。
     */
    private static void onTaskComplete(@Nullable ServerPlayerEntity player) {
        if (player == null || GameWorldComponent.KEY.get(player.getServerWorld()).getGameStatus()
                != GameWorldComponent.GameStatus.ACTIVE
                || !GameFunctions.isPlayerPlayingAndAlive(player)
                || !PartyAnimalRules.isPartyAnimal(GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player))) {
            return;
        }
        PartyAnimalSkillComponent skill = PartyAnimalSkillComponent.KEY.get(player);
        boolean wasUnlocked = skill.isUnlocked();
        skill.recordTask();
        if (!wasUnlocked && skill.isUnlocked()) {
            player.sendMessage(Text.translatable("tip.sparkstrength.partygoer.unlocked").formatted(Formatting.LIGHT_PURPLE),
                    true);
        }
    }

    /** RoleAssigned / ResetPlayer / round end: start from scratch. / 分配职业、重置玩家、对局结束：从零开始。 */
    public static void reset(ServerPlayerEntity player) {
        PartyAnimalSkillComponent.KEY.get(player).reset();
    }

    public static void assignForRole(ServerPlayerEntity player, Role role) {
        reset(player);
    }

    // ---- Key press: open the picker / 按键：打开选房界面 ----

    public static void openMenu(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        UUID roundId = DroneService.currentRoundId(world);
        if (roundId == null || !isActivePartyAnimal(player) || throttled(player, LAST_MENU)) {
            return;
        }
        Text refusal = refusal(player);
        if (refusal != null) {
            player.sendMessage(refusal, true);
            return;
        }
        List<PartyRoomIndex.PartyRoom> rooms = PartyRoomIndex.rooms(world, roundId);
        if (rooms.isEmpty()) {
            player.sendMessage(tip("no_rooms"), true);
            return;
        }
        List<String> names = new ArrayList<>(rooms.size());
        for (PartyRoomIndex.PartyRoom room : rooms) {
            names.add(room.name());
        }
        ServerPlayNetworking.send(player, new PartygoerMenuS2CPacket(names));
    }

    // ---- Picker confirmed: summon / 选房确认：召唤 ----

    public static void summon(ServerPlayerEntity player, String spawnName, String destinationName) {
        ServerWorld world = player.getServerWorld();
        UUID roundId = DroneService.currentRoundId(world);
        if (roundId == null || !isActivePartyAnimal(player) || throttled(player, LAST_SUMMON)) {
            return;
        }
        Text refusal = refusal(player);
        if (refusal != null) {
            player.sendMessage(refusal, true);
            return;
        }
        List<PartyRoomIndex.PartyRoom> rooms = PartyRoomIndex.rooms(world, roundId);
        PartyRoomIndex.PartyRoom spawn = find(rooms, spawnName);
        PartyRoomIndex.PartyRoom destination = find(rooms, destinationName);
        if (spawn == null || destination == null) {
            player.sendMessage(tip("no_rooms"), true);
            return;
        }
        // Money first: the prey search below walks the whole train. / 先查钱：下面的猎物搜索会走遍整列火车。
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
        int balance = shop.getBalance();
        if (KillerTeamEconomyService.availableForPurchase(player, balance) < PartyAnimalRules.SKILL_PRICE) {
            player.sendMessage(Text.translatable("tip.sparkstrength.partygoer.no_money", PartyAnimalRules.SKILL_PRICE)
                    .formatted(Formatting.RED), true);
            return;
        }
        TrainNavGrid grid = PartyRoomIndex.newGrid(world);
        ServerPlayerEntity prey = PartygoerTargeting.nearestPrey(world, grid, spawn.anchorCell(), player.getUuid());
        if (prey == null) {
            player.sendMessage(tip("no_prey"), true);
            return;
        }
        shop.setBalance(KillerTeamEconomyService.personalBalanceAfterPurchase(player, balance,
                PartyAnimalRules.SKILL_PRICE));
        PartygoerService.summon(player, roundId, grid, spawn, destination, prey);
        player.sendMessage(Text.translatable("tip.sparkstrength.partygoer.summoned", spawn.name(), destination.name())
                .formatted(Formatting.LIGHT_PURPLE), true);
    }

    /**
     * The first gate that blocks the skill right now, as an actionbar line; null when it may be used.
     * 当前阻止使用技能的第一个门槛（动作栏文本）；可以使用时为 null。
     */
    private static @Nullable Text refusal(ServerPlayerEntity player) {
        PartyAnimalSkillComponent skill = PartyAnimalSkillComponent.KEY.get(player);
        if (isSkillLocked(player)) {
            return tip("locked");
        }
        if (!skill.isUnlocked()) {
            return Text.translatable("tip.sparkstrength.partygoer.needs_tasks",
                    skill.getCompletedTasks(), PartyAnimalRules.UNLOCK_TASKS).formatted(Formatting.GRAY);
        }
        if (PartygoerService.hasActive(player.getUuid())) {
            return tip("active");
        }
        if (skill.getCooldownTicks() > 0) {
            return Text.translatable("tip.sparkstrength.partygoer.cooldown", Math.ceilDiv(skill.getCooldownTicks(), 20))
                    .formatted(Formatting.GRAY);
        }
        if (!PartygoerTargeting.anyValidPrey(player.getServerWorld(), player.getUuid())) {
            return tip("no_prey");
        }
        return null;
    }

    /** Alive, playing, in survival, not swallowed, and currently the Party Animal. / 存活、在局内、生存模式、未被吞，且当前是派对狂。 */
    private static boolean isActivePartyAnimal(ServerPlayerEntity player) {
        return GameFunctions.isPlayerPlayingAndAlive(player)
                && GameFunctions.isPlayerAliveAndSurvival(player)
                && !SwallowedPlayerComponent.isPlayerSwallowed(player)
                && PartyAnimalRules.isPartyAnimal(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    /** Engineer stun, SparkTraits role-skill block and a pending Last Stand, like the Vulture's second skill. / 与秃鹫二技能相同的封锁。 */
    private static boolean isSkillLocked(ServerPlayerEntity player) {
        return EngineerStunnedPlayerComponent.KEY.get(player).isStunned()
                || SparkTraitsDroneCompat.isRoleSkillBlocked(player)
                || SparkTraitsDroneCompat.isLastStandPending(player);
    }

    private static @Nullable PartyRoomIndex.PartyRoom find(List<PartyRoomIndex.PartyRoom> rooms, String name) {
        for (PartyRoomIndex.PartyRoom room : rooms) {
            if (room.name().equals(name)) {
                return room;
            }
        }
        return null;
    }

    private static Text tip(String key) {
        return Text.translatable("tip.sparkstrength.partygoer." + key).formatted(Formatting.GRAY);
    }
}
