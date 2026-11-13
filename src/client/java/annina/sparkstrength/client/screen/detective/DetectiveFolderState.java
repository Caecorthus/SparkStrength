package annina.sparkstrength.client.screen.detective;

import annina.sparkstrength.component.detective.DetectiveCasePlayerComponent;
import annina.sparkstrength.component.detective.DetectiveCasePlayerComponent.DetectiveCase;
import annina.sparkstrength.network.detective.SelectDetectiveCaseC2SPacket;
import annina.sparkstrength.network.detective.SetDetectiveKillerGuessC2SPacket;
import annina.sparkstrength.network.detective.SetDetectivePresumedKillerC2SPacket;
import annina.sparkstrength.network.detective.UpdateDetectiveCaseNotesC2SPacket;
import annina.sparkstrength.role.detective.DetectiveCaseRules;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Client copy of the case folder's editable state: the viewed case, its notes draft and the edit requests (select,
 * notes, killer guess, presumed killer) shown optimistically until an owner-only sync confirms them or they time out.
 * Every C2S edit of the folder screen is sent from here.
 * 案件文件夹的客户端可编辑状态：当前查看的命案、其笔记草稿，以及在仅发给本人的同步确认或超时前乐观显示的修改请求
 * （选择、笔记、凶手身份推测、假定凶手）。文件夹界面的所有 C2S 修改都从这里发送。
 */
final class DetectiveFolderState {
    // An unconfirmed optimistic edit falls back to the synced value after this long (e.g. the server rejected it).
    // 乐观修改若在此时间内未被同步确认（例如被服务端拒绝），回退到同步值。
    private static final int PENDING_TIMEOUT_TICKS = 100;
    // Notes are saved after this long without typing, so a sudden loss of access (swallow, death) keeps most of the
    // draft; the server rejects edits once the gate fails. / 停止输入这么久后自动保存笔记，突然失去权限（被吞、死亡）时
    // 草稿大多已保存；门槛失效后服务端会拒绝编辑。
    private static final int NOTES_AUTOSAVE_TICKS = 20;

    // Notes sent for cases no longer in the editor, kept until a sync confirms them. / 已发送但尚未被同步确认的其它案件笔记。
    private final Map<UUID, String> sentNotes = new HashMap<>();
    private final Map<UUID, Pending<Identifier>> pendingGuesses = new HashMap<>();
    private final Map<UUID, Pending<UUID>> pendingPresumed = new HashMap<>();
    private @Nullable DetectiveNotesBox notesBox;
    private @Nullable UUID viewedCaseId;
    private String notesDraft = "";
    // True once the user has typed in the viewed case's notes; only an untouched draft may be replaced by a sync.
    // 用户在当前命案笔记中输入过后为 true；只有未改动的草稿才会被同步覆盖。
    private boolean notesEdited;
    private @Nullable UUID pendingSelection;
    private int pendingSelectionTicks;
    private int notesAutosaveTicks;
    private boolean suppressNotesListener;

    @Nullable UUID viewedCaseId() {
        return viewedCaseId;
    }

    @Nullable DetectiveCase viewedCase(DetectiveCasePlayerComponent folder) {
        return viewedCaseId == null ? null : folder.findCase(viewedCaseId);
    }

    /**
     * Case to show: an unconfirmed tab click, else the server's selected case, else keep the current view, else the
     * newest case.
     * 要显示的命案：尚未确认的标签点击 > 服务端选中的命案 > 保持当前视图 > 最新的命案。
     */
    @Nullable UUID resolveView(DetectiveCasePlayerComponent folder) {
        if (pendingSelection != null && folder.findCase(pendingSelection) != null) {
            return pendingSelection;
        }
        UUID selected = folder.getSelectedCaseId();
        if (selected != null && folder.findCase(selected) != null) {
            return selected;
        }
        if (viewedCaseId != null && folder.findCase(viewedCaseId) != null) {
            return viewedCaseId;
        }
        List<DetectiveCase> cases = folder.getCases();
        return cases.isEmpty() ? null : cases.get(cases.size() - 1).getCaseId();
    }

    /** The case the magnifier works on, as far as this client knows. / 本客户端所知的放大镜当前调查命案。 */
    boolean isActive(DetectiveCasePlayerComponent folder, UUID caseId) {
        UUID active = pendingSelection != null ? pendingSelection : folder.getSelectedCaseId();
        return caseId.equals(active);
    }

