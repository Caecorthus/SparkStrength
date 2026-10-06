package annina.sparkstrength;

import annina.sparkstrength.item.BlueBelladonnaItem;
import annina.sparkstrength.item.BlueVitriolItem;
import annina.sparkstrength.item.CaptureDeviceItem;
import annina.sparkstrength.item.CapsuleItem;
import annina.sparkstrength.item.CaseFolderItem;
import annina.sparkstrength.item.CoronerBodyBagItem;
import annina.sparkstrength.item.AromaOrbItem;
import annina.sparkstrength.item.CoolingOilItem;
import annina.sparkstrength.item.DroneItem;
import annina.sparkstrength.item.EmberSugarItem;
import annina.sparkstrength.item.FlashlightItem;
import annina.sparkstrength.item.GhostflameBittersItem;
import annina.sparkstrength.item.M67Item;
import annina.sparkstrength.item.MagnifierItem;
import annina.sparkstrength.item.MorphDeviceItem;
import annina.sparkstrength.item.MorphReagentItem;
import annina.sparkstrength.item.PowerRestorationItem;
import annina.sparkstrength.item.ProfessorSerumItem;
import annina.sparkstrength.item.SkateboardItem;
import annina.sparkstrength.item.TVirusItem;
import annina.sparkstrength.item.TabletItem;
import annina.sparkstrength.item.VirusItem;
import annina.sparkstrength.item.ZephyrPerfumeItem;
import annina.sparkstrength.role.bomber.drone.DroneKind;
import annina.sparkstrength.role.perfumer.PerfumerRules;
import annina.sparkstrength.role.professor.ProfessorSerumType;
import annina.sparkstrength.role.toxicologist.ToxicologistBlueRules;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.item.IngredientItem;

