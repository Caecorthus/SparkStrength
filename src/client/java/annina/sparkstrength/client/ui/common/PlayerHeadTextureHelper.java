package annina.sparkstrength.client.ui.common;

import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 背包头像使用的稳定皮肤解析工具。
 *
 * <p>这里只读 Tab 列表、Wathe 玩家缓存和默认皮肤，不读取世界内玩家实体当前皮肤。
 * 世界实体可能已经被验尸官/变形怪改写外观，头像栏如果读取实时实体会把伪装状态泄露回 UI。</p>
 */
public final class PlayerHeadTextureHelper {
    private PlayerHeadTextureHelper() {
    }

    public static SkinTextures resolveStableSkinTextures(UUID targetUuid, @Nullable PlayerListEntry preferredEntry) {
        PlayerListEntry onlineEntry = onlineEntry(targetUuid, preferredEntry);
        if (onlineEntry != null) {
            return onlineEntry.getSkinTextures();
        }

        if (WatheClient.PLAYER_ENTRIES_CACHE != null) {
            PlayerListEntry cachedEntry = WatheClient.PLAYER_ENTRIES_CACHE.get(targetUuid);
            if (cachedEntry != null) {
                return cachedEntry.getSkinTextures();
            }
        }

        return DefaultSkinHelper.getSkinTextures(targetUuid);
    }

    /**
     * Preferred entry, then the tab-list entry, then the UUID's default skin. Unlike
     * {@link #resolveStableSkinTextures} it skips Wathe's player cache, so a player no longer in the tab list shows
     * the default skin (noisemaker and professor head buttons).
     * 依次使用传入条目、Tab 列表条目、UUID 默认皮肤。与 resolveStableSkinTextures 不同，这里不查 Wathe 玩家缓存，
     * 已不在 Tab 列表中的玩家显示默认皮肤（大嗓门与教授的头像按钮）。
     */
    public static SkinTextures resolveOnlineOrDefaultSkinTextures(UUID targetUuid, @Nullable PlayerListEntry preferredEntry) {
        PlayerListEntry onlineEntry = onlineEntry(targetUuid, preferredEntry);
        if (onlineEntry != null) {
            return onlineEntry.getSkinTextures();
        }

        return DefaultSkinHelper.getSkinTextures(targetUuid);
    }

    private static @Nullable PlayerListEntry onlineEntry(UUID targetUuid, @Nullable PlayerListEntry preferredEntry) {
        if (preferredEntry != null) {
            return preferredEntry;
        }

        ClientPlayerEntity localPlayer = MinecraftClient.getInstance().player;
        if (localPlayer != null && localPlayer.networkHandler != null) {
            return localPlayer.networkHandler.getPlayerListEntry(targetUuid);
        }
        return null;
    }
}
