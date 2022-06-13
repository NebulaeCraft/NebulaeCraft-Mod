
package net.kuina.nebulaecraft.block;

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

import net.kuina.nebulaecraft.creativetab.TabNebulaecraftMetro;
import net.kuina.nebulaecraft.ElementsNebulaecraftMod;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.RayTraceResult;

@ElementsNebulaecraftMod.ModElement.Tag
public class BlockCatenaryWireSupport extends ElementsNebulaecraftMod.ModElement {
	@GameRegistry.ObjectHolder("nebulaecraft:catenary_wire_support")
	public static final Block block = null;
	public BlockCatenaryWireSupport(ElementsNebulaecraftMod instance) {
		super(instance, 3);
	}

	@Override
	public void initElements() {
		elements.blocks.add(() -> new BlockCustom().setRegistryName("catenary_wire_support"));
		elements.items.add(() -> new ItemHasVariantsAndSubtypes(block).setSubtypeNames(new String[]{"subtype0", "subtype1"}).setRegistryName(block.getRegistryName()));
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void registerModels(ModelRegistryEvent event) {
        BlockCatenaryWireSupport.BlockCustom.EnumType[] allSubtypes = BlockCatenaryWireSupport.BlockCustom.EnumType.values();
        for (BlockCatenaryWireSupport.BlockCustom.EnumType subtype : allSubtypes) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), subtype.getMetadata(), new ModelResourceLocation("nebulaecraft:catenary_wire_support_" + subtype.getName(), "inventory"));
        }
    }

	public static class BlockCustom extends Block {
		public static final PropertyDirection FACING = BlockHorizontal.FACING;
		public static final PropertyEnum<BlockCustom.EnumType> SUBTYPE = PropertyEnum.create("subtype", BlockCustom.EnumType.class);
		public BlockCustom() {
			super(Material.IRON);
			setUnlocalizedName("catenary_wire_support");
			setSoundType(SoundType.METAL);
			setHardness(1F);
			setResistance(10F);
			setLightLevel(0F);
			setLightOpacity(0);
			setCreativeTab(TabNebulaecraftMetro.tab);
			this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
		}

		@Override
        @SideOnly(Side.CLIENT)
        public void getSubBlocks(CreativeTabs whichTab, NonNullList<ItemStack> items) {
            BlockCatenaryWireSupport.BlockCustom.EnumType[] allSubtypes = BlockCatenaryWireSupport.BlockCustom.EnumType.values();
            for (BlockCatenaryWireSupport.BlockCustom.EnumType subtype : allSubtypes) {
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
		public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
            if(state.getValue(SUBTYPE).getMetadata()==0){
                switch (state.getValue(BlockHorizontal.FACING)) {
                    case SOUTH:
                    default:
                        return new AxisAlignedBB(0.4375, 0, -0.375, 0.5625, 0.8, 1);
                    case NORTH:
                        return new AxisAlignedBB(0.4375, 0, 0, 0.5625, 0.8, 1.375);
                    case EAST:
                        return new AxisAlignedBB(-0.375, 0, 0.4375, 1, 0.8, 0.5625);
                    case WEST:
                        return new AxisAlignedBB(0, 0, 0.4375, 1.375, 0.8, 0.5625);
                }
            }
            else{
                switch (state.getValue(BlockHorizontal.FACING)) {
                    case SOUTH:
                    default:
                        return new AxisAlignedBB(0.4375, 0, -0.375, 0.5625, 0.8, 0.6);
                    case NORTH:
                        return new AxisAlignedBB(0.4375, 0, 0.4, 0.5625, 0.8, 1.375);
                    case EAST:
                        return new AxisAlignedBB(-0.375, 0, 0.4375, 0.6, 0.8, 0.5625);
                    case WEST:
                        return new AxisAlignedBB(0.4, 0, 0.4375, 1.375, 0.8, 0.5625);
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
            return this.getDefaultState().withProperty(FACING, EnumFacing.getFront((meta >= 4 ? meta - 4 : meta) + 2)).withProperty(SUBTYPE, meta >= 4 ? EnumType.SUBTYPE1 : EnumType.SUBTYPE0);
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return ((state.getValue(FACING).getIndex() - 2) + (state.getValue(SUBTYPE).getMetadata() == 0 ? 0 : 4))/4;
        }

        @Override
        public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
            super.getPickBlock(state, target, world, pos, player);
            return new ItemStack(this,1,getMetaFromState(state));
        }

        @Override
        public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
            BlockCustom.EnumType subtype = BlockCustom.EnumType.byMetadata(meta);

            if (facing == EnumFacing.UP || facing == EnumFacing.DOWN)
                return this.getDefaultState().withProperty(SUBTYPE, subtype).withProperty(FACING, placer.getHorizontalFacing().getOpposite());

            System.out.println(EnumFacing.getFront(facing.getIndex() - 2).getName2());
            return this.getDefaultState().withProperty(FACING, facing).withProperty(SUBTYPE, subtype);
        }

        @Override
        public boolean isOpaqueCube(IBlockState state) {
            return false;
        }

        public enum EnumType implements IStringSerializable {
            SUBTYPE0(0, "subtype0"),
            SUBTYPE1(1, "subtype1");

            private static final BlockCatenaryWireSupport.BlockCustom.EnumType[] META_LOOKUP = new BlockCatenaryWireSupport.BlockCustom.EnumType[values().length];

            static {
                for (BlockCatenaryWireSupport.BlockCustom.EnumType type : values()) {
                    META_LOOKUP[type.getMetadata()] = type;
                }
            }

            private final int meta;
            private final String name;

            EnumType(int i_meta, String i_name) {
                this.meta = i_meta;
                this.name = i_name;
            }

            public static BlockCatenaryWireSupport.BlockCustom.EnumType byMetadata(int meta) {
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
