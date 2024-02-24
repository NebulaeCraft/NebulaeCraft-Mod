package net.kuina.nebulaecraft.block;


import net.kuina.nebulaecraft.ElementsNebulaecraftMod;
import net.kuina.nebulaecraft.creativetab.TabNebulaecraftMetro;
import net.kuina.nebulaecraft.tileentity.TileEntityTdt;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.I18n;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Random;

@ElementsNebulaecraftMod.ModElement.Tag
public class BlockTdt extends ElementsNebulaecraftMod.ModElement {
    public BlockTdt(ElementsNebulaecraftMod instance) {
        super(instance, 108);
    }

    public static class BlockCustom extends Block {
        public static final PropertyDirection FACING = PropertyDirection.create("facing", EnumFacing.Plane.HORIZONTAL);
        public static final PropertyEnum<EnumRange> PROPERTYCOUNTDOWNRANGE = PropertyEnum.create("range", BlockCustom.EnumRange.class);

        public boolean canCountdown;

        public BlockCustom() {
            super(Material.GLASS);
            setUnlocalizedName("tdt");
            setSoundType(SoundType.METAL);
            setHardness(1F);
            setResistance(10F);
            setLightLevel(0.8F);
            setLightOpacity(0);
            setDefaultState(this.blockState.getBaseState()
                    .withProperty(FACING, EnumFacing.NORTH));
            canCountdown = false;
        }

        @SideOnly(Side.CLIENT)
        @Override
        public BlockRenderLayer getBlockLayer() {
            return BlockRenderLayer.CUTOUT_MIPPED;
        }

        @Override
        public boolean isFullCube(IBlockState state) {
            return false;
        }

        @Override
        public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
            switch (state.getValue(BlockHorizontal.FACING)) {
                case SOUTH:
                default:
                    return new AxisAlignedBB(0.21875, 0.15625, 0, 0.78125, 0.59375, 0.0625);
                case NORTH:
                    return new AxisAlignedBB(0.21875, 0.15625, 0.9375, 0.78125, 0.59375, 1);
                case EAST:
                    return new AxisAlignedBB(0, 0.15625, 0.21875, 0.0625, 0.59375, 0.78125);
                case WEST:
                    return new AxisAlignedBB(0.9375, 0.15625, 0.21875, 1, 0.59375, 0.78125);
            }
        }

        @Override
        public boolean isOpaqueCube(IBlockState state) {
            return false;
        }

