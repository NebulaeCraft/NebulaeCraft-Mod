
package net.kuina.nebulaecraft.block;

import net.kuina.nebulaecraft.creativetab.TabNebulaecraftMetro;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.event.ModelRegistryEvent;

import net.minecraft.world.World;
import net.minecraft.world.IBlockAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.Rotation;
import net.minecraft.util.Mirror;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.BlockRenderLayer;
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
import net.kuina.nebulaecraft.ElementsNebulaecraftMod;


@ElementsNebulaecraftMod.ModElement.Tag
public class BlockScreenDoorBig extends ElementsNebulaecraftMod.ModElement {

	public BlockScreenDoorBig(ElementsNebulaecraftMod instance) {
		super(instance, 40);
	}

	public static final class BlockCustom extends Block {
		public static final PropertyDirection FACING = BlockHorizontal.FACING;
		public BlockCustom() {
			super(Material.IRON);
			setSoundType(SoundType.METAL);
			setHardness(1F);
			setResistance(10F);
			setLightLevel(0F);
			setLightOpacity(0);
			setCreativeTab(TabNebulaecraftMetro.tab);
			this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
		}

		@SideOnly(Side.CLIENT)
		@Override
		public BlockRenderLayer getBlockLayer() {
			return BlockRenderLayer.TRANSLUCENT;
		}

		@Override
		public boolean isFullCube(IBlockState state) {
			return false;
		}

		@Override
		public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {  //TODO Input AABB data SMALL
			switch (state.getValue(BlockHorizontal.FACING)) {
				case SOUTH :
				default :
					return new AxisAlignedBB(0, 0, 0.75, 1, 2, 1);
				case NORTH :
					return new AxisAlignedBB(0, 0, 0, 1, 2, 0.25);
				case EAST :
					return new AxisAlignedBB(0.75, 0, 0, 1, 2, 1);
				case WEST :
					return new AxisAlignedBB(0, 0, 0, 0.25, 2, 1);
			}
		}

		@Override
		protected net.minecraft.block.state.BlockStateContainer createBlockState() {
			return new net.minecraft.block.state.BlockStateContainer(this, FACING);
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
			return this.getDefaultState().withProperty(FACING, EnumFacing.getFront(meta+2));
		}

		@Override
		public int getMetaFromState(IBlockState state) {
			return (state.getValue(FACING).getIndex()-2);
		}

		@Override
		public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
			return this.getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
		}

		@Override
		public boolean isOpaqueCube(IBlockState state) {
			return false;
		}
	}
}
