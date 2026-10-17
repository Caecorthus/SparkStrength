package annina.sparkstrength;

import annina.sparkstrength.block.CircuitBreakerBlock;
import annina.sparkstrength.block.EngineUnitBlock;
import annina.sparkstrength.block.JunctionBoxBlock;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.MapColor;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

/**
 * SparkStrength blocks and their block items. Both are map fixtures: unbreakable outside creative, no drops.
 * SparkStrength 的方块及其方块物品。两者都是地图设施：创造模式以外无法破坏，不掉落。
 */
public final class SparkStrengthBlocks {
    public static final Identifier JUNCTION_BOX_ID = SparkStrength.id("junction_box");
    public static final Identifier CIRCUIT_BREAKER_ID = SparkStrength.id("circuit_breaker");
    public static final Identifier ENGINE_UNIT_ID = SparkStrength.id("engine_unit");

    private static boolean registered;
    private static Block junctionBox;
    private static Block circuitBreaker;
    private static Block engineUnit;

    private SparkStrengthBlocks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        junctionBox = registerWithItem(JUNCTION_BOX_ID, new JunctionBoxBlock(fixtureSettings()));
        circuitBreaker = registerWithItem(CIRCUIT_BREAKER_ID, new CircuitBreakerBlock(fixtureSettings()));
        engineUnit = registerWithItem(ENGINE_UNIT_ID, new EngineUnitBlock(fixtureSettings()));
        registered = true;
    }

    public static Block junctionBox() {
        if (junctionBox == null) {
            throw new IllegalStateException("SparkStrength blocks are not registered yet");
        }
        return junctionBox;
    }

    public static Block circuitBreaker() {
        if (circuitBreaker == null) {
            throw new IllegalStateException("SparkStrength blocks are not registered yet");
        }
        return circuitBreaker;
    }

    public static Block engineUnit() {
        if (engineUnit == null) {
            throw new IllegalStateException("SparkStrength blocks are not registered yet");
        }
        return engineUnit;
    }

    /** Any block the sabotage system scans for. / 破坏系统会扫描的任意方块。 */
    public static boolean isSabotageFixture(net.minecraft.block.BlockState state) {
        return state.isOf(junctionBox()) || state.isOf(circuitBreaker()) || state.isOf(engineUnit());
    }

    private static AbstractBlock.Settings fixtureSettings() {
        return AbstractBlock.Settings.create()
                .mapColor(MapColor.IRON_GRAY)
                .strength(-1.0F, 3_600_000.0F)
                .sounds(BlockSoundGroup.METAL)
                .nonOpaque()
                .dropsNothing()
                .pistonBehavior(PistonBehavior.BLOCK);
    }

    private static Block registerWithItem(Identifier id, Block block) {
        Block registeredBlock = Registry.register(Registries.BLOCK, id, block);
        Registry.register(Registries.ITEM, id, new BlockItem(registeredBlock, new Item.Settings()));
        return registeredBlock;
    }
}