        @Override
        public int damageDropped(IBlockState state) {
            return 0;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void getSubBlocks(CreativeTabs whichTab, NonNullList<ItemStack> items) {
            items.add(new ItemStack(this, 1, EnumTdt.DISABLED.getMetadata()));
        }

        @Override
        public IBlockState getStateFromMeta(int meta) {
            int countdownModeMeta = (meta & 0b0100) >> 2; // store countdown mode meta at 0100;
            int facingMeta = meta & 3; // 0011;
            return this.getDefaultState()
                    .withProperty(FACING, EnumFacing.getHorizontal(facingMeta))
                    .withProperty(PROPERTYCOUNTDOWNRANGE, EnumRange.byMetadata(countdownModeMeta));
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            int countdownModeMeta = state.getValue(PROPERTYCOUNTDOWNRANGE).getMetadata() << 2;
            int facingMeta = state.getValue(FACING).getHorizontalIndex();
            return countdownModeMeta | facingMeta;
        }

        @Override
        public IBlockState getActualState(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
            return state;
        }

        @Override
        protected BlockStateContainer createBlockState() {
            return new BlockStateContainer(this, FACING, PROPERTYCOUNTDOWNRANGE);
        }

        @Override
        public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing blockFaceClickedOn, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
            return this.getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite()).withProperty(PROPERTYCOUNTDOWNRANGE, EnumRange.TWENTY);
        }

        // Default blockstate is disabled
        @Override
        public void onBlockPlacedBy(World worldIn, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
            worldIn.setBlockState(pos, state.withProperty(FACING, placer.getHorizontalFacing().getOpposite()), 3);
            worldIn.scheduleUpdate(pos, this, 5);
        }

        @Override
        public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
            if (!worldIn.isRemote) {
                if (playerIn.isSneaking()) {
                    if (state.getValue(PROPERTYCOUNTDOWNRANGE) == EnumRange.TWENTY) {
                        worldIn.setBlockState(pos, state.withProperty(PROPERTYCOUNTDOWNRANGE, EnumRange.FORTY), 3);
                        playerIn.sendMessage(new TextComponentTranslation("message.tdt_mode_switch.name").appendText(": " + EnumRange.FORTY.getName()));

                    } else {
                        worldIn.setBlockState(pos, state.withProperty(PROPERTYCOUNTDOWNRANGE, EnumRange.TWENTY), 3);
                        playerIn.sendMessage(new TextComponentTranslation("message.tdt_mode_switch.name").appendText(": " + EnumRange.TWENTY.getName()));
                    }
                }
            }
            return true;
        }


        public static String getCountdown(World worldIn, BlockPos pos) {
            IBlockState state = worldIn.getBlockState(pos);
            Block block = state.getBlock();
            if (!block.getRegistryName().getResourcePath().contains("_"))
                return EnumTdt.DISABLED.getName();
            return block.getRegistryName().getResourcePath().split("_")[1];
        }

        public static int getCountdownInt(World worldIn, BlockPos pos) {
            String countdown = getCountdown(worldIn, pos);
            if (countdown.equals(EnumTdt.DISABLED.getName()))
                return -1;
            return Integer.parseInt(countdown);
        }

        public static void setState(World worldIn, BlockPos pos, String next) {
            TileEntityTdt tileentity = (TileEntityTdt) worldIn.getTileEntity(pos);
            IBlockState state = worldIn.getBlockState(pos);
            if (!next.equals(EnumTdt.DISABLED.getName())) {
                worldIn.setBlockState(pos,
                        Block.getBlockFromName("nebulaecraft:tdt_" + next)
                                .getDefaultState()
                                .withProperty(FACING, state.getValue(FACING))
                                .withProperty(PROPERTYCOUNTDOWNRANGE, state.getValue(PROPERTYCOUNTDOWNRANGE))
                        , 3);
            } else {
                worldIn.setBlockState(pos,
                        Block.getBlockFromName("nebulaecraft:tdt")
                                .getDefaultState()
                                .withProperty(FACING, state.getValue(FACING))
                                .withProperty(PROPERTYCOUNTDOWNRANGE, state.getValue(PROPERTYCOUNTDOWNRANGE))
                        , 3);
            }
            if (tileentity != null) {
                tileentity.validate();
                worldIn.setTileEntity(pos, tileentity);
            }
        }

        public static boolean isPowered(World worldIn, BlockPos pos) {
            return worldIn.isBlockPowered(pos);
        }

        public static EnumRange getRange(World worldIn, BlockPos pos) {
            IBlockState state = worldIn.getBlockState(pos);
            return state.getValue(PROPERTYCOUNTDOWNRANGE);
        }

        @Override
        public boolean hasTileEntity(IBlockState state) {
            return true;
        }

        @Override
        public TileEntity createTileEntity(World world, IBlockState state) {
            return new TileEntityTdt();
        }

        public ItemStack getItem(World worldIn, BlockPos pos, IBlockState state)
        {
            return new ItemStack(Block.getBlockFromName("nebulaecraft:tdt"));
        }

        public enum EnumTdt implements IStringSerializable {
            ZERO(0, "0"),
            ONE(1, "1"),
            TWO(2, "2"),
            THREE(3, "3"),
            FOUR(4, "4"),
            FIVE(5, "5"),
            SIX(6, "6"),
            SEVEN(7, "7"),
            EIGHT(8, "8"),
            NINE(9, "9"),
            TEN(10, "10"),
            ELEVEN(11, "11"),
            TWELVE(12, "12"),
            THIRTEEN(13, "13"),
            FOURTEEN(14, "14"),
            FIFTEEN(15, "15"),
            SIXTEEN(16, "16"),
            SEVENTEEN(17, "17"),
            EIGHTEEN(18, "18"),
            NINETEEN(19, "19"),
            TWENTY(20, "20"),
            TWENTY_ONE(21, "21"),
            TWENTY_TWO(22, "22"),
            TWENTY_THREE(23, "23"),
            TWENTY_FOUR(24, "24"),
            TWENTY_FIVE(25, "25"),
            TWENTY_SIX(26, "26"),
            TWENTY_SEVEN(27, "27"),
            TWENTY_EIGHT(28, "28"),
            TWENTY_NINE(29, "29"),
            THIRTY(30, "30"),
            THIRTY_ONE(31, "31"),
            THIRTY_TWO(32, "32"),
            THIRTY_THREE(33, "33"),
            THIRTY_FOUR(34, "34"),
            THIRTY_FIVE(35, "35"),
            THIRTY_SIX(36, "36"),
            THIRTY_SEVEN(37, "37"),
            THIRTY_EIGHT(38, "38"),
            THIRTY_NINE(39, "39"),
            FORTY(40, "40"),

            DISABLED(41, "disabled");


            private static final BlockCustom.EnumTdt[] META_LOOKUP = new BlockCustom.EnumTdt[values().length];

            static {
                for (BlockCustom.EnumTdt countdown : values()) {
                    META_LOOKUP[countdown.getMetadata()] = countdown;
                }
            }

            private final int meta;
            private final String name;

            EnumTdt(int i_meta, String i_name) {
                this.meta = i_meta;
                this.name = i_name;
            }

            public static BlockCustom.EnumTdt byMetadata(int meta) {
                if (meta < 0 || meta >= META_LOOKUP.length) {
                    meta = 0;
                }

                return META_LOOKUP[meta];
            }

            public int getMetadata() {
                return this.meta;
            }

            @Override
            public String toString() {
                return this.name;
            }

            public String getName() {
                return this.name;
            }
        }

        public enum EnumRange implements IStringSerializable {
            TWENTY(0, "20"),
            FORTY(1, "40");


            private static final BlockCustom.EnumRange[] META_LOOKUP = new BlockCustom.EnumRange[values().length];

            static {
                for (BlockCustom.EnumRange countdown : values()) {
                    META_LOOKUP[countdown.getMetadata()] = countdown;
                }
            }

            private final int meta;
            private final String name;

            EnumRange(int i_meta, String i_name) {
                this.meta = i_meta;
                this.name = i_name;
            }

            public static BlockCustom.EnumRange byMetadata(int meta) {
                if (meta < 0 || meta >= META_LOOKUP.length) {
                    meta = 0;
                }

                return META_LOOKUP[meta];
            }

            public int getMetadata() {
                return this.meta;
            }

            @Override
            public String toString() {
                return this.name;
            }

            public String getName() {
                return this.name;
            }
        }
    }
}
