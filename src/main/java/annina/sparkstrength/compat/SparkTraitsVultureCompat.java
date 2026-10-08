package annina.sparkstrength.compat;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Optional bridge to SparkTraits' public facade for the Vulture's death scream. Only
 * {@code dev.caecorthus.sparktraits.api.SparkTraitsApi} is reflected; a missing, old or failing facade yields an empty
 * list, so without SparkTraits the Vulture always screams normally (fail closed).
 * 秃鹫死亡惨叫使用的 SparkTraits 公开门面可选桥接。只反射公开门面；门面缺失、过旧或出错时返回空列表，
 * 因此没装 SparkTraits 时秃鹫总是播放普通惨叫（失败即关闭）。
 *
 * <p>Kept apart from {@link SparkTraitsCompat} so that class's compile surface (several local fixture tests compile it
 * against narrow stubs without {@code ServerWorld}) stays unchanged.
 * 与 {@link SparkTraitsCompat} 分开，避免改变其编译面（多个本地夹具测试用不含 {@code ServerWorld} 的窄桩编译它）。</p>
 */
public final class SparkTraitsVultureCompat {
    private static final String API_CLASS = "dev.caecorthus.sparktraits.api.SparkTraitsApi";
    private static final boolean INSTALLED = FabricLoader.getInstance().isModLoaded("sparktraits");
    private static final Method GET_ROUND_END_TRAIT_IDS = findRoundEndTraitIdsMethod();

    private SparkTraitsVultureCompat() {
    }

    /**
     * Traits the player holds or died with: live traits, else the death snapshot, else the round snapshot. Safe inside
     * Wathe's {@code KillPlayer.AFTER} whichever side of SparkTraits' own AFTER listener (which snapshots, then wipes,
     * the victim's traits) this runs on. Server-only; never null.
     * 玩家当前或死亡时持有的词条：在线词条，否则死亡快照，再否则本局快照。无论本调用在 SparkTraits 自己的
     * {@code KillPlayer.AFTER}（先快照、再清空受害者词条）之前还是之后执行，结果都正确。仅限服务端；永不返回 null。
     */
    public static List<Identifier> getRoundEndTraitIds(ServerWorld world, UUID playerUuid) {
        if (world == null || playerUuid == null || GET_ROUND_END_TRAIT_IDS == null) {
            return List.of();
        }
        Object result;
        try {
            result = GET_ROUND_END_TRAIT_IDS.invoke(null, world, playerUuid);
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError ignored) {
            return List.of();
        }
        if (!(result instanceof List<?> raw)) {
            return List.of();
        }
        List<Identifier> traitIds = new ArrayList<>(raw.size());
        for (Object element : raw) {
            if (element instanceof Identifier traitId) {
                traitIds.add(traitId);
            }
        }
        return List.copyOf(traitIds);
    }

    private static Method findRoundEndTraitIdsMethod() {
        if (!INSTALLED) {
            return null;
        }
        try {
            Method method = Class.forName(API_CLASS).getMethod("getRoundEndTraitIds", ServerWorld.class, UUID.class);
            return Modifier.isStatic(method.getModifiers()) && List.class.isAssignableFrom(method.getReturnType())
                    ? method
                    : null;
        } catch (ClassNotFoundException | NoSuchMethodException | SecurityException | LinkageError ignored) {
            return null;
        }
    }
}
