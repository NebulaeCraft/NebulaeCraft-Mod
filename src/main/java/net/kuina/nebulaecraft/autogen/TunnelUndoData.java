package net.kuina.nebulaecraft.autogen;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TunnelUndoData extends WorldSavedData {
    private static final String NAME = "nebulaecraft_tunnel_undo";
    private int dimension;
    private boolean incomplete;
    private final List<Entry> entries = new ArrayList<>();

    public TunnelUndoData() {
        super(NAME);
    }

    public TunnelUndoData(String name) {
        super(name);
    }

    public static TunnelUndoData get(MinecraftServer server) {
        WorldServer overworld = server.getWorld(0);
        MapStorage storage = overworld.getMapStorage();
        TunnelUndoData data = (TunnelUndoData) storage.getOrLoadData(TunnelUndoData.class, NAME);
        if (data == null) {
            data = new TunnelUndoData();
            storage.setData(NAME, data);
        }
        return data;
    }

    public void begin(int dimension) {
        this.dimension = dimension;
        this.incomplete = true;
        this.entries.clear();
        markDirty();
    }

    public void capture(WorldServer world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        TileEntity tile = world.getTileEntity(pos);
        NBTTagCompound tileTag = tile == null ? null : tile.writeToNBT(new NBTTagCompound());
        entries.add(new Entry(pos, Block.getStateId(state), tileTag));
        markDirty();
    }

    public void finish() {
        incomplete = false;
        markDirty();
    }

    public void clear() {
        entries.clear();
        incomplete = false;
        markDirty();
    }

    public int getDimension() {
        return dimension;
    }

    public boolean isIncomplete() {
        return incomplete;
    }

    public boolean hasEntries() {
        return !entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    public List<Entry> reversedEntries() {
        List<Entry> result = new ArrayList<>(entries);
        Collections.reverse(result);
        return result;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        dimension = nbt.getInteger("Dimension");
        incomplete = nbt.getBoolean("Incomplete");
        entries.clear();
        NBTTagList list = nbt.getTagList("Entries", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            NBTTagCompound tile = entry.hasKey("Tile", 10) ? entry.getCompoundTag("Tile") : null;
            entries.add(new Entry(BlockPos.fromLong(entry.getLong("Position")), entry.getInteger("State"), tile));
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setInteger("Dimension", dimension);
        compound.setBoolean("Incomplete", incomplete);
        NBTTagList list = new NBTTagList();
        for (Entry value : entries) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setLong("Position", value.pos.toLong());
            entry.setInteger("State", value.stateId);
            if (value.tileData != null) {
                entry.setTag("Tile", value.tileData);
            }
            list.appendTag(entry);
        }
        compound.setTag("Entries", list);
        return compound;
    }

    public static final class Entry {
        public final BlockPos pos;
        public final int stateId;
        public final NBTTagCompound tileData;

        Entry(BlockPos pos, int stateId, NBTTagCompound tileData) {
            this.pos = pos;
            this.stateId = stateId;
            this.tileData = tileData;
        }

        public void restore(WorldServer world) {
            IBlockState state = Block.getStateById(stateId);
            world.setBlockState(pos, state, 2);
            if (tileData != null) {
                TileEntity tile = world.getTileEntity(pos);
                if (tile != null) {
                    tile.readFromNBT(tileData.copy());
                    tile.markDirty();
                }
            }
        }
    }
}
