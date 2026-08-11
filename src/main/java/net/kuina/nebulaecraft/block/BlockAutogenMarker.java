package net.kuina.nebulaecraft.block;

import net.kuina.nebulaecraft.autogen.AutogenSelection;
import net.kuina.nebulaecraft.autogen.AutogenPermissions;
import net.kuina.nebulaecraft.creativetab.TabNebulaecraftMetro;
import net.kuina.nebulaecraft.item.ItemAutogenWand;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class BlockAutogenMarker extends Block {
    public static final PropertyDirection FACING = BlockHorizontal.FACING;
    private static final AxisAlignedBB BOX_NORTH = new AxisAlignedBB(
            7.0 / 16.0, 0.0, 1.5 / 16.0,
            9.0 / 16.0, 13.5 / 16.0, 9.0 / 16.0);
    private static final AxisAlignedBB BOX_EAST = new AxisAlignedBB(
            7.0 / 16.0, 0.0, 7.0 / 16.0,
            14.5 / 16.0, 13.5 / 16.0, 9.0 / 16.0);
    private static final AxisAlignedBB BOX_SOUTH = new AxisAlignedBB(
            7.0 / 16.0, 0.0, 7.0 / 16.0,
            9.0 / 16.0, 13.5 / 16.0, 14.5 / 16.0);
    private static final AxisAlignedBB BOX_WEST = new AxisAlignedBB(
            1.5 / 16.0, 0.0, 7.0 / 16.0,
            9.0 / 16.0, 13.5 / 16.0, 9.0 / 16.0);

    public BlockAutogenMarker() {
        super(Material.IRON);
        setSoundType(SoundType.METAL);
        setHardness(1.0F);
        setResistance(10.0F);
        setCreativeTab(TabNebulaecraftMetro.tab);
        setDefaultState(blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
    }

    @Override
    protected BlockStateContainer createBlockState() {
        return new BlockStateContainer(this, FACING);
    }

    @Override
    public IBlockState getStateFromMeta(int meta) {
        return getDefaultState().withProperty(FACING, EnumFacing.getHorizontal(meta & 3));
    }

    @Override
    public int getMetaFromState(IBlockState state) {
        return state.getValue(FACING).getHorizontalIndex();
    }

    @Override
    public IBlockState withRotation(IBlockState state, Rotation rotation) {
        return state.withProperty(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public IBlockState withMirror(IBlockState state, Mirror mirror) {
        return state.withRotation(mirror.toRotation(state.getValue(FACING)));
    }

    @Override
    public IBlockState getStateForPlacement(World world, BlockPos pos, EnumFacing side, float hitX, float hitY, float hitZ,
                                            int meta, EntityLivingBase placer) {
        return getDefaultState().withProperty(FACING, placer.getHorizontalFacing());
    }

    @Override
    public boolean onBlockActivated(World world, BlockPos pos, IBlockState state, EntityPlayer player, EnumHand hand,
                                    EnumFacing side, float hitX, float hitY, float hitZ) {
        ItemStack held = player.getHeldItem(hand);
        if (held.getItem() instanceof ItemAutogenWand) {
            if (!world.isRemote) {
                if (!AutogenPermissions.canUse(player)) {
                    AutogenPermissions.sendDenied(player);
                } else {
                    int index = AutogenSelection.select(player, pos, state.getValue(FACING));
                    player.sendMessage(new TextComponentTranslation(index == 1
                            ? "message.nebulaecraft.autogen.start_selected"
                            : "message.nebulaecraft.autogen.end_selected", pos.getX(), pos.getY(), pos.getZ()));
                }
            }
            return true;
        }

        if (held.isEmpty()) {
            if (!world.isRemote) {
                IBlockState rotated = state.withRotation(Rotation.CLOCKWISE_90);
                world.setBlockState(pos, rotated, 3);
                player.sendMessage(new TextComponentTranslation("message.nebulaecraft.autogen.marker_rotated",
                        rotated.getValue(FACING).getName()));
            }
            return true;
        }
        return false;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        switch (state.getValue(FACING)) {
            case EAST:
                return BOX_EAST;
            case SOUTH:
                return BOX_SOUTH;
            case WEST:
                return BOX_WEST;
            case NORTH:
            default:
                return BOX_NORTH;
        }
    }

    @Override
    public BlockRenderLayer getBlockLayer() {
        return BlockRenderLayer.CUTOUT_MIPPED;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }
}