    /**
     * Switches the editor to {@code next}, saving the outgoing case's notes first. Returns true if the view changed.
     * 将编辑器切换到 next，切换前先保存离开的命案笔记；视图变化时返回 true。
     */
    boolean switchTo(@Nullable UUID next, DetectiveCasePlayerComponent folder) {
        if (Objects.equals(next, viewedCaseId)) {
            return false;
        }
        commitNotes(folder);
        viewedCaseId = next;
        DetectiveCase detectiveCase = next == null ? null : folder.findCase(next);
        notesDraft = detectiveCase == null ? "" : sentNotes.getOrDefault(next, detectiveCase.getNotes());
        notesEdited = false;
        return true;
    }

    /**
     * Sends the viewed case's notes if they differ from the latest text the server has or is about to have.
     * 若当前命案笔记与服务端已有或即将收到的文本不同，则发送保存。
     */
    void commitNotes(DetectiveCasePlayerComponent folder) {
        if (viewedCaseId == null) {
            return;
        }
        DetectiveCase detectiveCase = folder.findCase(viewedCaseId);
        if (detectiveCase == null) {
            return;
        }
        String sanitized = DetectiveCaseRules.sanitizeNotes(notesDraft);
        String known = sentNotes.getOrDefault(viewedCaseId, detectiveCase.getNotes());
        if (sanitized.equals(known)) {
            return;
        }
        if (send(new UpdateDetectiveCaseNotesC2SPacket(viewedCaseId, sanitized))) {
            sentNotes.put(viewedCaseId, sanitized);
        }
    }

    /** Saves the notes once typing has paused for the autosave delay. / 停止输入达到自动保存延迟后保存笔记。 */
    void tickNotesAutosave(DetectiveCasePlayerComponent folder) {
        if (notesAutosaveTicks > 0 && --notesAutosaveTicks == 0) {
            commitNotes(folder);
        }
    }

    /**
     * A sync for the viewed case replaces an untouched draft, so a screen opened on stale data never re-saves it.
     * 同步到当前命案时替换未改动的草稿，避免以旧数据打开的界面把旧文本重新保存。
     */
    void adoptUntouchedNotes(DetectiveCasePlayerComponent folder) {
        if (viewedCaseId == null || notesEdited || sentNotes.containsKey(viewedCaseId)) {
            return;
        }
        DetectiveCase detectiveCase = folder.findCase(viewedCaseId);
        if (detectiveCase == null || detectiveCase.getNotes().equals(notesDraft)) {
            return;
        }
        notesDraft = detectiveCase.getNotes();
        if (notesBox != null) {
            suppressNotesListener = true;
            try {
                notesBox.setText(notesDraft);
            } finally {
                suppressNotesListener = false;
            }
        }
    }

    /** Drops optimistic state that the new sync confirmed or made moot. / 丢弃已被新同步确认或已失效的乐观状态。 */
    void reconcile(DetectiveCasePlayerComponent folder) {
        if (pendingSelection != null
                && (pendingSelection.equals(folder.getSelectedCaseId()) || folder.findCase(pendingSelection) == null)) {
            pendingSelection = null;
        }
        sentNotes.entrySet().removeIf(entry -> {
            DetectiveCase detectiveCase = folder.findCase(entry.getKey());
            return detectiveCase == null || detectiveCase.getNotes().equals(entry.getValue());
        });
        pendingGuesses.entrySet().removeIf(entry -> {
            DetectiveCase detectiveCase = folder.findCase(entry.getKey());
            return detectiveCase == null || Objects.equals(detectiveCase.getKillerGuess(), entry.getValue().value);
        });
        pendingPresumed.entrySet().removeIf(entry -> {
            DetectiveCase detectiveCase = folder.findCase(entry.getKey());
            return detectiveCase == null || Objects.equals(detectiveCase.getPresumedKillerUuid(), entry.getValue().value);
        });
    }

    /** Ages optimistic state; returns true when the pending tab selection expired. / 推进乐观状态计时，标签选择过期时返回 true。 */
    boolean tickPending() {
        boolean expired = false;
        if (pendingSelection != null && --pendingSelectionTicks <= 0) {
            pendingSelection = null;
            expired = true;
        }
        pendingGuesses.values().removeIf(pending -> --pending.ticksLeft <= 0);
        pendingPresumed.values().removeIf(pending -> --pending.ticksLeft <= 0);
        return expired;
    }

