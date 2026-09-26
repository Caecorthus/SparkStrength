package annina.sparkstrength.component.detective;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.detective.DetectiveCaseRules;
import io.netty.handler.codec.DecoderException;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * The detective's case folder: filed murders, notes, killer-role guess, presumed killer and the suspect clues of each
 * case.
 * 侦探的文件夹：已记录的命案、笔记、凶手身份推测、假定凶手以及每起命案的嫌疑人线索。
 *
 * <p>Authority: the server owns this state; clients only edit it through the detective C2S packets, which the
 * service validates. Mutators do not sync on their own (callers batch changes, then call {@link #sync()}, or
 * {@link #markDirty()} for client-driven edits), except {@link #clearAll()}. Sync is owner-only and never carries a
 * suspect's real uuid; NBT keeps it for the server.
 * 权威：状态由服务端持有，客户端只能通过经服务校验的侦探 C2S 包修改。除 clearAll 外，修改方法不会自动同步，
 * 调用方批量修改后自行 sync()（客户端触发的修改用 markDirty()）。同步仅发给本人且绝不包含嫌疑人真实 UUID；
 * NBT 中保留真实 UUID 供服务端使用。</p>
 */
public final class DetectiveCasePlayerComponent implements AutoSyncedComponent {
    public static final ComponentKey<DetectiveCasePlayerComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("detective_case_player"),
            DetectiveCasePlayerComponent.class
    );

    private final PlayerEntity player;
    private final List<DetectiveCase> cases = new ArrayList<>();
    private final List<DetectiveCase> casesView = Collections.unmodifiableList(cases);
    private @Nullable UUID selectedCaseId;
    // Client mirror of the server's world setting; the server always reads the world component instead.
    // 客户端镜像的上限值；服务端始终直接读取世界组件。
    private int syncedSuspectLimit = DetectiveCaseRules.DEFAULT_SUSPECT_LIMIT;
    private int syncRevision;
    // Server-only, never persisted: coalesced client-driven edits and the pre-open sync throttle.
    // 仅服务端、不持久化：合并客户端触发的修改，以及打开文件夹前同步的节流。
    private boolean dirty;
    // Server-only, transient: an open request deferred until the pending sync is flushed. 仅服务端临时状态：等待同步后再打开。
    private boolean openAfterFlush;
    private long nextOpenSyncTick;

    public DetectiveCasePlayerComponent(PlayerEntity player) {
        this.player = player;
    }

    public List<DetectiveCase> getCases() {
        return casesView;
    }

    public @Nullable DetectiveCase findCase(UUID caseId) {
        if (caseId == null) {
            return null;
        }
        for (DetectiveCase detectiveCase : cases) {
            if (detectiveCase.caseId.equals(caseId)) {
                return detectiveCase;
            }
        }
        return null;
    }

    public @Nullable UUID getSelectedCaseId() {
        return selectedCaseId;
    }

    public @Nullable DetectiveCase getSelectedCase() {
        return findCase(selectedCaseId);
    }

    /**
     * Files a new case (caseId = body entity uuid). Returns false if it already exists or the folder is full.
     * Does not select it.
     * 记录新命案（caseId 为尸体实体 UUID）；已存在或文件夹已满时返回 false。不会自动选中。
     */
    public boolean addCase(UUID caseId, @Nullable UUID victimDisplayUuid, String victimName) {
        if (caseId == null || findCase(caseId) != null || cases.size() >= DetectiveCaseRules.MAX_CASES) {
            return false;
        }
        cases.add(new DetectiveCase(caseId, victimDisplayUuid, victimName, "", null, null));
        return true;
    }

    /**
     * Returns true only when the case exists and the selection actually changed, so no-op requests never sync.
     * 仅当案件存在且选中项确实改变时返回 true，无变化的请求不会触发同步。
     */
    public boolean selectCase(UUID caseId) {
        if (findCase(caseId) == null || caseId.equals(selectedCaseId)) {
            return false;
        }
        selectedCaseId = caseId;
        return true;
    }

    /**
     * Returns true only when the case exists and its notes changed. Notes are re-sanitized defensively (idempotent).
     * 仅当案件存在且笔记确实改变时返回 true；笔记会再次清洗（幂等）。
     */
    public boolean setNotes(UUID caseId, String sanitizedNotes) {
        DetectiveCase detectiveCase = findCase(caseId);
        if (detectiveCase == null) {
            return false;
        }
        String notes = DetectiveCaseRules.sanitizeNotes(sanitizedNotes);
        if (notes.equals(detectiveCase.notes)) {
            return false;
        }
        detectiveCase.notes = notes;
        return true;
    }

    /**
     * Returns true only when the case exists and the guess changed; role validation belongs to the service.
     * 仅当案件存在且推测确实改变时返回 true；角色合法性由服务校验。
     */
    public boolean setKillerGuess(UUID caseId, @Nullable Identifier roleId) {
        DetectiveCase detectiveCase = findCase(caseId);
        if (detectiveCase == null || Objects.equals(detectiveCase.killerGuess, roleId)) {
            return false;
        }
        detectiveCase.killerGuess = roleId;
        return true;
    }

    /**
     * Returns true only when the case exists and the presumed killer changed; participant validation belongs to the
     * service. A private note with no gameplay effect.
     * 仅当案件存在且假定凶手确实改变时返回 true；是否为本局玩家由服务校验。仅为私人笔记，不影响玩法。
     */
    public boolean setPresumedKiller(UUID caseId, @Nullable UUID playerUuid) {
        DetectiveCase detectiveCase = findCase(caseId);
        if (detectiveCase == null || Objects.equals(detectiveCase.presumedKillerUuid, playerUuid)) {
            return false;
        }
        detectiveCase.presumedKillerUuid = playerUuid;
        return true;
    }

    /**
     * Whether the case already holds a clue for what the detective SEES now ({@link DetectiveCaseRules#isDuplicateClue}):
     * never keyed on the real uuid, so a free ALREADY_RECORDED answer cannot expose who is behind a disguise.
     * 该案件是否已有侦探此刻“看到的身份”的线索；绝不按真实 UUID 判断，免费的“已记录”回应不会暴露伪装背后的人。
     */
    public boolean hasSuspectPair(UUID caseId, @Nullable UUID displayUuid, boolean anonymous) {
        DetectiveCase detectiveCase = findCase(caseId);
        if (detectiveCase == null) {
            return false;
        }
        for (SuspectClue clue : detectiveCase.suspects) {
            if (DetectiveCaseRules.isDuplicateClue(clue.anonymous(), clue.displayUuid(), anonymous, displayUuid)) {
                return true;
            }
        }
        return false;
    }

    /** Appends a clue; false if the case is missing or already holds MAX_SUSPECT_LIMIT clues. 超过 4 条返回 false。 */
    public boolean addSuspect(UUID caseId, SuspectClue clue) {
        DetectiveCase detectiveCase = findCase(caseId);
        if (detectiveCase == null || clue == null
                || detectiveCase.suspects.size() >= DetectiveCaseRules.MAX_SUSPECT_LIMIT) {
            return false;
        }
        detectiveCase.suspects.add(clue);
        return true;
    }

    /**
     * Server: the live world setting. Client: the value carried by the last sync.
     * 服务端返回世界组件中的实时设置；客户端返回最近一次同步的值。
     */
    public int getSyncedSuspectLimit() {
        World world = player == null ? null : player.getWorld();
        if (world != null && !world.isClient()) {
            return DetectiveCaseWorldComponent.KEY.get(world).getSuspectLimit();
        }
        return syncedSuspectLimit;
    }

    /** Client: increments on every applied sync so open screens can poll for changes. 客户端每次应用同步时自增。 */
    public int getSyncRevision() {
        return syncRevision;
    }

    public void clearAll() {
        if (cases.isEmpty() && selectedCaseId == null) {
            return;
        }
        cases.clear();
        selectedCaseId = null;
        sync();
    }

    public void sync() {
        dirty = false;
        if (player != null) {
            KEY.sync(player);
        }
    }

    /**
     * Server: queues one full-folder sync for the end of the tick. Client-driven edits use this instead of
     * {@link #sync()} so a flood of edit packets costs at most one sync per tick.
     * 服务端：把一次完整同步推迟到本 tick 末尾。客户端触发的修改使用它而不是 sync()，大量编辑包每 tick 最多同步一次。
     */
    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    /** Server: asks the end-of-tick flush to open the folder after syncing. 服务端：请求 tick 末尾同步后再打开文件夹。 */
    public void requestOpenAfterFlush() {
        openAfterFlush = true;
    }

    /** Server: true once per deferred open request. 服务端：每个延迟打开请求只返回一次 true。 */
    public boolean consumeOpenAfterFlush() {
        boolean requested = openAfterFlush;
        openAfterFlush = false;
        return requested;
    }

    /** Server: sends the queued sync, if any. Called once per server tick. 服务端每 tick 调用一次，发送待处理的同步。 */
    public void flushIfDirty() {
        if (dirty) {
            sync();
        }
    }

    /**
     * Server: true (and arms the throttle) when the defensive pre-open sync may run at {@code now}; false while the
     * previous one is fewer than {@code minIntervalTicks} ticks old.
     * 服务端：若此刻允许执行打开前的保险同步则返回 true 并重新计时；距上次不足 minIntervalTicks 时返回 false。
     */
    public boolean claimOpenSync(long now, int minIntervalTicks) {
        if (now < nextOpenSyncTick) {
            return false;
        }
        nextOpenSyncTick = now + minIntervalTicks;
        return true;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayerEntity recipient) {
        return recipient == player;
    }

    @Override
    public void writeSyncPacket(RegistryByteBuf buf, ServerPlayerEntity recipient) {
        // Defence in depth: shouldSyncWith already limits this to the owner.
        // 双重保险：shouldSyncWith 已限定为本人，这里再次裁剪。
        boolean visible = recipient == player;
        buf.writeBoolean(visible);
        if (!visible) {
            return;
        }
        buf.writeVarInt(DetectiveCaseRules.clampSuspectLimit(getSyncedSuspectLimit()));
        writeOptionalUuid(buf, selectedCaseId);
        int caseCount = Math.min(cases.size(), DetectiveCaseRules.MAX_CASES);
        buf.writeVarInt(caseCount);
        for (int i = 0; i < caseCount; i++) {
            DetectiveCase detectiveCase = cases.get(i);
            buf.writeUuid(detectiveCase.caseId);
            writeOptionalUuid(buf, detectiveCase.victimDisplayUuid);
            buf.writeString(detectiveCase.victimName, DetectiveCaseRules.NAME_MAX_LENGTH);
            buf.writeString(detectiveCase.notes, DetectiveCaseRules.NOTES_MAX_LENGTH);
            writeOptionalIdentifier(buf, detectiveCase.killerGuess);
            writeOptionalUuid(buf, detectiveCase.presumedKillerUuid);
            int suspectCount = Math.min(detectiveCase.suspects.size(), DetectiveCaseRules.MAX_SUSPECT_LIMIT);
            buf.writeVarInt(suspectCount);
            for (int j = 0; j < suspectCount; j++) {
                // realUuid is deliberately never written. 刻意不写入真实 UUID。
                SuspectClue clue = detectiveCase.suspects.get(j);
                writeOptionalUuid(buf, clue.displayUuid());
                buf.writeString(clue.displayName(), DetectiveCaseRules.NAME_MAX_LENGTH);
                buf.writeBoolean(clue.anonymous());
                buf.writeVarInt(clue.distanceBlocks());
            }
        }
    }

    @Override
    public void applySyncPacket(RegistryByteBuf buf) {
        List<DetectiveCase> decoded = new ArrayList<>();
        UUID selected = null;
        int limit = DetectiveCaseRules.DEFAULT_SUSPECT_LIMIT;
        if (buf.readBoolean()) {
            limit = DetectiveCaseRules.clampSuspectLimit(buf.readVarInt());
            selected = readOptionalUuid(buf);
            int caseCount = readBoundedCount(buf, DetectiveCaseRules.MAX_CASES);
            for (int i = 0; i < caseCount; i++) {
                UUID caseId = buf.readUuid();
                UUID victimDisplayUuid = readOptionalUuid(buf);
                String victimName = buf.readString(DetectiveCaseRules.NAME_MAX_LENGTH);
                String notes = buf.readString(DetectiveCaseRules.NOTES_MAX_LENGTH);
                Identifier killerGuess = readOptionalIdentifier(buf);
                UUID presumedKiller = readOptionalUuid(buf);
                DetectiveCase detectiveCase = new DetectiveCase(
                        caseId, victimDisplayUuid, victimName, notes, killerGuess, presumedKiller);
                int suspectCount = readBoundedCount(buf, DetectiveCaseRules.MAX_SUSPECT_LIMIT);
                for (int j = 0; j < suspectCount; j++) {
                    UUID displayUuid = readOptionalUuid(buf);
                    String displayName = buf.readString(DetectiveCaseRules.NAME_MAX_LENGTH);
                    boolean anonymous = buf.readBoolean();
                    int distance = buf.readVarInt();
                    detectiveCase.suspects.add(new SuspectClue(null, displayUuid, displayName, anonymous, distance));
                }
                if (decoded.stream().noneMatch(existing -> existing.caseId.equals(caseId))) {
                    decoded.add(detectiveCase);
                }
            }
        }
        // Commit only after the whole payload decoded, so a malformed packet never leaves a half-applied folder.
        // 整个数据包解码成功后才提交，避免格式错误时留下半更新的文件夹。
        cases.clear();
        cases.addAll(decoded);
        selectedCaseId = findCase(selected) != null ? selected : null;
        syncedSuspectLimit = limit;
        syncRevision++;
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        if (!cases.isEmpty()) {
            NbtList caseList = new NbtList();
            for (DetectiveCase detectiveCase : cases) {
                NbtCompound caseTag = new NbtCompound();
                caseTag.putUuid("Case", detectiveCase.caseId);
                if (detectiveCase.victimDisplayUuid != null) {
                    caseTag.putUuid("VictimDisplay", detectiveCase.victimDisplayUuid);
                }
                caseTag.putString("VictimName", detectiveCase.victimName);
                caseTag.putString("Notes", detectiveCase.notes);
                if (detectiveCase.killerGuess != null) {
                    caseTag.putString("KillerGuess", detectiveCase.killerGuess.toString());
                }
                if (detectiveCase.presumedKillerUuid != null) {
                    caseTag.putUuid("PresumedKiller", detectiveCase.presumedKillerUuid);
                }
                NbtList suspectList = new NbtList();
                for (SuspectClue clue : detectiveCase.suspects) {
                    NbtCompound suspectTag = new NbtCompound();
                    if (clue.realUuid() != null) {
                        suspectTag.putUuid("Real", clue.realUuid());
                    }
                    if (clue.displayUuid() != null) {
                        suspectTag.putUuid("Display", clue.displayUuid());
                    }
                    suspectTag.putString("Name", clue.displayName());
                    suspectTag.putBoolean("Anonymous", clue.anonymous());
                    suspectTag.putInt("Distance", clue.distanceBlocks());
                    suspectList.add(suspectTag);
                }
                caseTag.put("Suspects", suspectList);
                caseList.add(caseTag);
            }
            tag.put("DetectiveCases", caseList);
        }
        if (selectedCaseId != null) {
            tag.putUuid("DetectiveSelectedCase", selectedCaseId);
        }
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        cases.clear();
        NbtList caseList = tag.getList("DetectiveCases", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < caseList.size() && cases.size() < DetectiveCaseRules.MAX_CASES; i++) {
            NbtCompound caseTag = caseList.getCompound(i);
            if (!caseTag.containsUuid("Case") || findCase(caseTag.getUuid("Case")) != null) {
                continue;
            }
            DetectiveCase detectiveCase = new DetectiveCase(
                    caseTag.getUuid("Case"),
                    caseTag.containsUuid("VictimDisplay") ? caseTag.getUuid("VictimDisplay") : null,
                    caseTag.getString("VictimName"),
                    caseTag.getString("Notes"),
                    caseTag.contains("KillerGuess", NbtElement.STRING_TYPE)
                            ? Identifier.tryParse(caseTag.getString("KillerGuess"))
                            : null,
                    caseTag.containsUuid("PresumedKiller") ? caseTag.getUuid("PresumedKiller") : null
            );
            NbtList suspectList = caseTag.getList("Suspects", NbtElement.COMPOUND_TYPE);
            for (int j = 0; j < suspectList.size()
                    && detectiveCase.suspects.size() < DetectiveCaseRules.MAX_SUSPECT_LIMIT; j++) {
                NbtCompound suspectTag = suspectList.getCompound(j);
                detectiveCase.suspects.add(new SuspectClue(
                        suspectTag.containsUuid("Real") ? suspectTag.getUuid("Real") : null,
                        suspectTag.containsUuid("Display") ? suspectTag.getUuid("Display") : null,
                        suspectTag.getString("Name"),
                        suspectTag.getBoolean("Anonymous"),
                        suspectTag.getInt("Distance")
                ));
            }
            cases.add(detectiveCase);
        }
        UUID selected = tag.containsUuid("DetectiveSelectedCase") ? tag.getUuid("DetectiveSelectedCase") : null;
        selectedCaseId = findCase(selected) != null ? selected : null;
    }

    private static int readBoundedCount(RegistryByteBuf buf, int max) {
        int count = buf.readVarInt();
        if (count < 0 || count > max) {
            throw new DecoderException("Detective case list size " + count + " exceeds " + max);
        }
        return count;
    }

    private static void writeOptionalUuid(RegistryByteBuf buf, @Nullable UUID uuid) {
        buf.writeBoolean(uuid != null);
        if (uuid != null) {
            buf.writeUuid(uuid);
        }
    }

    private static @Nullable UUID readOptionalUuid(RegistryByteBuf buf) {
        return buf.readBoolean() ? buf.readUuid() : null;
    }

    private static void writeOptionalIdentifier(RegistryByteBuf buf, @Nullable Identifier identifier) {
        buf.writeBoolean(identifier != null);
        if (identifier != null) {
            buf.writeIdentifier(identifier);
        }
    }

    private static @Nullable Identifier readOptionalIdentifier(RegistryByteBuf buf) {
        return buf.readBoolean() ? buf.readIdentifier() : null;
    }

    /**
     * One suspect clue. {@code realUuid} is server-only (null on the client) and is never used for duplicate checks,
     * which depend only on what the detective saw ({@code displayUuid}/{@code displayName}). Anonymous clues never
     * carry a display uuid. Names are capped to NAME_MAX_LENGTH and distances floored at 0 on construction.
     * 一条嫌疑人线索。realUuid 仅服务端持有（客户端为 null），不参与去重；去重只看侦探看到的身份（displayUuid/displayName）。
     * 匿名线索不携带显示 UUID；构造时名字截断到 32 字符、距离不小于 0。
     */
    public record SuspectClue(@Nullable UUID realUuid, @Nullable UUID displayUuid,
                              String displayName, boolean anonymous, int distanceBlocks) {
        public SuspectClue {
            if (anonymous) {
                displayUuid = null;
            }
            displayName = DetectiveCaseRules.truncate(displayName, DetectiveCaseRules.NAME_MAX_LENGTH);
            distanceBlocks = Math.max(0, distanceBlocks);
        }
    }

    /**
     * One filed murder. Read-only outside this component; edit through the component's mutators.
     * 一起已记录的命案；组件外只读，修改需通过组件方法。
     */
    public static final class DetectiveCase {
        private final UUID caseId;
        private final @Nullable UUID victimDisplayUuid;
        private final String victimName;
        private String notes;
        private @Nullable Identifier killerGuess;
        // A real player uuid the detective picked (not a disguise), so the client may resolve its name and head.
        // 侦探亲自选择的真实玩家 UUID（并非伪装身份），客户端可据此解析名字与头像。
        private @Nullable UUID presumedKillerUuid;
        private final List<SuspectClue> suspects = new ArrayList<>();
        private final List<SuspectClue> suspectsView = Collections.unmodifiableList(suspects);

        private DetectiveCase(UUID caseId, @Nullable UUID victimDisplayUuid, @Nullable String victimName,
                              @Nullable String notes, @Nullable Identifier killerGuess,
                              @Nullable UUID presumedKillerUuid) {
            this.caseId = Objects.requireNonNull(caseId, "caseId");
            this.victimDisplayUuid = victimDisplayUuid;
            this.victimName = DetectiveCaseRules.truncate(victimName, DetectiveCaseRules.NAME_MAX_LENGTH);
            this.notes = DetectiveCaseRules.sanitizeNotes(notes);
            this.killerGuess = killerGuess;
            this.presumedKillerUuid = presumedKillerUuid;
        }

        public UUID getCaseId() {
            return caseId;
        }

        public @Nullable UUID getVictimDisplayUuid() {
            return victimDisplayUuid;
        }

        public String getVictimName() {
            return victimName;
        }

        public String getNotes() {
            return notes;
        }

        public @Nullable Identifier getKillerGuess() {
            return killerGuess;
        }

        public @Nullable UUID getPresumedKillerUuid() {
            return presumedKillerUuid;
        }

        /** Unmodifiable, in record order. 不可修改，按记录顺序。 */
        public List<SuspectClue> getSuspects() {
            return suspectsView;
        }
    }
}
