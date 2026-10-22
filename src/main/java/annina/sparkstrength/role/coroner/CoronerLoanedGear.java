package annina.sparkstrength.role.coroner;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkWitchCompat;
import annina.sparkstrength.component.coroner.CoronerPlayerComponent;
import annina.sparkstrength.component.timekeeper.TimekeeperWatchComponent;
import com.mojang.authlib.GameProfile;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.index.tag.WatheItemTags;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.ModItems;
import org.agmas.noellesroles.demonhunter.DemonHunterPistolItem;
import org.agmas.noellesroles.demonhunter.DemonHunterPlayerComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The gear a Coroner disguise lends: each stack is marked with its owner, disguise and kind when granted, and is taken
 * back from the inventory and the ground when the disguise changes, is suppressed by a morph, or ends with a death or
 * reset. The Demon Hunter disguise lends its pistol only while someone is in a frenzy.
 * 验尸官伪装借出的临时装备：发放时在物品上标记主人、伪装与种类；伪装切换、被变形压制或因死亡/重置结束时，
 * 从背包和地面一并收回。恶魔猎人伪装只在有人疯魔期间借出手枪。
 */
final class CoronerLoanedGear {
    private static final String TEMP_ITEM_ROOT_KEY = "SparkStrengthCoronerTemporaryGrant";
    private static final String TEMP_OWNER_KEY = "Owner";
    private static final String TEMP_DISGUISE_KEY = "Disguise";
    private static final String TEMP_ROLE_ID_KEY = "RoleId";
    private static final String TEMP_KIND_KEY = "Kind";
    /**
     * Every loaded item entity, whatever it holds (a stack can change after it loads). The frenzy-over cleanup filters
     * these exactly as its world scan did, instead of walking every entity in the world. Weak, so an item removed
     * without an unload event (its chunk untracked) cannot leak.
     * 所有已加载的掉落物实体，不论装着什么（物品堆在加载后仍可能改变）。疯魔结束后的清理按原先世界扫描的条件筛选它们，
     * 而不是遍历世界中的每个实体。弱引用：没有卸载事件就被移除的掉落物（所在区块未追踪）不会泄漏。
     */
    private static final Set<ItemEntity> LOADED_ITEMS = Collections.newSetFromMap(new WeakHashMap<>());

    private CoronerLoanedGear() {
    }

