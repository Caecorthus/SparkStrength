package annina.sparkstrength.client.world;

import annina.sparkstrength.component.partyanimal.PartygoerCorpseComponent;
import annina.sparkstrength.entity.DroneEntity;
import annina.sparkstrength.entity.PartygoerEntity;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Which drones, Partygoers and corpses the client world holds, from Fabric's entity load/unload events (Fabric also
 * unloads every entity on respawn, rejoin and disconnect). Per-frame crosshair and label picks ask this first and skip
 * their block clips and entity queries while none is loaded, which can only find nothing then. Render thread only.
 * 客户端世界中有哪些无人机、派对客与尸体，来自 Fabric 的实体加载/卸载事件（重生、重新加入与断开连接时 Fabric 也会卸载全部
 * 实体）。逐帧的准星与标签选取先问这里，没有加载这类实体时跳过方块截断与实体查询，因为那时它们只会一无所获。仅在渲染线程使用。
 */
public final class LoadedEntities {
    private static final Set<DroneEntity> DRONES = new ReferenceOpenHashSet<>();
    private static final Set<PartygoerEntity> PARTYGOERS = new ReferenceOpenHashSet<>();
    private static final Set<PlayerBodyEntity> BODIES = new ReferenceOpenHashSet<>();
    private static boolean registered;
    private static @Nullable ClientWorld world;

    private LoadedEntities() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        ClientEntityEvents.ENTITY_LOAD.register(LoadedEntities::onLoad);
        ClientEntityEvents.ENTITY_UNLOAD.register(LoadedEntities::onUnload);
        // Fabric already unloads every entity on disconnect; this only lets go of the old world. A world joined
        // before this runs has rebound already and is kept.
        // Fabric 断开连接时已卸载全部实体；这里只是放开旧世界。在此之前已加入的新世界已经重新绑定，予以保留。
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            if (client.world != world) {
                reset();
            }
        }));
    }

    /** A live drone may be loaded in {@code in}. / {@code in} 中可能加载着无人机。 */
    public static boolean anyDrone(World in) {
        return in == world && !DRONES.isEmpty();
    }

    /** A Partygoer may be loaded in {@code in}. / {@code in} 中可能加载着派对客。 */
    public static boolean anyPartygoer(World in) {
        return in == world && !PARTYGOERS.isEmpty();
    }

    /**
     * A loaded corpse in {@code in} is a Partygoer's. The mark is a synced component, so it is read now, not at load.
     * {@code in} 中有已加载的尸体是派对客尸体。标记是同步组件，因此现在读取，而不是在加载时读取。
     */
    public static boolean anyPartygoerCorpse(World in) {
        if (in != world) {
            return false;
        }
        for (PlayerBodyEntity body : BODIES) {
            if (PartygoerCorpseComponent.isPartygoerCorpse(body)) {
                return true;
            }
        }
        return false;
    }

    private static void onLoad(Entity entity, ClientWorld loadedIn) {
        if (loadedIn != world) {
            reset();
            world = loadedIn;
        }
        if (entity instanceof DroneEntity drone) {
            DRONES.add(drone);
        } else if (entity instanceof PartygoerEntity partygoer) {
            PARTYGOERS.add(partygoer);
        } else if (entity instanceof PlayerBodyEntity body) {
            BODIES.add(body);
        }
    }

    private static void onUnload(Entity entity, ClientWorld unloadedFrom) {
        if (unloadedFrom != world) {
            return;
        }
        if (entity instanceof DroneEntity drone) {
            DRONES.remove(drone);
        } else if (entity instanceof PartygoerEntity partygoer) {
            PARTYGOERS.remove(partygoer);
        } else if (entity instanceof PlayerBodyEntity body) {
            BODIES.remove(body);
        }
    }

    private static void reset() {
        DRONES.clear();
        PARTYGOERS.clear();
        BODIES.clear();
        world = null;
    }
}
