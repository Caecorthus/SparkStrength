package annina.sparkstrength.component.economy;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.economy.KillerTeamEconomyRules;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.World;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

/**
 * Server-owned round purse; deliberately not an auto-synced CCA component.
 * 服务端持有的回合共享钱包，刻意不通过 CCA 自动广播。
 */
public final class KillerTeamEconomyWorldComponent implements Component {
    public static final ComponentKey<KillerTeamEconomyWorldComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("killer_team_economy"), KillerTeamEconomyWorldComponent.class
    );

    private boolean initialized;
    private int openingMembers;
    private int balance;

    public KillerTeamEconomyWorldComponent(World world) {
    }

    public boolean isEnabled() {
        return KillerTeamEconomyRules.isEnabled(initialized, openingMembers);
    }

    public int getBalance() {
        return balance;
    }

    public void initializeRound(int openingMembers) {
        this.initialized = true;
        this.openingMembers = Math.max(0, openingMembers);
        this.balance = 0;
    }

    public void clearRoundState() {
        initialized = false;
        openingMembers = 0;
        balance = 0;
    }

    public boolean recordIncome(long actualIncome) {
        return recordIncome(actualIncome, false);
    }

    public boolean recordIncome(long actualIncome, boolean teamFirst) {
        if (!isEnabled()) {
            return false;
        }
        int previous = balance;
        balance = KillerTeamEconomyRules.availableBalance(
                balance, KillerTeamEconomyRules.contribution(actualIncome, openingMembers, teamFirst)
        );
        return balance != previous;
    }

    public boolean spend(int amount) {
        if (!isEnabled() || amount < 0 || amount > balance) {
            return false;
        }
        balance -= amount;
        return true;
    }

    @Override
    public void writeToNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        tag.putBoolean("Initialized", initialized);
        tag.putInt("OpeningMembers", openingMembers);
        tag.putInt("Balance", balance);
    }

    @Override
    public void readFromNbt(NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        clearRoundState();
        // Older saves must wait for a genuine new round; never reconstruct N from mid-round survivors.
        // 旧存档必须等待真正的新回合，绝不使用回合中途的幸存者人数重建 N。
        if (!tag.contains("Initialized", NbtElement.BYTE_TYPE)
                || !tag.contains("OpeningMembers", NbtElement.INT_TYPE)
                || !tag.contains("Balance", NbtElement.INT_TYPE)
                || !tag.getBoolean("Initialized")
                || tag.getInt("OpeningMembers") < 0 || tag.getInt("Balance") < 0) {
            return;
        }
        initialized = true;
        openingMembers = tag.getInt("OpeningMembers");
        balance = isEnabled() ? tag.getInt("Balance") : 0;
    }
}
