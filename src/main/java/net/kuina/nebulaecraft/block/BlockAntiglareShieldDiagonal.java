package net.kuina.nebulaecraft.block;

import net.kuina.nebulaecraft.ElementsNebulaecraftMod;
import net.kuina.nebulaecraft.creativetab.TabNebulaecraftRoad;
import net.minecraft.block.Block;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@ElementsNebulaecraftMod.ModElement.Tag
public class BlockAntiglareShieldDiagonal extends ElementsNebulaecraftMod.ModElement {
    @GameRegistry.ObjectHolder("nebulaecraft:antiglare_shield_diagonal")
    public static final Block block = null;

    public BlockAntiglareShieldDiagonal(ElementsNebulaecraftMod instance) {
        super(instance, 24);
    }

    @Override
    public void initElements() {
        elements.blocks.add(() -> new BlockCustom().setRegistryName("antiglare_shield_diagonal"));
        elements.items.add(() -> new ItemHasVariantsAndSubtypes(block).setSubtypeNames(new String[]{"subtype0", "subtype1"}).setRegistryName(block.getRegistryName()));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerModels(ModelRegistryEvent event) {
        BlockAntiglareShieldDiagonal.BlockCustom.EnumType[] allSubtypes = BlockAntiglareShieldDiagonal.BlockCustom.EnumType.values();
        for (BlockAntiglareShieldDiagonal.BlockCustom.EnumType subtype : allSubtypes) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), subtype.getMetadata(), new ModelResourceLocation("nebulaecraft:antiglare_shield_diagonal_" + subtype.getName(), "inventory"));
        }
    }

    public static class BlockCustom extends Block {
        public static final PropertyDirection FACING = BlockHorizontal.FACING;
        public static final PropertyEnum<EnumType> SUBTYPE = PropertyEnum.create("subtype", EnumType.class);

        public BlockCustom() {
            super(Material.IRON);
            setUnlocalizedName("antiglare_shield_diagonal");
            setSoundType(SoundType.METAL);
            setHardness(1F);
            setResistance(10F);
            setLightLevel(0F);
            setLightOpacity(0);
            setCreativeTab(TabNebulaecraftRoad.tab);
            this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void getSubBlocks(CreativeTabs whichTab, NonNullList<ItemStack> items) {
            BlockAntiglareShieldDiagonal.BlockCustom.EnumType[] allSubtypes = BlockAntiglareShieldDiagonal.BlockCustom.EnumType.values();
            for (BlockAntiglareShieldDiagonal.BlockCustom.EnumType subtype : allSubtypes) {
                items.add(new ItemStack(this, 1, subtype.getMetadata()));
            }
        }

        @Override
        public BlockRenderLayer getBlockLayer() {
            return BlockRenderLayer.CUTOUT_MIPPED;
        }

        @Override
        public boolean isFullCube(IBlockState state) {
            return false;
        }

        @Override
        public boolean isOpaqueCube(IBlockState state) {
            return false;
        }

        @Override
        public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
            if(state.getValue(SUBTYPE).getMetadata()==0){
                switch (state.getValue(BlockHorizontal.FACING)) {
                    case SOUTH :
                    default :
                        return new AxisAlignedBB(0.5, 0, 0.5, 1, 0.625, 1);
                    case NORTH :
                        return new AxisAlignedBB(0, 0, 0, 0.5, 0.625, 0.5);
                    case EAST :
                        return new AxisAlignedBB(0.5, 0, 0, 1, 0.625, 0.5);
                    case WEST :
                        return new AxisAlignedBB(0, 0, 0.5, 0.5, 0.625, 1);
                }
            }
            else{
                switch (state.getValue(BlockHorizontal.FACING)) {
                    case SOUTH :
                    default :
                        return new AxisAlignedBB(0.5, -0.5, 0.5, 1, 0.125, 1);
                    case NORTH :
                        return new AxisAlignedBB(0, -0.5, 0, 0.5, 0.125, 0.5);
                    case EAST :
                        return new AxisAlignedBB(0.5, -0.5, 0, 1, 0.125, 0.5);
                    case WEST :
                        return new AxisAlignedBB(0, -0.5, 0.5, 0.5, 0.125, 1);
                }
            }
        }

        @Override
        protected net.minecraft.block.state.BlockStateContainer createBlockState() {
            return new net.minecraft.block.state.BlockStateContainer(this, FACING, SUBTYPE);
        }

        @Override
        public IBlockState withRotation(IBlockState state, Rotation rot) {
            return state.withProperty(FACING, rot.rotate(state.getValue(FACING)));
        }

        @Override
        public IBlockState withMirror(IBlockState state, Mirror mirrorIn) {
            return state.withRotation(mirrorIn.toRotation(state.getValue(FACING)));
        }

        @Override
        public IBlockState getStateFromMeta(int meta) {
            return this.getDefaultState().withProperty(FACING, EnumFacing.getFront((meta % 4) + 2)).withProperty(SUBTYPE, EnumType.byMetadata(meta / 4));
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            int metadata=((state.getValue(FACING).getIndex() - 2) + (4 * state.getValue(SUBTYPE).getMetadata()));
            return metadata;
        }

        @Override
        public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
            super.getPickBlock(state, target, world, pos, player);
            return new ItemStack(this,1,getMetaFromState(state)/4);
        }

        @Override
        public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
            EnumType subtype = EnumType.byMetadata(meta);

            if (facing == EnumFacing.UP || facing == EnumFacing.DOWN)
                return this.getDefaultState().withProperty(SUBTYPE, subtype).withProperty(FACING, placer.getHorizontalFacing().getOpposite());

//            System.out.println(EnumFacing.getFront(facing.getIndex() - 2).getName2());
            return this.getDefaultState().withProperty(FACING, facing).withProperty(SUBTYPE, subtype);
        }

        public enum EnumType implements IStringSerializable {
            SUBTYPE0(0, "subtype0"),
            SUBTYPE1(1, "subtype1");

            private static final BlockAntiglareShieldDiagonal.BlockCustom.EnumType[] META_LOOKUP = new BlockAntiglareShieldDiagonal.BlockCustom.EnumType[values().length];

            static {
                for (BlockAntiglareShieldDiagonal.BlockCustom.EnumType type : values()) {
                    META_LOOKUP[type.getMetadata()] = type;
                }
            }

            private final int meta;
            private final String name;

            EnumType(int i_meta, String i_name) {
                this.meta = i_meta;
                this.name = i_name;
            }

            public static BlockAntiglareShieldDiagonal.BlockCustom.EnumType byMetadata(int meta) {
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
