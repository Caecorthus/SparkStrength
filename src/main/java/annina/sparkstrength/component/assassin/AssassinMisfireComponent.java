package annina.sparkstrength.component.assassin;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.assassin.AssassinGuessRules;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

/**
 * How many wrong guesses this Assassin has been forgiven this round. NoellesRoles' {@code AssassinPlayerComponent}
 * keeps charges and the cooldown; this only adds the count the misfire rule needs, synced to the owner so the guess
 * screen can say whether the next miss is lethal.
 * 这名刺客本局已被宽恕的猜错次数。次数与冷却仍由 NoellesRoles 的 AssassinPlayerComponent 保存；这里只补充反噬规则需要的计数，
 * 并同步给本人，让猜测界面能提示下一次猜错是否致命。
 */
public final class AssassinMisfireComponent implements AutoSyncedComponent {
    public static final ComponentKey<AssassinMisfireComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("assassin_misfire"),
            AssassinMisfireComponent.class
    );

    private final PlayerEntity player;
    private int forgivenMisses;

    public AssassinMisfireComponent(PlayerEntity player) {
        this.player = player;
    }

    /** Whether the next wrong guess is still forgiven. / 下一次猜错是否仍会被宽恕。 */
    public boolean hasFreeMiss() {
        return AssassinGuessRules.missIsForgiven(forgivenMisses);
    }

    /** Spends a free miss if one is left; false means the misfire kills. / 还有免死机会就用掉一次；返回 false 表示反噬致命。 */
    public boolean forgiveMiss() {
        if (!hasFreeMiss()) {
            return false;
        }
        forgivenMisses++;
        KEY.sync(player);
        return true;
    }

    public void reset() {
        if (forgivenMisses == 0) {
            return;
        }
        forgivenMisses = 0;
        KEY.sync(player);
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (forgivenMisses > 0) {
            tag.putInt("ForgivenMisses", forgivenMisses);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        forgivenMisses = tag.contains("ForgivenMisses", NbtElement.NUMBER_TYPE)
                ? Math.max(0, tag.getInt("ForgivenMisses"))
                : 0;
    }
}
