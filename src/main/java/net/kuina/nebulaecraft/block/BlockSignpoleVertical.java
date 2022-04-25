
package net.kuina.nebulaecraft.block;

import net.minecraft.block.BlockTallGrass;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.event.ModelRegistryEvent;

import net.minecraft.world.World;
import net.minecraft.world.IBlockAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.Item;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.material.Material;
import net.minecraft.block.SoundType;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.Block;

import net.kuina.nebulaecraft.creativetab.TabNebulaecraftRoad;
import net.kuina.nebulaecraft.ElementsNebulaecraftMod;
import scala.reflect.internal.Types;

@ElementsNebulaecraftMod.ModElement.Tag
public class BlockSignpoleVertical extends ElementsNebulaecraftMod.ModElement {
	@GameRegistry.ObjectHolder("nebulaecraft:signpole_vertical")
	public static final Block block = null;
	public BlockSignpoleVertical(ElementsNebulaecraftMod instance) {
		super(instance, 10);
	}

	@Override
	public void initElements() {
		elements.blocks.add(() -> new BlockCustom().setRegistryName("signpole_vertical"));
		elements.items.add(() -> new ItemHasVarientsAndSubtypes(block).setSubtypeNames(new String[]{"subtype0", "subtype1"}).setRegistryName(block.getRegistryName()));
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void registerModels(ModelRegistryEvent event) {
        BlockSignpoleVertical.BlockCustom.EnumType[] allSubtypes = BlockSignpoleVertical.BlockCustom.EnumType.values();
        for (BlockSignpoleVertical.BlockCustom.EnumType subtype : allSubtypes) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), subtype.getMetadata(), new ModelResourceLocation("nebulaecraft:signpole_vertical_" + subtype.getName(), "inventory"));
        }
    }
    
	public static class BlockCustom extends Block {
		public static final PropertyDirection FACING = BlockHorizontal.FACING;
		public static final PropertyEnum<BlockCustom.EnumType> SUBTYPE = PropertyEnum.<BlockCustom.EnumType>create("subtype", BlockCustom.EnumType.class);
		public BlockCustom() {
			super(Material.IRON);
			setUnlocalizedName("signpole_vertical");
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
            BlockSignpoleVertical.BlockCustom.EnumType[] allSubtypes = BlockSignpoleVertical.BlockCustom.EnumType.values();
            for (BlockSignpoleVertical.BlockCustom.EnumType subtype : allSubtypes) {
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
                switch ((EnumFacing) state.getValue(BlockHorizontal.FACING)) {
                    case SOUTH :
					default :
						return new AxisAlignedBB(0.4375, 0, 0.875, 0.5625, 1, 1);
					case NORTH :
						return new AxisAlignedBB(0.4375, 0, 0, 0.5625, 1, 0.125);
					case EAST :
						return new AxisAlignedBB(0.875, 0, 0.4375, 1, 1, 0.5625);
					case WEST :
						return new AxisAlignedBB(0, 0, 0.4375, 0.125, 1, 0.5625);

                }
            }
            else if(state.getValue(SUBTYPE).getMetadata()==1){
                switch ((EnumFacing) state.getValue(BlockHorizontal.FACING)) {
                    case SOUTH :
					default :
						return new AxisAlignedBB(0.4375, 0, 0.875, 0.5625, 0.5, 1);
					case NORTH :
						return new AxisAlignedBB(0.4375, 0, 0, 0.5625, 0.5, 0.125);
					case EAST :
						return new AxisAlignedBB(0.875, 0, 0.4375, 1, 0.5, 0.5625);
					case WEST :
						return new AxisAlignedBB(0, 0, 0.4375, 0.125, 0.5, 0.5625);

                }
            }
            else if(state.getValue(SUBTYPE).getMetadata()==2){
                switch ((EnumFacing) state.getValue(BlockHorizontal.FACING)) {
                    case SOUTH :
					default :
						return new AxisAlignedBB(0.4375, 0, 0.875, 1, 1.3125, 1);
					case NORTH :
						return new AxisAlignedBB(0, 0, 0, 0.5625, 1.3125, 0.125);
					case EAST :
						return new AxisAlignedBB(0.875, 0, 0, 1, 1.3125, 0.5625);
					case WEST :
						return new AxisAlignedBB(0, 0, 0.4375, 0.125, 1.3125, 1);

                }
            }
            else{
                switch ((EnumFacing) state.getValue(BlockHorizontal.FACING)) {
                    case SOUTH :
					default :
						return new AxisAlignedBB(0, 0, 0.875, 0.5625, 1.3125, 1);
					case NORTH :
						return new AxisAlignedBB(0.4375, 0, 0, 1, 1.3125, 0.125);
					case EAST :
						return new AxisAlignedBB(0.875, 0, 0.4375, 1, 1.3125, 1);
					case WEST :
						return new AxisAlignedBB(0, 0, 0, 0.125, 1.3125, 0.5625);

                }
            }
		}

		@Override
        protected net.minecraft.block.state.BlockStateContainer createBlockState() {
            return new net.minecraft.block.state.BlockStateContainer(this, new IProperty[]{FACING, SUBTYPE});
        }

        @Override
        public IBlockState withRotation(IBlockState state, Rotation rot) {
            return state.withProperty(FACING, rot.rotate((EnumFacing) state.getValue(FACING)));
        }

        @Override
        public IBlockState withMirror(IBlockState state, Mirror mirrorIn) {
            return state.withRotation(mirrorIn.toRotation((EnumFacing) state.getValue(FACING)));
        }

        @Override
        public IBlockState getStateFromMeta(int meta) {
            return this.getDefaultState().withProperty(FACING, EnumFacing.getFront((meta >= 4 ? meta - 4 : meta) + 2)).withProperty(SUBTYPE, meta >= 4 ? EnumType.SUBTYPE1 : EnumType.SUBTYPE0);
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return ((((EnumFacing) state.getValue(FACING)).getIndex() - 2) + (state.getValue(SUBTYPE).getMetadata() == 0 ? 0 : 4));
        }

        @Override
        public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
            BlockCustom.EnumType subtype = BlockCustom.EnumType.byMetadata(meta);

            if (facing == EnumFacing.UP || facing == EnumFacing.DOWN)
                return this.getDefaultState().withProperty(SUBTYPE, subtype).withProperty(FACING, placer.getHorizontalFacing().getOpposite());

            System.out.println(EnumFacing.getFront(facing.getIndex() - 2).getName2());
            return this.getDefaultState().withProperty(FACING, facing).withProperty(SUBTYPE, subtype);
        }

        public static enum EnumType implements IStringSerializable {
            SUBTYPE0(0, "subtype0"),
            SUBTYPE1(1, "subtype1"),
            SUBTYPE2(2, "subtype2"),
            SUBTYPE3(3, "subtype3");

            private static final BlockSignpoleVertical.BlockCustom.EnumType[] META_LOOKUP = new BlockSignpoleVertical.BlockCustom.EnumType[values().length];

            static {
                for (BlockSignpoleVertical.BlockCustom.EnumType type : values()) {
                    META_LOOKUP[type.getMetadata()] = type;
                }
            }

            private final int meta;
            private final String name;

            private EnumType(int i_meta, String i_name) {
                this.meta = i_meta;
                this.name = i_name;
            }

            public static BlockSignpoleVertical.BlockCustom.EnumType byMetadata(int meta) {
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
