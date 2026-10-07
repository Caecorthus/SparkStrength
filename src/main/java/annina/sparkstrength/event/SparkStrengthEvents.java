package annina.sparkstrength.event;

import annina.sparkstrength.component.detective.DetectiveCasePlayerComponent;
import annina.sparkstrength.component.collision.PlayerCollisionGraceWorldComponent;
import annina.sparkstrength.component.demonhunter.DemonHunterSniffPlayerComponent;
import annina.sparkstrength.component.morphling.MorphBodyDisguiseWorldComponent;
import annina.sparkstrength.component.noisemaker.NoisemakerGlowTargetComponent;
import annina.sparkstrength.component.noisemaker.NoisemakerGlowUserComponent;
import annina.sparkstrength.component.phantom.PhantomBackpackTargetComponent;
import annina.sparkstrength.component.phantom.PhantomBackpackUserComponent;
import annina.sparkstrength.component.professor.ProfessorSerumTargetComponent;
import annina.sparkstrength.component.professor.ProfessorSerumUserComponent;
import annina.sparkstrength.item.m67.M67RoundService;
import annina.sparkstrength.component.reporter.ReporterCommunicationComponent;
import annina.sparkstrength.role.noisemaker.NoisemakerGlowService;
import annina.sparkstrength.role.phantom.PhantomBackpackService;
import annina.sparkstrength.role.attendant.AttendantFlashlightService;
import annina.sparkstrength.role.attendant.DoorLogService;
import annina.sparkstrength.role.bomber.drone.DroneCombatService;
import annina.sparkstrength.role.bomber.drone.DronePilotService;
import annina.sparkstrength.role.bomber.drone.DroneService;
import annina.sparkstrength.role.bomber.BomberTrapService;
import annina.sparkstrength.role.coroner.CoronerEconomyService;
import annina.sparkstrength.role.coroner.CoronerEngineerService;
import annina.sparkstrength.role.coroner.CoronerService;
import annina.sparkstrength.role.coroner.CoronerShopService;
import annina.sparkstrength.role.corruptcop.CorruptCopAbilityService;
import annina.sparkstrength.role.corruptcop.CorruptCopFeatureService;
import annina.sparkstrength.role.detective.DetectiveCaseService;
import annina.sparkstrength.role.demonhunter.DemonHunterSniffService;
import annina.sparkstrength.role.economy.RoleEconomyService;
import annina.sparkstrength.role.economy.KillerTeamEconomyService;
import annina.sparkstrength.role.engineer.EngineerCaptureDeviceService;
import annina.sparkstrength.role.engineer.EngineerPowerRestorationService;
import annina.sparkstrength.role.engineer.EngineerShopService;
import annina.sparkstrength.role.morphling.MorphlingService;
import annina.sparkstrength.role.morphling.MorphlingShopService;
import annina.sparkstrength.role.pathogen.PathogenFeatureService;
import annina.sparkstrength.role.taotie.TaotieHeadFeatureService;
import annina.sparkstrength.role.poisoner.PoisonerEconomyService;
import annina.sparkstrength.role.professor.ProfessorSerumShopService;
import annina.sparkstrength.role.recaller.RecallerEconomyService;
import annina.sparkstrength.role.recaller.RecallerShopService;
import annina.sparkstrength.role.reporter.ReporterCommunicationManager;
import annina.sparkstrength.role.toxicologist.ToxicologistAntidoteService;
import annina.sparkstrength.role.toxicologist.ToxicologistBluePassiveService;
import annina.sparkstrength.role.toxicologist.ToxicologistBlueVitriolService;
import annina.sparkstrength.role.toxicologist.ToxicologistCapsuleShop;
import annina.sparkstrength.role.attendant.FlashlightBlackoutService;
import annina.sparkstrength.role.veteran.VeteranBlackoutService;
import annina.sparkstrength.role.veteran.VeteranEconomyService;
import annina.sparkstrength.role.veteran.VeteranKnifeService;
import annina.sparkstrength.role.veteran.VeteranShopService;
import annina.sparkstrength.role.vulture.VultureSkateboardService;
import annina.sparkstrength.role.shadowjester.ShadowJesterShowdownService;
import annina.sparkstrength.role.waiter.WaiterTaskRevealService;
import annina.sparkstrength.role.timekeeper.TimekeeperWatchService;
import annina.sparkstrength.component.timekeeper.TimekeeperWatchComponent;
import annina.sparkstrength.tablet.TabletShopService;
import annina.sparkstrength.tablet.TabletStateService;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import annina.sparkstrength.role.serialkiller.SerialKillerCooldownService;
import annina.sparkstrength.role.serialkiller.SerialPistolGuardService;

/**
 * 统一注册 SparkStrength 的服务端事件。
 */
public final class SparkStrengthEvents {
    private SparkStrengthEvents() {
    }

