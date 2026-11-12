package annina.sparkstrength.role.bandit;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What one Bandit revolver stack carries: the fitted parts and the cylinder. {@code rounds} are chambered and fire in
 * order (index 0 first); {@code pending} were pushed into the cylinder but are not seated yet, and seat one at a time
 * while the gun is held ({@link BanditRevolverRules#loadTicks}). Values are the stable {@code code()}s; unknown codes
 * read as "nothing". Immutable: every change returns a new state.
 * 一把强盗改装左轮携带的数据：已装配件与弹巢。{@code rounds} 为已上膛的子弹，按顺序击发（下标 0 最先）；{@code pending}
 * 已塞入弹巢但尚未到位，手持时逐发到位（见 {@link BanditRevolverRules#loadTicks}）。数值为稳定的 {@code code()}；未知取值
 * 视为“无”。不可变：每次变更都返回新状态。
 */
public record BanditRevolverState(int barrel, int grip, int cylinder, List<Integer> rounds, List<Integer> pending) {
    public static final BanditRevolverState EMPTY = new BanditRevolverState(0, 0, 0, List.of(), List.of());

    public static final Codec<BanditRevolverState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("barrel", 0).forGetter(BanditRevolverState::barrel),
            Codec.INT.optionalFieldOf("grip", 0).forGetter(BanditRevolverState::grip),
            Codec.INT.optionalFieldOf("cylinder", 0).forGetter(BanditRevolverState::cylinder),
            Codec.INT.listOf().optionalFieldOf("rounds", List.of()).forGetter(BanditRevolverState::rounds),
            Codec.INT.listOf().optionalFieldOf("pending", List.of()).forGetter(BanditRevolverState::pending)
    ).apply(instance, BanditRevolverState::new));

    public static final PacketCodec<RegistryByteBuf, BanditRevolverState> PACKET_CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, BanditRevolverState::barrel,
            PacketCodecs.VAR_INT, BanditRevolverState::grip,
            PacketCodecs.VAR_INT, BanditRevolverState::cylinder,
            PacketCodecs.VAR_INT.collect(PacketCodecs.toList()), BanditRevolverState::rounds,
            PacketCodecs.VAR_INT.collect(PacketCodecs.toList()), BanditRevolverState::pending,
            BanditRevolverState::new
    );

    public BanditRevolverState {
        rounds = List.copyOf(rounds);
        pending = List.copyOf(pending);
    }

    public @Nullable BanditAttachment part(BanditAttachmentSlot slot) {
        BanditAttachment part = BanditAttachment.byCode(switch (slot) {
            case BARREL -> barrel;
            case GRIP -> grip;
            case CYLINDER -> cylinder;
        });
        return part != null && part.slot() == slot ? part : null;
    }

    public @Nullable BanditAttachment barrelPart() {
        return part(BanditAttachmentSlot.BARREL);
    }

    public @Nullable BanditAttachment gripPart() {
        return part(BanditAttachmentSlot.GRIP);
    }

    public @Nullable BanditAttachment cylinderPart() {
        return part(BanditAttachmentSlot.CYLINDER);
    }

    /** The same gun with {@code part} in {@code slot}; null empties the slot. / 在该部位换上（或以 null 卸下）配件后的状态。 */
    public BanditRevolverState withPart(BanditAttachmentSlot slot, @Nullable BanditAttachment part) {
        int code = part == null ? BanditAttachment.NONE : part.code();
        return switch (slot) {
            case BARREL -> new BanditRevolverState(code, grip, cylinder, rounds, pending);
            case GRIP -> new BanditRevolverState(barrel, code, cylinder, rounds, pending);
            case CYLINDER -> new BanditRevolverState(barrel, grip, code, rounds, pending);
        };
    }

    public int capacity() {
        return BanditRevolverRules.chambers(cylinderPart());
    }

    /** Chambered plus pending. / 已上膛与待到位之和。 */
    public int loaded() {
        return rounds.size() + pending.size();
    }

    public boolean hasRoom() {
        return loaded() < capacity();
    }

    /** The round the next shot fires, or null when nothing is chambered. / 下一枪击发的子弹；未上膛时为 null。 */
    public @Nullable BanditAmmo nextRound() {
        return rounds.isEmpty() ? null : BanditAmmo.byCode(rounds.get(0));
    }

    /** The round at {@code index} counting chambered rounds first, then pending ones. / 先数已上膛、再数待到位的第 index 发。 */
    public @Nullable BanditAmmo roundAt(int index) {
        if (index < 0 || index >= loaded()) {
            return null;
        }
        return BanditAmmo.byCode(index < rounds.size() ? rounds.get(index) : pending.get(index - rounds.size()));
    }

    public BanditRevolverState withRoundFired() {
        if (rounds.isEmpty()) {
            return this;
        }
        return new BanditRevolverState(barrel, grip, cylinder, rounds.subList(1, rounds.size()), pending);
    }

    /** Pushes one round into the cylinder; unchanged when full. / 塞入一发子弹；弹巢已满时不变。 */
    public BanditRevolverState withPendingAdded(BanditAmmo ammo) {
        if (!hasRoom()) {
            return this;
        }
        List<Integer> next = new ArrayList<>(pending);
        next.add(ammo.code());
        return new BanditRevolverState(barrel, grip, cylinder, rounds, next);
    }

    /** Seats the oldest pending round. / 让最早塞入的一发到位。 */
    public BanditRevolverState withPendingSeated() {
        if (pending.isEmpty()) {
            return this;
        }
        List<Integer> seated = new ArrayList<>(rounds);
        seated.add(pending.get(0));
        return new BanditRevolverState(barrel, grip, cylinder, seated, pending.subList(1, pending.size()));
    }

    /** Takes out the round at {@code index} (same order as {@link #roundAt}). / 取出第 index 发（顺序同 {@link #roundAt}）。 */
    public BanditRevolverState withRoundRemoved(int index) {
        if (index < 0 || index >= loaded()) {
            return this;
        }
        List<Integer> nextRounds = new ArrayList<>(rounds);
        List<Integer> nextPending = new ArrayList<>(pending);
        if (index < nextRounds.size()) {
            nextRounds.remove(index);
        } else {
            nextPending.remove(index - nextRounds.size());
        }
        return new BanditRevolverState(barrel, grip, cylinder, nextRounds, nextPending);
    }
}