    /**
     * Keeps {@link #LOADED_ITEMS} in step with the server's item entities. Only {@link CoronerService#register} calls
     * this, behind its run-once guard.
     * 让 {@link #LOADED_ITEMS} 与服务端的掉落物实体保持一致。只由 {@link CoronerService#register}
     * 在其只运行一次的守卫之后调用。
     */
    static void registerLoadedItemTracking() {
        // Same type test as getEntitiesByType(EntityType.ITEM, ...). / 与 getEntitiesByType(EntityType.ITEM, ...) 相同的类型判断。
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (entity.getType() == EntityType.ITEM && entity instanceof ItemEntity item) {
                LOADED_ITEMS.add(item);
            }
        });
        // Only once removed: an item in a chunk that stops being tracked is still in the world's entity index.
        // 只在真正移除时去掉：所在区块不再追踪的掉落物仍在世界的实体索引里。
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (entity instanceof ItemEntity item && item.isRemoved()) {
                LOADED_ITEMS.remove(item);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> LOADED_ITEMS.clear());
    }

    static void refreshTemporaryEquipmentForMorphPriority(ServerPlayerEntity player) {
        CoronerPlayerComponent component = CoronerPlayerComponent.KEY.get(player);
        UUID disguiseUuid = component.activeDisguiseUuid();
        if (disguiseUuid == null) {
            component.setTemporaryEquipmentSuppressed(false);
            return;
        }

        if (CoronerDisguises.isHigherPriorityMorphActive(player)) {
            if (!component.isTemporaryEquipmentSuppressed()) {
                /*
                 * 被变形怪效果覆盖时，验尸官自己的伪装能力临时失效：
                 * 外观已经由客户端优先级处理，这里同步收回尸体身份发放的刀、枪、钥匙。
                 */
                removeTemporaryItems(player);
                TimekeeperWatchComponent.KEY.get(player).reset();
                component.setTemporaryEquipmentSuppressed(true);
            }
            return;
        }

        if (!component.isTemporaryEquipmentSuppressed()) {
            return;
        }

        CoronerPlayerComponent.BodySnapshot snapshot = component.snapshot(disguiseUuid);
        component.setTemporaryEquipmentSuppressed(false);
        if (snapshot != null) {
            /*
             * 高优先级变形结束后，验尸官继续自己之前选中的长期尸体伪装，
             * 因此把该尸体身份对应的临时装备重新补发回来。
             */
            grantTemporaryEquipment(player, disguiseUuid, snapshot);
        }
    }

    static void grantTemporaryEquipment(
            ServerPlayerEntity player,
            UUID disguiseUuid,
            CoronerPlayerComponent.BodySnapshot snapshot
    ) {
        Role role = CoronerRules.resolveRole(snapshot.roleId());
        if (role == null) {
            return;
        }

        if (CoronerRules.grantsDagger(role)) {
            giveTemporaryItem(player, WatheItems.KNIFE, disguiseUuid, snapshot.roleId(), "knife");
        }
        if (CoronerRules.grantsRevolver(role)) {
            giveTemporaryItem(player, WatheItems.REVOLVER, disguiseUuid, snapshot.roleId(), "revolver");
        }
        if (CoronerRules.grantsPoisonNeedle(role)) {
            /*
             * 毒师尸体身份是杀手阵营中的特例：验尸官不拿默认匕首，
             * 而是临时获得 NoellesRoles 毒师开局/商店使用的毒针。
             */
            giveTemporaryItem(player, ModItems.POISON_NEEDLE, disguiseUuid, snapshot.roleId(), "poison_needle");
        }
        if (CoronerRules.isToxicologist(role)) {
            giveTemporaryItem(player, ModItems.ANTIDOTE, disguiseUuid, snapshot.roleId(), "antidote");
        }
        if (CoronerRules.isProfessor(role)) {
            giveTemporaryItem(player, ModItems.IRON_MAN_VIAL, disguiseUuid, snapshot.roleId(), "iron_man_vial");
        }
        if (CoronerRules.isAttendant(role)) {
            giveTemporaryStack(player, createAttendantBook(player), disguiseUuid, snapshot.roleId(), "attendant_book");
            /*
             * Attendant disguise also grants the tablet door monitor (TabletChannelResolver#features). Lend a marked
             * tablet only when the Coroner has none, so removeTemporaryItems never takes a tablet the Coroner owns.
             * 乘务员伪装同时获得平板房门监控；只在验尸官没有任何平板时借出带标记的临时平板，
             * 保证 removeTemporaryItems 绝不会收走验尸官自己的平板。
             */
            if (!hasTabletAnywhere(player)) {
                giveTemporaryItem(player, SparkStrengthItems.tablet(), disguiseUuid, snapshot.roleId(), "tablet");
            }
        }
        if (CoronerRules.isUndercover(role)) {
            giveTemporaryItem(player, WatheItems.WALKIE_TALKIE, disguiseUuid, snapshot.roleId(), "walkie_talkie");
        }
        if (CoronerRules.isEngineer(role)) {
            giveTemporaryItem(player, ModItems.REPAIR_TOOL, disguiseUuid, snapshot.roleId(), "repair_tool");
        }
        if (CoronerRules.isTimekeeper(role)) {
            // 计时员伪装获得完整的 SparkStrength 濒毁怀表；临时标记会随伪装结束自动回收。
            giveTemporaryItem(player, SparkStrengthItems.dyingWatch(), disguiseUuid, snapshot.roleId(), "dying_watch");
        }
        if (CoronerRules.grantsTimedBomb(role)) {
            giveTemporaryItem(player, ModItems.TIMED_BOMB, disguiseUuid, snapshot.roleId(), "timed_bomb");
        }
        if (CoronerRules.isSparkWitchKidnapper(role)) {
            /*
             * 验尸官伪装 SparkWitch 绑架者时，通过软兼容拿 1 个临时迷药。
             * SparkWitch 未安装或版本过旧时 createKidnapperDrugStack 会返回 null，验尸官其它伪装不受影响。
             */
            ItemStack knockoutDrug = SparkWitchCompat.createKidnapperDrugStack();
            if (knockoutDrug != null) {
                giveTemporaryStack(player, knockoutDrug, disguiseUuid, snapshot.roleId(), "sparkwitch_knockout_drug");
            }
        }
        if (CoronerRules.isDemonHunter(role)) {
            refreshDemonHunterState(player);
        }
        if (CoronerRules.grantsConductorMasterKey(role)) {
            giveTemporaryItem(player, ModItems.MASTER_KEY, disguiseUuid, snapshot.roleId(), "master_key");
        }
        if (CoronerRules.grantsNeutralMasterKey(role)) {
            giveTemporaryItem(player, ModItems.NEUTRAL_MASTER_KEY, disguiseUuid, snapshot.roleId(), "neutral_master_key");
        }
    }

    private static void giveTemporaryStack(
            ServerPlayerEntity player,
            ItemStack stack,
            UUID disguiseUuid,
            Identifier roleId,
            String kind
    ) {
        if (stack.isEmpty()) {
            return;
        }
        markTemporaryGrant(stack, player.getUuid(), disguiseUuid, roleId, kind);
        player.giveItemStack(stack);
    }

    private static void giveTemporaryItem(
            ServerPlayerEntity player,
            Item item,
            UUID disguiseUuid,
            Identifier roleId,
            String kind
    ) {
        ItemStack stack = item.getDefaultStack();
        markTemporaryGrant(stack, player.getUuid(), disguiseUuid, roleId, kind);
        player.giveItemStack(stack);
    }

    private static void markTemporaryGrant(
            ItemStack stack,
            UUID ownerUuid,
            UUID disguiseUuid,
            Identifier roleId,
            String kind
    ) {
        NbtComponent component = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT);
        NbtCompound data = component.copyNbt();

        NbtCompound root = new NbtCompound();
        root.putUuid(TEMP_OWNER_KEY, ownerUuid);
        root.putUuid(TEMP_DISGUISE_KEY, disguiseUuid);
        root.putString(TEMP_ROLE_ID_KEY, roleId.toString());
        root.putString(TEMP_KIND_KEY, kind);
        data.put(TEMP_ITEM_ROOT_KEY, root);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(data));
    }

    static boolean isTemporaryGrant(ItemStack stack) {
        return !stack.isEmpty()
                && stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT)
                .copyNbt()
                .contains(TEMP_ITEM_ROOT_KEY, NbtElement.COMPOUND_TYPE);
    }

    private static boolean isTemporaryGrantOwnedBy(ItemStack stack, UUID ownerUuid) {
        NbtCompound data = stack.getOrDefault(DataComponentTypes.CUSTOM_DATA, NbtComponent.DEFAULT).copyNbt();
        if (!data.contains(TEMP_ITEM_ROOT_KEY, NbtElement.COMPOUND_TYPE)) {
            return false;
        }
        NbtCompound root = data.getCompound(TEMP_ITEM_ROOT_KEY);
        return root.containsUuid(TEMP_OWNER_KEY) && ownerUuid.equals(root.getUuid(TEMP_OWNER_KEY));
    }

    private static boolean hasTabletAnywhere(ServerPlayerEntity player) {
        if (player.getInventory().contains(stack -> stack.isOf(SparkStrengthItems.tablet()))) {
            return true;
        }
        return player.currentScreenHandler != null
                && player.currentScreenHandler.getCursorStack().isOf(SparkStrengthItems.tablet());
    }

    static void removeTemporaryItems(ServerPlayerEntity player) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (isTemporaryGrantOwnedBy(stack, player.getUuid())) {
                player.getInventory().setStack(slot, ItemStack.EMPTY);
            }
        }
        removeDroppedTemporaryItems(player);
    }

    static void removeDroppedTemporaryItems(ServerPlayerEntity player) {
        /*
         * 玩家可以在变形期间主动把临时装备丢到远处；解除/切换变形时必须把这些装备也收回。
         * 这里按当前世界全量扫描 ItemEntity，只在切换、死亡、重置等低频路径运行，换取回收语义可靠。
         */
        for (ItemEntity itemEntity : player.getServerWorld().getEntitiesByType(EntityType.ITEM,
                itemEntity -> isTemporaryGrantOwnedBy(itemEntity.getStack(), player.getUuid()))) {
            itemEntity.discard();
        }
    }

    static void refreshDemonHunterState(ServerPlayerEntity hunter) {
        if (!CoronerDisguises.hasDemonHunterDisguise(hunter) || !GameFunctions.isPlayerPlayingAndAlive(hunter)) {
            DemonHunterPlayerComponent.KEY.get(hunter).reset();
            removeTemporaryDemonHunterPistols(hunter);
            return;
        }

        DemonHunterPlayerComponent component = DemonHunterPlayerComponent.KEY.get(hunter);
        boolean anyFrenzied = false;
        List<UUID> trackedBefore = new ArrayList<>(component.getFrenzyPlayerUuids());
        for (UUID trackedUuid : trackedBefore) {
            PlayerEntity tracked = hunter.getWorld().getPlayerByUuid(trackedUuid);
            if (tracked == null || dev.doctor4t.wathe.cca.PlayerPsychoComponent.KEY.get(tracked).getPsychoTicks() <= 0) {
                component.removeFrenzyPlayer(trackedUuid);
            }
        }
        for (ServerPlayerEntity player : hunter.getServerWorld().getPlayers()) {
            if (player == hunter || !GameFunctions.isPlayerPlayingAndAlive(player)) {
                continue;
            }
            if (dev.doctor4t.wathe.cca.PlayerPsychoComponent.KEY.get(player).getPsychoTicks() > 0) {
                component.addFrenzyPlayer(player.getUuid());
                anyFrenzied = true;
            }
        }

        anyFrenzied |= !component.getFrenzyPlayerUuids().isEmpty();

        if (anyFrenzied) {
            ensureTemporaryDemonHunterPistol(hunter);
        } else {
            removeTemporaryDemonHunterPistols(hunter);
        }
    }

    static void ensureTemporaryDemonHunterPistol(ServerPlayerEntity hunter) {
        if (DemonHunterPistolItem.findPistol(hunter) != null) {
            return;
        }
        boolean hasAnyGun = hunter.getInventory().contains(stack -> stack.isIn(WatheItemTags.GUNS));
        if (hasAnyGun) {
            return;
        }
        CoronerPlayerComponent component = CoronerPlayerComponent.KEY.get(hunter);
        UUID disguiseUuid = component.activeDisguiseUuid();
        Identifier roleId = component.activeDisguiseRoleId();
        if (disguiseUuid == null || roleId == null) {
            return;
        }

        ItemStack pistol = new ItemStack(ModItems.DEMON_HUNTER_PISTOL);
        pistol.set(ModItems.BULLETS, 2);
        markTemporaryGrant(pistol, hunter.getUuid(), disguiseUuid, roleId, "demon_hunter_pistol");
        dev.doctor4t.wathe.util.ShopEntry.insertStackInFreeSlot(hunter, pistol);
    }

    private static void removeTemporaryDemonHunterPistols(ServerPlayerEntity player) {
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            ItemStack stack = player.getInventory().getStack(slot);
            if (stack.isOf(ModItems.DEMON_HUNTER_PISTOL) && isTemporaryGrantOwnedBy(stack, player.getUuid())) {
                player.getInventory().setStack(slot, ItemStack.EMPTY);
            }
        }
        LOADED_ITEMS.removeIf(ItemEntity::isRemoved);
        if (LOADED_ITEMS.isEmpty()) {
            return;
        }
        // Collected first: discarding fires the unload event, which edits the set. / 先收集：丢弃会触发卸载事件并修改集合。
        ServerWorld world = player.getServerWorld();
        List<ItemEntity> dropped = null;
        for (ItemEntity itemEntity : LOADED_ITEMS) {
            if (itemEntity.getWorld() == world && itemEntity.getStack().isOf(ModItems.DEMON_HUNTER_PISTOL)
                    && isTemporaryGrantOwnedBy(itemEntity.getStack(), player.getUuid())) {
                if (dropped == null) {
                    dropped = new ArrayList<>();
                }
                dropped.add(itemEntity);
            }
        }
        if (dropped != null) {
            for (ItemEntity itemEntity : dropped) {
                itemEntity.discard();
            }
        }
    }

    private static ItemStack createAttendantBook(ServerPlayerEntity player) {
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        GameWorldComponent gameWorld = GameWorldComponent.KEY.get(player.getWorld());
        List<RawFilteredPair<Text>> pages = new ArrayList<>();
        HashMap<Integer, GameWorldComponent.RoomData> rooms = gameWorld.getRooms();
        HashMap<UUID, GameProfile> profiles = gameWorld.getGameProfiles();

        List<GameWorldComponent.RoomData> sortedRooms = new ArrayList<>(rooms.values());
        sortedRooms.sort(Comparator.comparingInt(GameWorldComponent.RoomData::getIndex));
        for (GameWorldComponent.RoomData room : sortedRooms) {
            StringBuilder pageContent = new StringBuilder();
            pageContent.append("§l§1【").append(room.getName()).append("】§r\n\n");
            List<UUID> roomPlayers = room.getPlayers();
            if (roomPlayers.isEmpty()) {
                pageContent.append("§8（空房间）§r");
            } else {
                for (UUID playerUuid : roomPlayers) {
                    GameProfile profile = profiles.get(playerUuid);
                    pageContent.append("§0• ")
                            .append(profile != null ? profile.getName() : "未知")
                            .append("§r\n");
                }
            }
            pages.add(RawFilteredPair.of(Text.literal(pageContent.toString())));
        }

        book.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, new WrittenBookContentComponent(
                RawFilteredPair.of("列车信息手册"),
                "乘务员",
                0,
                pages,
                true
        ));
        return book;
    }
}