    /**
     * Saves the viewed notes, then asks the server to select {@code caseId}. Returns false, doing nothing, when that
     * case is already viewed and active.
     * 先保存当前笔记，再请求服务端选择该命案；该命案已在查看且为调查中时不做任何事并返回 false。
     */
    boolean requestSelection(DetectiveCasePlayerComponent folder, UUID caseId) {
        if (caseId.equals(viewedCaseId) && isActive(folder, caseId)) {
            return false;
        }
        // Save first, then select, then the caller switches the view (switchTo finds nothing left to save).
        // 先保存笔记，再发送选择，最后由调用方切换视图（switchTo 不会重复保存）。
        commitNotes(folder);
        if (send(new SelectDetectiveCaseC2SPacket(caseId))) {
            pendingSelection = caseId;
            pendingSelectionTicks = PENDING_TIMEOUT_TICKS;
        }
        return true;
    }

    /** Returns false, sending nothing, when no case is viewed. / 没有查看中的命案时不发送并返回 false。 */
    boolean requestKillerGuess(DetectiveCasePlayerComponent folder, @Nullable Identifier roleId) {
        if (viewedCaseId == null || folder.findCase(viewedCaseId) == null) {
            return false;
        }
        if (send(new SetDetectiveKillerGuessC2SPacket(viewedCaseId, Optional.ofNullable(roleId)))) {
            pendingGuesses.put(viewedCaseId, new Pending<>(roleId));
        }
        return true;
    }

    /** Returns false, sending nothing, when no case is viewed. / 没有查看中的命案时不发送并返回 false。 */
    boolean requestPresumedKiller(DetectiveCasePlayerComponent folder, @Nullable UUID playerUuid) {
        if (viewedCaseId == null || folder.findCase(viewedCaseId) == null) {
            return false;
        }
        if (send(new SetDetectivePresumedKillerC2SPacket(viewedCaseId, Optional.ofNullable(playerUuid)))) {
            pendingPresumed.put(viewedCaseId, new Pending<>(playerUuid));
        }
        return true;
    }

    @Nullable Identifier displayedGuess(DetectiveCase viewed) {
        Pending<Identifier> pending = pendingGuesses.get(viewed.getCaseId());
        return pending != null ? pending.value : viewed.getKillerGuess();
    }

    @Nullable UUID displayedPresumed(DetectiveCase viewed) {
        Pending<UUID> pending = pendingPresumed.get(viewed.getCaseId());
        return pending != null ? pending.value : viewed.getPresumedKillerUuid();
    }

    /**
     * Binds a freshly built notes box: it shows the draft (as the box accepted it) and reports edits here.
     * 绑定新建的笔记框：显示草稿（以输入框接受后的文本为准），并把编辑回报到这里。
     */
    void attachNotesBox(DetectiveNotesBox box) {
        box.setText(notesDraft);
        notesDraft = box.getText();
        box.setChangeListener(this::onNotesEdited);
        notesBox = box;
    }

    /** Forgets the notes box of the previous widget build. / 丢弃上一次控件构建的笔记框。 */
    void detachNotesBox() {
        notesBox = null;
    }

    private void onNotesEdited(String value) {
        if (suppressNotesListener) {
            return;
        }
        if (lineCount(value) > DetectiveCaseRules.NOTES_MAX_LINES && notesBox != null) {
            // Same line cap the server applies; reject the edit instead of silently losing lines on save.
            // 与服务端相同的行数上限；直接拒绝本次编辑，避免保存时静默丢行。
            suppressNotesListener = true;
            try {
                notesBox.setText(notesDraft);
            } finally {
                suppressNotesListener = false;
            }
            return;
        }
        notesDraft = value;
        notesEdited = true;
        notesAutosaveTicks = NOTES_AUTOSAVE_TICKS;
    }

    private static int lineCount(String value) {
        int lines = 1;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    /** Sends only when the server registered the channel, so an older server never disconnects us. / 服务端注册了通道才发送。 */
    private static boolean send(CustomPayload payload) {
        if (!ClientPlayNetworking.canSend(payload.getId())) {
            return false;
        }
        ClientPlayNetworking.send(payload);
        return true;
    }

    /** Optimistic value shown until a sync confirms it or it times out; null means "cleared". / 乐观显示值，null 表示已清除。 */
    private static final class Pending<T> {
        private final @Nullable T value;
        private int ticksLeft = PENDING_TIMEOUT_TICKS;

        private Pending(@Nullable T value) {
            this.value = value;
        }
    }
}
