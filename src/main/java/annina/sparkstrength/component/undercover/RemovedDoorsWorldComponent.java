package annina.sparkstrength.component.undercover;

import annina.sparkstrength.SparkStrength;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.Component;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.ArrayList;
import java.util.List;

/**
 * Every door the Undercover's tomahawk took away, exactly as it was (block state + block entity NBT), until it is put
 * back. Saved with the world, so a crash or a forced stop between the chop and the round end never loses a door: the
 * next server start restores it.
 * 战术斧拆掉的每扇门，原样记录（方块状态与方块实体 NBT），直到被放回。随世界保存，所以拆门后到回合结束之间即使崩溃或强制关服，
 * 门也不会丢：下次启动服务器时会还原。
 */
public final class RemovedDoorsWorldComponent implements Component {
    public static final ComponentKey<RemovedDoorsWorldComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("removed_doors"), RemovedDoorsWorldComponent.class
    );

    public record RemovedBlock(BlockPos pos, BlockState state, @Nullable NbtCompound blockEntity) {
    }

    private final List<RemovedBlock> removed = new ArrayList<>();

    public RemovedDoorsWorldComponent(World world) {
    }

    public void add(List<RemovedBlock> blocks) {
        removed.addAll(blocks);
    }

    public boolean isEmpty() {
        return removed.isEmpty();
    }

    /** Hands over everything recorded and forgets it. / 交出全部记录并清空。 */
    public List<RemovedBlock> takeAll() {
        List<RemovedBlock> taken = List.copyOf(removed);
        removed.clear();
        return taken;
    }

    @Override
    public void readFromNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        removed.clear();
        NbtList list = tag.getList("removed", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            BlockState state = NbtHelper.toBlockState(registryLookup.getWrapperOrThrow(RegistryKeys.BLOCK),
                    entry.getCompound("state"));
            NbtCompound blockEntity = entry.contains("block_entity", NbtElement.COMPOUND_TYPE)
                    ? entry.getCompound("block_entity") : null;
            removed.add(new RemovedBlock(BlockPos.fromLong(entry.getLong("pos")), state, blockEntity));
        }
    }

    @Override
    public void writeToNbt(@NotNull NbtCompound tag, RegistryWrapper.WrapperLookup registryLookup) {
        NbtList list = new NbtList();
        for (RemovedBlock block : removed) {
            NbtCompound entry = new NbtCompound();
            entry.putLong("pos", block.pos().asLong());
            entry.put("state", NbtHelper.fromBlockState(block.state()));
            if (block.blockEntity() != null) {
                entry.put("block_entity", block.blockEntity());
            }
            list.add(entry);
        }
        tag.put("removed", list);
    }
}
