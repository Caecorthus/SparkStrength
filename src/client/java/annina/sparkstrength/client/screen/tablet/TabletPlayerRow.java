package annina.sparkstrength.client.screen.tablet;

import com.mojang.authlib.GameProfile;
import dev.doctor4t.wathe.client.WatheClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;

import java.util.UUID;

final class TabletPlayerRow {
    private TabletPlayerRow() {
    }

    static void drawAvatar(DrawContext context, UUID uuid, String name, int x, int y, int size) {
        PlayerSkinDrawer.draw(context, skin(uuid, name), x, y, size);
    }

    private static SkinTextures skin(UUID uuid, String name) {
        PlayerListEntry entry = WatheClient.PLAYER_ENTRIES_CACHE.get(uuid);
        if (entry != null) {
            return entry.getSkinTextures();
        }
        return DefaultSkinHelper.getSkinTextures(new GameProfile(uuid, name));
    }
}
