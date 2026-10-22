package annina.sparkstrength.replay;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.role.toxicologist.ToxicologistBlueVitriolService;
import dev.doctor4t.wathe.record.replay.ReplayGenerator;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

import java.util.UUID;

/**
 * Replay line for the Toxicologist's Blue Vitriol.
 * 毒理学家蓝矾的回放文本。
 */
public final class BlueVitriolReplayEvents {
    private BlueVitriolReplayEvents() {
    }

    static void register() {
        // Wathe drops ITEM_USE events whose item has no registered formatter; the action comes from the vitriol service.
        // Wathe 会忽略没有注册格式化器的物品使用事件；action 由蓝矾服务写入。
        ReplayRegistry.registerItemUseFormatter(SparkStrengthItems.BLUE_VITRIOL_ID, (event, match, world) -> {
            var playerInfoCache = ReplayGenerator.getPlayerInfoCache(match);
            NbtCompound data = event.data();
            UUID actorUuid = data.containsUuid("actor") ? data.getUuid("actor") : null;
            if (actorUuid == null) {
                return null;
            }
            Text actorText = ReplayGenerator.formatPlayerName(actorUuid, playerInfoCache);
            String action = data.getString("action");
            if (ToxicologistBlueVitriolService.ACTION_FOOD.equals(action)) {
                return Text.translatable(
                        "replay.item_use.sparkstrength.blue_vitriol.food",
                        actorText,
                        ReplayGenerator.formatItemName(data, world)
                );
            }
            if (ToxicologistBlueVitriolService.ACTION_PLATE.equals(action)
                    || ToxicologistBlueVitriolService.ACTION_BED.equals(action)
                    || ToxicologistBlueVitriolService.ACTION_CAPSULE.equals(action)) {
                return Text.translatable("replay.item_use.sparkstrength.blue_vitriol." + action, actorText);
            }
            return null;
        });
    }
}
