package net.kuina.nebulaecraft.autogen;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

public final class AutogenSelection {
    private static final String ROOT_KEY = "NebulaeCraftAutogen";
    private static final String START_KEY = "Start";
    private static final String END_KEY = "End";

    private AutogenSelection() {
    }

    public static Selection get(EntityPlayer player) {
        NBTTagCompound root = getRoot(player, false);
        return new Selection(readAnchor(root, START_KEY), readAnchor(root, END_KEY));
    }

    public static int select(EntityPlayer player, BlockPos pos, EnumFacing facing) {
        NBTTagCompound root = getRoot(player, true);
        Anchor start = readAnchor(root, START_KEY);
        Anchor end = readAnchor(root, END_KEY);
        Anchor selected = new Anchor(player.dimension, pos, facing);

        if (start == null || end != null) {
            root.setTag(START_KEY, selected.write());
            root.removeTag(END_KEY);
            return 1;
        }

        root.setTag(END_KEY, selected.write());
        return 2;
    }

    public static void clear(EntityPlayer player) {
        NBTTagCompound persisted = getPersisted(player, true);
        persisted.removeTag(ROOT_KEY);
    }

    private static Anchor readAnchor(NBTTagCompound root, String key) {
        if (root == null || !root.hasKey(key, 10)) {
            return null;
        }
        NBTTagCompound tag = root.getCompoundTag(key);
        EnumFacing facing = EnumFacing.getFront(tag.getInteger("Facing"));
        if (!facing.getAxis().isHorizontal()) {
            return null;
        }
        return new Anchor(tag.getInteger("Dimension"), BlockPos.fromLong(tag.getLong("Position")), facing);
    }

    private static NBTTagCompound getRoot(EntityPlayer player, boolean create) {
        NBTTagCompound persisted = getPersisted(player, create);
        if (!persisted.hasKey(ROOT_KEY, 10)) {
            if (!create) {
                return new NBTTagCompound();
            }
            persisted.setTag(ROOT_KEY, new NBTTagCompound());
        }
        return persisted.getCompoundTag(ROOT_KEY);
    }

    private static NBTTagCompound getPersisted(EntityPlayer player, boolean create) {
        NBTTagCompound entityData = player.getEntityData();
        if (!entityData.hasKey(EntityPlayer.PERSISTED_NBT_TAG, 10)) {
            if (!create) {
                return new NBTTagCompound();
            }
            entityData.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return entityData.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    public static final class Selection {
        public final Anchor start;
        public final Anchor end;

        private Selection(Anchor start, Anchor end) {
            this.start = start;
            this.end = end;
        }

        public boolean isComplete() {
            return start != null && end != null;
        }
    }

    public static final class Anchor {
        public final int dimension;
        public final BlockPos pos;
        public final EnumFacing facing;

        private Anchor(int dimension, BlockPos pos, EnumFacing facing) {
            this.dimension = dimension;
            this.pos = pos;
            this.facing = facing;
        }

        private NBTTagCompound write() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("Dimension", dimension);
            tag.setLong("Position", pos.toLong());
            tag.setInteger("Facing", facing.getIndex());
            return tag;
        }
    }
}