public final class SparkStrengthItems {
    public static final Identifier CAPSULE_ID = SparkStrength.id("capsule");
    public static final Identifier FLASHLIGHT_ID = SparkStrength.id("flashlight");
    public static final Identifier TABLET_ID = SparkStrength.id("tablet");
    public static final Identifier INVISIBILITY_SERUM_ID = SparkStrength.id("invisibility_serum");
    public static final Identifier DOORPASSING_POTION_ID = SparkStrength.id("doorpassing_potion");
    public static final Identifier SEDATIVE_ID = SparkStrength.id("sedative");
    public static final Identifier TRUTH_SERUM_ID = SparkStrength.id("truth_serum");
    public static final Identifier CAPTURE_DEVICE_ID = SparkStrength.id("capture_device");
    public static final Identifier POWER_RESTORATION_ID = SparkStrength.id("power_restoration");
    public static final Identifier MORPH_REAGENT_ID = SparkStrength.id("morph_reagent");
    public static final Identifier MORPH_DEVICE_ID = SparkStrength.id("morph_device");
    public static final Identifier CORONER_BODY_BAG_ID = SparkStrength.id("coroner_body_bag");
    public static final Identifier M67_ID = SparkStrength.id("m67");
    public static final Identifier MAGNIFIER_ID = SparkStrength.id("magnifier");
    public static final Identifier CASE_FOLDER_ID = SparkStrength.id("case_folder");
    public static final Identifier COOLING_OIL_ID = SparkStrength.id("cooling_oil");
    public static final Identifier AROMA_ORB_ID = SparkStrength.id("aroma_orb");
    public static final Identifier ZEPHYR_PERFUME_ID = SparkStrength.id("zephyr_perfume");
    public static final Identifier BLUE_VITRIOL_ID = SparkStrength.id("blue_vitriol");
    public static final Identifier BLUE_BELLADONNA_ID = SparkStrength.id("blue_belladonna");
    public static final Identifier GRENADE_DRONE_ID = SparkStrength.id("grenade_drone");
    public static final Identifier BOMB_DRONE_ID = SparkStrength.id("bomb_drone");
    public static final Identifier SKATEBOARD_ID = SparkStrength.id("skateboard");
    public static final Identifier GHOSTFLAME_BITTERS_ID = SparkStrength.id("ghostflame_bitters");
    public static final Identifier EMBER_SUGAR_ID = SparkStrength.id("ember_sugar");
    public static final Identifier VIRUS_ID = SparkStrength.id("virus");
    public static final Identifier T_VIRUS_ID = SparkStrength.id("t_virus");
    private static Item m67;
    private static Item capsule;
    private static Item flashlight;
    private static Item tablet;
    private static Item invisibilitySerum;
    private static Item doorpassingPotion;
    private static Item sedative;
    private static Item truthSerum;
    private static Item captureDevice;
    private static Item powerRestoration;
    private static Item morphReagent;
    private static Item morphDevice;
    private static Item coronerBodyBag;
    private static Item magnifier;
    private static Item caseFolder;
    private static Item coolingOil;
    private static Item aromaOrb;
    private static Item zephyrPerfume;
    private static Item blueVitriol;
    private static Item blueBelladonna;
    private static Item grenadeDrone;
    private static Item bombDrone;
    private static Item skateboard;
    private static Item ghostflameBitters;
    private static Item emberSugar;
    private static Item virus;
    private static Item tVirus;
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
        invisibilitySerum = Registry.register(
                Registries.ITEM,
                INVISIBILITY_SERUM_ID,
                new ProfessorSerumItem(new Item.Settings().maxCount(1), ProfessorSerumType.INVISIBILITY)
        );
        doorpassingPotion = Registry.register(
                Registries.ITEM,
                DOORPASSING_POTION_ID,
                new ProfessorSerumItem(new Item.Settings().maxCount(1), ProfessorSerumType.DOORPASSING)
        );
        sedative = Registry.register(
                Registries.ITEM,
                SEDATIVE_ID,
                new ProfessorSerumItem(new Item.Settings().maxCount(1), ProfessorSerumType.SEDATIVE)
        );
        truthSerum = Registry.register(
                Registries.ITEM,
                TRUTH_SERUM_ID,
                new ProfessorSerumItem(new Item.Settings().maxCount(1), ProfessorSerumType.TRUTH)
        );
        captureDevice = Registry.register(
                Registries.ITEM,
                CAPTURE_DEVICE_ID,
                new CaptureDeviceItem(new Item.Settings().maxCount(1))
        );
        powerRestoration = Registry.register(
                Registries.ITEM,
                POWER_RESTORATION_ID,
                new PowerRestorationItem(new Item.Settings().maxCount(1))
        );
        morphReagent = Registry.register(
                Registries.ITEM,
                MORPH_REAGENT_ID,
                new MorphReagentItem(new Item.Settings().maxCount(1))
        );
        morphDevice = Registry.register(
                Registries.ITEM,
                MORPH_DEVICE_ID,
                new MorphDeviceItem(new Item.Settings().maxCount(1))
        );
        coronerBodyBag = Registry.register(
                Registries.ITEM,
                CORONER_BODY_BAG_ID,
                new CoronerBodyBagItem(new Item.Settings().maxCount(1))
        );
        m67 = Registry.register(
                Registries.ITEM,
                M67_ID,
                new M67Item(new Item.Settings().maxCount(1))
        );
        magnifier = Registry.register(
                Registries.ITEM,
                MAGNIFIER_ID,
                new MagnifierItem(new Item.Settings().maxCount(1))
        );
        caseFolder = Registry.register(
                Registries.ITEM,
                CASE_FOLDER_ID,
                new CaseFolderItem(new Item.Settings().maxCount(1))
        );
        coolingOil = Registry.register(
                Registries.ITEM,
                COOLING_OIL_ID,
                new CoolingOilItem(new Item.Settings().maxCount(PerfumerRules.THROWABLE_MAX_STACK))
        );
        aromaOrb = Registry.register(
                Registries.ITEM,
                AROMA_ORB_ID,
                new AromaOrbItem(new Item.Settings().maxCount(PerfumerRules.THROWABLE_MAX_STACK))
        );
        zephyrPerfume = Registry.register(
                Registries.ITEM,
                ZEPHYR_PERFUME_ID,
                new ZephyrPerfumeItem(new Item.Settings().maxCount(1))
        );
        blueVitriol = Registry.register(
                Registries.ITEM,
                BLUE_VITRIOL_ID,
                new BlueVitriolItem(new Item.Settings().maxCount(ToxicologistBlueRules.MAX_STACK))
        );
        blueBelladonna = Registry.register(
                Registries.ITEM,
                BLUE_BELLADONNA_ID,
                new BlueBelladonnaItem(new Item.Settings()
                        .maxCount(ToxicologistBlueRules.MAX_STACK)
                        .food(BlueBelladonnaItem.FOOD))
        );
        grenadeDrone = Registry.register(
                Registries.ITEM,
                GRENADE_DRONE_ID,
                new DroneItem(DroneKind.GRENADE, new Item.Settings().maxCount(1))
        );
        bombDrone = Registry.register(
                Registries.ITEM,
                BOMB_DRONE_ID,
                new DroneItem(DroneKind.BOMB, new Item.Settings().maxCount(1))
        );
        skateboard = Registry.register(
                Registries.ITEM,
                SKATEBOARD_ID,
                new SkateboardItem(new Item.Settings().maxCount(1))
        );
        ghostflameBitters = registerIngredient(
                GHOSTFLAME_BITTERS_ID,
                new GhostflameBittersItem(new Item.Settings().maxCount(1))
        );
        emberSugar = registerIngredient(
                EMBER_SUGAR_ID,
                new EmberSugarItem(new Item.Settings().maxCount(1))
        );
        virus = Registry.register(
                Registries.ITEM,
                VIRUS_ID,
                new VirusItem(new Item.Settings().maxCount(16))
        );
        tVirus = Registry.register(
                Registries.ITEM,
                T_VIRUS_ID,
                new TVirusItem(new Item.Settings().maxCount(1))
        );
        registered = true;
    }

    public static Item m67() {
        if (m67 == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return m67;
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

    public static Item invisibilitySerum() {
        if (invisibilitySerum == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return invisibilitySerum;
    }

    public static Item doorpassingPotion() {
        if (doorpassingPotion == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return doorpassingPotion;
    }

    public static Item sedative() {
        if (sedative == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return sedative;
    }

    public static Item truthSerum() {
        if (truthSerum == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return truthSerum;
    }

    public static Item captureDevice() {
        if (captureDevice == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return captureDevice;
    }

    public static Item powerRestoration() {
        if (powerRestoration == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return powerRestoration;
    }

    public static Item morphReagent() {
        if (morphReagent == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return morphReagent;
    }

    public static Item morphDevice() {
        if (morphDevice == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return morphDevice;
    }

    public static Item coronerBodyBag() {
        if (coronerBodyBag == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return coronerBodyBag;
    }

    public static Item magnifier() {
        if (magnifier == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return magnifier;
    }

    public static Item caseFolder() {
        if (caseFolder == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return caseFolder;
    }

    public static Item coolingOil() {
        if (coolingOil == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return coolingOil;
    }

    public static Item aromaOrb() {
        if (aromaOrb == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return aromaOrb;
    }

    public static Item zephyrPerfume() {
        if (zephyrPerfume == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return zephyrPerfume;
    }

    public static Item blueVitriol() {
        if (blueVitriol == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return blueVitriol;
    }

    public static Item blueBelladonna() {
        if (blueBelladonna == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return blueBelladonna;
    }

    public static Item grenadeDrone() {
        if (grenadeDrone == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return grenadeDrone;
    }

    public static Item bombDrone() {
        if (bombDrone == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return bombDrone;
    }

    public static Item skateboard() {
        if (skateboard == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return skateboard;
    }

    public static Item ghostflameBitters() {
        if (ghostflameBitters == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return ghostflameBitters;
    }

    public static Item emberSugar() {
        if (emberSugar == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return emberSugar;
    }

    /**
     * Bartender spices also join NoellesRoles' ingredient registry, which a base spirit uses to resolve the ids in
     * its NBT (effects, naming, tooltip).
     * 酒保调料同时登记到 NoellesRoles 的调剂注册表；基酒靠它解析 NBT 里的 id（效果、命名、提示）。
     */
    private static Item registerIngredient(Identifier id, IngredientItem ingredient) {
        Item item = Registry.register(Registries.ITEM, id, ingredient);
        IngredientItem.register(ingredient);
        return item;
    }

    public static Item drone(DroneKind kind) {
        return kind == DroneKind.BOMB ? bombDrone() : grenadeDrone();
    }

    public static Item virus() {
        if (virus == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return virus;
    }

    public static Item tVirus() {
        if (tVirus == null) {
            throw new IllegalStateException("SparkStrength items are not registered yet");
        }
        return tVirus;
    }
}
