package annina.sparkstrength.role.coroner;

import annina.sparkstrength.component.coroner.CoronerPlayerComponent;
import annina.sparkstrength.component.morphling.MorphMarkPlayerComponent;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.morphling.MorphlingPlayerComponent;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Read-only questions about the body identity a Coroner has borrowed, asked on both sides. A higher-priority morph
 * (NoellesRoles Morphling or the SparkStrength morph reagent) hides the disguise, so every query then answers "none".
 * 验尸官借用的尸体身份的只读查询，客户端与服务端共用。更高优先级的变形（NoellesRoles 变形怪或 SparkStrength 变形试剂）
 * 会遮住伪装，此时所有查询都回答“没有伪装”。
 *
 * <p>{@link CoronerService} keeps a one-line delegate for each query other code calls, including the ones sibling mods
 * look up by reflection on its class name.
 * {@link CoronerService} 为其它代码调用的每个查询保留一行委托，包括兄弟模组按其类名反射查找的那些。</p>
 */
public final class CoronerDisguises {
    private CoronerDisguises() {
    }

    public static @Nullable UUID activeDisguiseUuid(PlayerEntity player) {
        if (player == null) {
            return null;
        }
        CoronerPlayerComponent component = CoronerPlayerComponent.KEY.get(player);
        return component.hasActiveDisguise() ? component.activeDisguiseUuid() : null;
    }

    public static @Nullable Role activeDisguiseRole(PlayerEntity player) {
        if (player == null) {
            return null;
        }
        if (isHigherPriorityMorphActive(player)) {
            return null;
        }
        CoronerPlayerComponent component = CoronerPlayerComponent.KEY.get(player);
        Identifier roleId = component.activeDisguiseRoleId();
        if (roleId == null && component.activeDisguiseUuid() != null) {
            CoronerPlayerComponent.BodySnapshot snapshot = component.snapshot(component.activeDisguiseUuid());
            roleId = snapshot == null ? null : snapshot.roleId();
        }
        return CoronerRules.resolveRole(roleId);
    }

    public static boolean hasKillerFactionDisguise(PlayerEntity player) {
        return CoronerRules.isKillerFaction(activeDisguiseRole(player));
    }

    public static boolean hasVeteranDisguise(PlayerEntity player) {
        return CoronerRules.isVeteran(activeDisguiseRole(player));
    }

    public static boolean hasInstantSilentKnifeDisguise(PlayerEntity player) {
        return CoronerRules.grantsInstantSilentKnife(activeDisguiseRole(player));
    }

    public static boolean hasWaiterDisguise(PlayerEntity player) {
        return CoronerRules.grantsWaiterDoublePickup(activeDisguiseRole(player));
    }

    public static boolean hasCorruptCopDisguise(PlayerEntity player) {
        return CoronerRules.isCorruptCop(activeDisguiseRole(player));
    }

    public static boolean hasPoisonerDisguise(PlayerEntity player) {
        return CoronerRules.isPoisoner(activeDisguiseRole(player));
    }

    public static boolean hasToxicologistDisguise(PlayerEntity player) {
        return CoronerRules.isToxicologist(activeDisguiseRole(player));
    }

    public static boolean hasProfessorDisguise(PlayerEntity player) {
        return CoronerRules.isProfessor(activeDisguiseRole(player));
    }

    public static boolean hasAttendantDisguise(PlayerEntity player) {
        return CoronerRules.isAttendant(activeDisguiseRole(player));
    }

    public static boolean hasSurvivalMasterDisguise(PlayerEntity player) {
        return CoronerRules.isSurvivalMaster(activeDisguiseRole(player));
    }

    public static boolean hasTimekeeperDisguise(PlayerEntity player) {
        return CoronerRules.isTimekeeper(activeDisguiseRole(player));
    }

    public static boolean hasUndercoverDisguise(PlayerEntity player) {
        return CoronerRules.isUndercover(activeDisguiseRole(player));
    }

    public static boolean hasNoisemakerDisguise(PlayerEntity player) {
        return CoronerRules.isNoisemaker(activeDisguiseRole(player));
    }

    public static boolean hasSilencerDisguise(PlayerEntity player) {
        return CoronerRules.isSilencer(activeDisguiseRole(player));
    }

    public static boolean hasBartenderDisguise(PlayerEntity player) {
        return CoronerRules.isBartender(activeDisguiseRole(player));
    }

    public static boolean hasDemonHunterDisguise(PlayerEntity player) {
        return CoronerRules.isDemonHunter(activeDisguiseRole(player));
    }

    public static boolean hasEngineerDisguise(PlayerEntity player) {
        return CoronerRules.isEngineer(activeDisguiseRole(player));
    }

    public static boolean hasBomberDisguise(PlayerEntity player) {
        return CoronerRules.isBomber(activeDisguiseRole(player));
    }

    public static boolean hasKidnapperDisguise(PlayerEntity player) {
        return CoronerRules.isSparkWitchKidnapper(activeDisguiseRole(player));
    }

    public static boolean isActualCoroner(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        return CoronerRules.isCoroner(GameWorldComponent.KEY.get(player.getWorld()).getRole(player));
    }

    public static boolean canUseToxicologistDisguiseItem(PlayerEntity player) {
        return !isActualCoroner(player) || hasToxicologistDisguise(player);
    }

    public static boolean canUseProfessorDisguiseItem(PlayerEntity player) {
        return !isActualCoroner(player) || hasProfessorDisguise(player);
    }

    public static boolean canUseBartenderDisguiseItem(PlayerEntity player) {
        return !isActualCoroner(player) || hasBartenderDisguise(player);
    }

    public static boolean canUseEngineerDisguiseItem(PlayerEntity player) {
        return !isActualCoroner(player) || hasEngineerDisguise(player);
    }

    public static boolean hasNeutralDisguise(PlayerEntity player) {
        Role role = activeDisguiseRole(player);
        return role != null && role.isNeutral();
    }

    static boolean isHigherPriorityMorphActive(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        MorphlingPlayerComponent originalMorph = MorphlingPlayerComponent.KEY.get(player);
        if (originalMorph.getMorphTicks() > 0) {
            return true;
        }
        return MorphMarkPlayerComponent.KEY.get(player).isActive();
    }
}
