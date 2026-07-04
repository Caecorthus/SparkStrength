package annina.sparkstrength;

import annina.sparkstrength.item.CapsuleItem;
import annina.sparkstrength.item.FlashlightItem;
import annina.sparkstrength.item.TabletItem;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class SparkStrengthItems {
    public static final Identifier CAPSULE_ID = SparkStrength.id("capsule");
    public static final Identifier FLASHLIGHT_ID = SparkStrength.id("flashlight");
    public static final Identifier TABLET_ID = SparkStrength.id("tablet");
    private static Item capsule;
    private static Item flashlight;
    private static Item tablet;
    private static boolean registered;

    private SparkStrengthItems() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        capsule = Registry.register(
                Registries.ITEM,
                CAPSULE_ID,
                new CapsuleItem(new Item.Settings().maxCount(1))
        );
        flashlight = Registry.register(
                Registries.ITEM,
                FLASHLIGHT_ID,
                new FlashlightItem(new Item.Settings().maxCount(1))
        );
        tablet = Registry.register(
                Registries.ITEM,
                TABLET_ID,
                new TabletItem(new Item.Settings().maxCount(1))
        );
        registered = true;
    }

    public static Item capsule() {
        if (capsule == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return capsule;
    }

    public static Item flashlight() {
        if (flashlight == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return flashlight;
    }

    public static Item tablet() {
        if (tablet == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return tablet;
    }
}
