package annina.sparkstrength.event;

import annina.sparkstrength.component.detective.CriminologistPlayerComponent;
import annina.sparkstrength.component.detective.CriminologistWorldComponent;
import annina.sparkstrength.component.demonhunter.DemonHunterSniffPlayerComponent;
import annina.sparkstrength.component.morphling.MorphBodyDisguiseWorldComponent;
import annina.sparkstrength.component.noisemaker.NoisemakerGlowTargetComponent;
import annina.sparkstrength.component.noisemaker.NoisemakerGlowUserComponent;
import annina.sparkstrength.component.professor.ProfessorSerumTargetComponent;
import annina.sparkstrength.component.professor.ProfessorSerumUserComponent;
import annina.sparkstrength.role.noisemaker.NoisemakerGlowService;
import annina.sparkstrength.role.attendant.AttendantFlashlightService;
import annina.sparkstrength.role.corruptcop.CorruptCopFeatureService;
import annina.sparkstrength.role.detective.CriminologistService;
import annina.sparkstrength.role.demonhunter.DemonHunterSniffService;
import annina.sparkstrength.role.economy.RoleEconomyService;
import annina.sparkstrength.role.engineer.EngineerCaptureDeviceService;
import annina.sparkstrength.role.engineer.EngineerPowerRestorationService;
import annina.sparkstrength.role.engineer.EngineerShopService;
import annina.sparkstrength.role.morphling.MorphlingService;
import annina.sparkstrength.role.morphling.MorphlingShopService;
import annina.sparkstrength.role.poisoner.PoisonerEconomyService;
import annina.sparkstrength.role.professor.ProfessorSerumShopService;
import annina.sparkstrength.role.toxicologist.ToxicologistCapsuleShop;
import annina.sparkstrength.role.attendant.FlashlightBlackoutService;
import annina.sparkstrength.role.veteran.VeteranBlackoutService;
import annina.sparkstrength.role.veteran.VeteranEconomyService;
import annina.sparkstrength.role.veteran.VeteranKnifeService;
import annina.sparkstrength.role.veteran.VeteranShopService;
import annina.sparkstrength.tablet.TabletShopService;
import annina.sparkstrength.tablet.TabletStateService;
import dev.doctor4t.wathe.api.event.GameEvents;
import dev.doctor4t.wathe.api.event.KillPlayer;
import dev.doctor4t.wathe.api.event.ResetPlayer;
import dev.doctor4t.wathe.api.event.RoleAssigned;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * 统一注册 SparkStrength 的服务端事件。
 */
public final class SparkStrengthEvents {
    private SparkStrengthEvents() {
    }

    public static void register() {
        CorruptCopFeatureService.register();
        CriminologistService.register();
        FlashlightBlackoutService.register();
        RoleEconomyService.register();
        EngineerPowerRestorationService.register();
        EngineerShopService.register();
        MorphlingService.register();
        MorphlingShopService.register();
        PoisonerEconomyService.register();
        ProfessorSerumShopService.register();
        ToxicologistCapsuleShop.register();
        TabletShopService.register();
        VeteranShopService.register();
        ServerTickEvents.END_WORLD_TICK.register(TabletStateService::tick);
        ServerTickEvents.END_WORLD_TICK.register(VeteranBlackoutService::tick);

        RoleAssigned.EVENT.register((player, role) -> {
            if (player instanceof ServerPlayerEntity serverPlayer) {
                RoleEconomyService.assignForRole(serverPlayer, role);
                AttendantFlashlightService.assignForRole(serverPlayer, role);
                CriminologistService.assignForRole(serverPlayer, role);
                DemonHunterSniffService.assignForRole(serverPlayer, role);
                MorphlingService.assignForRole(serverPlayer, role);
                VeteranKnifeService.assignForRole(serverPlayer, role);
            }
        });

        ResetPlayer.EVENT.register(player -> {
            // Wathe 在死亡、重置玩家、新一局开始等场景会触发 ResetPlayer。
            // 这里把点亮冷却和目标倒计时都清掉，避免跨局残留。
            NoisemakerGlowUserComponent.KEY.get(player).reset();
            NoisemakerGlowTargetComponent.KEY.get(player).reset();
            ProfessorSerumUserComponent.KEY.get(player).reset();
            ProfessorSerumTargetComponent.KEY.get(player).reset();
            CriminologistPlayerComponent.KEY.get(player).clearAll();
            DemonHunterSniffPlayerComponent.KEY.get(player).clearSniff();
            if (player instanceof ServerPlayerEntity serverPlayer) {
                EngineerCaptureDeviceService.clearPlayer(serverPlayer);
                MorphlingService.reset(serverPlayer);
                VeteranKnifeService.reset(serverPlayer);
            }
        });

        KillPlayer.AFTER.register((victim, killer, deathReason) -> {
            // 大嗓门死亡后的“杀手发光 15 秒”是被动效果，不写入回放。
            NoisemakerGlowService.glowKillerWhenNoisemakerDies(victim, killer);
            CriminologistService.afterKill(victim, killer, deathReason);
            MorphlingService.afterKill(victim, killer, deathReason);
            VeteranEconomyService.afterKill(victim, killer, deathReason);
        });

        GameEvents.ON_FINISH_FINALIZE.register((world, gameComponent) -> {
            if (world instanceof ServerWorld serverWorld) {
                CriminologistWorldComponent.KEY.get(serverWorld).clearRoundState();
                MorphBodyDisguiseWorldComponent.KEY.get(serverWorld).clearRoundState();
                EngineerCaptureDeviceService.clearRoundState(serverWorld);
                TabletStateService.clearRoundState(serverWorld);
                VeteranBlackoutService.clear(serverWorld);
                for (ServerPlayerEntity player : serverWorld.getPlayers()) {
                    CriminologistPlayerComponent.KEY.get(player).clearAll();
                    EngineerCaptureDeviceService.clearPlayer(player);
                    MorphlingService.reset(player);
                    ProfessorSerumUserComponent.KEY.get(player).reset();
                    ProfessorSerumTargetComponent.KEY.get(player).reset();
                    DemonHunterSniffService.clearPlayer(player);
                    VeteranKnifeService.reset(player);
                }
            }
        });

        GameEvents.ON_FINISH_INITIALIZE.register((world, gameComponent) -> {
            if (world instanceof ServerWorld serverWorld) {
                MorphBodyDisguiseWorldComponent.KEY.get(serverWorld).clearRoundState();
                EngineerCaptureDeviceService.clearRoundState(serverWorld);
            }
        });
    }
}