    public static void register() {
        M67RoundService.initialize();
        DroneService.register();
        DroneCombatService.register();
        DronePilotService.register();
        CorruptCopFeatureService.register();
        CorruptCopAbilityService.register();
        CoronerEngineerService.register();
        CoronerService.register();
        CoronerShopService.register();
        DetectiveCaseService.register();
        DoorLogService.register();
        FlashlightBlackoutService.register();
        VeteranBlackoutService.register();
        RoleEconomyService.register();
        KillerTeamEconomyService.register();
        EngineerPowerRestorationService.register();
        EngineerShopService.register();
        MorphlingService.register();
        MorphlingShopService.register();
        PathogenFeatureService.register();
        TaotieHeadFeatureService.register();
        PoisonerEconomyService.register();
        ProfessorSerumShopService.register();
        ReporterCommunicationManager.register();
        RecallerShopService.register();
        ToxicologistAntidoteService.register();
        ToxicologistBluePassiveService.register();
        ToxicologistCapsuleShop.register();
        ToxicologistBlueVitriolService.register();
        TabletShopService.register();
        TabletStateService.register();
        VeteranShopService.register();
        VultureSkateboardService.register();
        // 老兵经济服务同时注册死亡前阵营快照和死亡后金币结算。
        VeteranEconomyService.register();
        ShadowJesterShowdownService.register();
        // 任务完成后记录 30 秒的服务员专属透视状态。
        WaiterTaskRevealService.register();
        TimekeeperWatchService.register();
        SerialKillerCooldownService.register();
        // Serial pistols exist only during their Serial Killer's psycho: sweep + world-use guard.
        // 连环手枪只在其连环杀手疯魔期间存在：清扫与世界交互防护。
        SerialPistolGuardService.register();
        // 回溯者被动收入需要按世界 tick 定时结算，注册在服务端世界 tick 末尾。
        ServerTickEvents.END_WORLD_TICK.register(CoronerEconomyService::tick);
        ServerTickEvents.END_WORLD_TICK.register(CoronerService::tick);
        ServerTickEvents.END_WORLD_TICK.register(RecallerEconomyService::tick);
        ServerTickEvents.END_WORLD_TICK.register(TabletStateService::tick);
        // Mid-round tablet grant reconciliation (replaces a RoleAssigned-time grant; see TabletShopService#tick).
        // 局中平板补发对账（取代 RoleAssigned 时发放；见 TabletShopService#tick）。
        ServerTickEvents.END_WORLD_TICK.register(TabletShopService::tick);
        // Must stay on END_WORLD_TICK: it reads what SparkTraits' player component tick did earlier in the same world tick.
        // 必须挂在 END_WORLD_TICK：它读取同一世界 tick 内 SparkTraits 玩家组件刚做的结果。
        ServerTickEvents.END_WORLD_TICK.register(ToxicologistBluePassiveService::tick);
        ServerTickEvents.END_WORLD_TICK.register(VeteranBlackoutService::tick);

        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                CorruptCopAbilityService.reset(serverPlayer);
                CoronerService.assignForRole(serverPlayer, role);
                RoleEconomyService.assignForRole(serverPlayer, role);
                AttendantFlashlightService.assignForRole(serverPlayer, role);
                DetectiveCaseService.assignForRole(serverPlayer, role);
                DemonHunterSniffService.assignForRole(serverPlayer, role);
                MorphlingService.assignForRole(serverPlayer, role);
                PhantomBackpackService.assignForRole(serverPlayer, role);
                ToxicologistAntidoteService.clearPlayer(serverPlayer);
                VeteranKnifeService.assignForRole(serverPlayer, role);
                if (role == org.agmas.noellesroles.Noellesroles.TIMEKEEPER) {
                    TimekeeperWatchComponent.KEY.get(serverPlayer).reset();
                    serverPlayer.giveItemStack(annina.sparkstrength.SparkStrengthItems.dyingWatch().getDefaultStack());
                }
            }
        });

        ResetPlayer.EVENT.register(player -> {
            // Wathe fires ResetPlayer in baseInitialize (round start) and in resetPlayer, which serverTick runs while no
            // game is running for players still inside the play area; never on death.
            // Wathe 只在开局 baseInitialize，以及非对局期间 serverTick 对仍在游戏区域内玩家调用的 resetPlayer 中触发 ResetPlayer；死亡时不会触发。
            // 这里把点亮冷却和目标倒计时都清掉，避免跨局残留。
            NoisemakerGlowUserComponent.KEY.get(player).reset();
            NoisemakerGlowTargetComponent.KEY.get(player).reset();
            PhantomBackpackUserComponent.KEY.get(player).reset();
            PhantomBackpackTargetComponent.KEY.get(player).reset();
            ProfessorSerumUserComponent.KEY.get(player).reset();
            ProfessorSerumTargetComponent.KEY.get(player).reset();
            ReporterCommunicationComponent.KEY.get(player).reset();
            if (player instanceof ServerPlayerEntity serverPlayer) {
                WaiterTaskRevealService.reset(serverPlayer);
                TimekeeperWatchComponent.KEY.get(serverPlayer).reset();
            }
            DetectiveCasePlayerComponent.KEY.get(player).clearAll();
            DemonHunterSniffPlayerComponent.KEY.get(player).clearSniff();
            if (player instanceof ServerPlayerEntity serverPlayer) {
                CorruptCopAbilityService.reset(serverPlayer);
                CoronerService.clearPlayer(serverPlayer);
                EngineerCaptureDeviceService.clearPlayer(serverPlayer);
                MorphlingService.reset(serverPlayer);
                ToxicologistAntidoteService.clearPlayer(serverPlayer);
                ToxicologistBluePassiveService.clearPlayer(serverPlayer);
                VeteranKnifeService.reset(serverPlayer);
            }
        });

        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            // 大嗓门死亡后的“杀手发光 15 秒”是被动效果，不写入回放。
            NoisemakerGlowService.glowKillerWhenNoisemakerDies(victim, killer);
            CoronerService.afterKill(victim);
            MorphlingService.afterKill(victim, killer, deathReason);
            ToxicologistBluePassiveService.clearPlayer(victim);
        });

        GameEvents.ON_FINISH_FINALIZE.register((world, gameComponent) -> {
            if (world instanceof ServerWorld serverWorld) {
                DetectiveCaseService.clearRoundState(serverWorld);
                // 对局结束后清掉开局 tick，避免下一局开始前沿用上一局的保护时间。
                PlayerCollisionGraceWorldComponent.KEY.get(serverWorld).clearRoundState();
                MorphBodyDisguiseWorldComponent.KEY.get(serverWorld).clearRoundState();
                BomberTrapService.clearRoundState(serverWorld);
                EngineerCaptureDeviceService.clearRoundState(serverWorld);
                TabletStateService.clearRoundState(serverWorld);
                VeteranBlackoutService.clear(serverWorld);
                VeteranEconomyService.clearRoundState();
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    CorruptCopAbilityService.reset(player);
                    CoronerService.clearPlayer(player);
                    DetectiveCasePlayerComponent.KEY.get(player).clearAll();
                    EngineerCaptureDeviceService.clearPlayer(player);
                    MorphlingService.reset(player);
                    PhantomBackpackService.clearPlayer(player);
                    ToxicologistAntidoteService.clearPlayer(player);
                    ToxicologistBluePassiveService.clearPlayer(player);
                    ProfessorSerumUserComponent.KEY.get(player).reset();
                    ProfessorSerumTargetComponent.KEY.get(player).reset();
                    ReporterCommunicationComponent.KEY.get(player).reset();
                    WaiterTaskRevealService.reset(player);
                    DemonHunterSniffService.clearPlayer(player);
                    VeteranKnifeService.reset(player);
                    TimekeeperWatchComponent.KEY.get(player).reset();
                }
            }
        });

        GameEvents.ON_FINISH_INITIALIZE.register((world, gameComponent) -> {
            if (world instanceof ServerWorld serverWorld) {
                // 该事件发生在 Wathe 完成角色/地图初始化、切换 ACTIVE 之前，正好作为本局保护期起点。
                PlayerCollisionGraceWorldComponent.KEY.get(serverWorld).markRoundStart(serverWorld.getTime());
                MorphBodyDisguiseWorldComponent.KEY.get(serverWorld).clearRoundState();
                BomberTrapService.clearRoundState(serverWorld);
                EngineerCaptureDeviceService.clearRoundState(serverWorld);
                // Also clear at round start: an aborted round or restart must not carry tablet chat/suspects forward.
                // 开局时也清理：异常结束或重启的对局不能把平板聊天/嫌疑人带入下一局。
                TabletStateService.clearRoundState(serverWorld);
                // After the clear (it resets the per-round grant set): every tablet-eligible player gets one free tablet.
                // 必须在清理之后（清理会重置本局发放记录）：每名符合条件的玩家免费获得一台平板。
                TabletShopService.grantStarterTablets(serverWorld, gameComponent);
                // Crime-scene snapshots are round state; clear before granting kits so a new round starts empty.
                // 案发快照属于单局状态；先清空再发放侦探道具，保证新一局从空白开始。
                DetectiveCaseService.clearRoundState(serverWorld);
                DetectiveCaseService.grantStarterKits(serverWorld);
                VeteranEconomyService.clearRoundState();
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    // 新一局初始化时清理上一局可能残留的服务员任务透视倒计时。
                    WaiterTaskRevealService.reset(player);
                    TimekeeperWatchComponent.KEY.get(player).reset();
                }
            }
        });
    }
}
