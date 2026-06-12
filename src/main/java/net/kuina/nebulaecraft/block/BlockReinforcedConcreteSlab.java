
package net.kuina.nebulaecraft.block;

import net.kuina.nebulaecraft.ElementsNebulaecraftMod;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.NonNullList;
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
public class BlockReinforcedConcreteSlab extends ElementsNebulaecraftMod.ModElement {
	@GameRegistry.ObjectHolder("nebulaecraft:reinforced_concrete_slab")
	public static final Block block = null;

	public BlockReinforcedConcreteSlab(ElementsNebulaecraftMod instance) {
		super(instance, 53);
	}

	@Override
	public void initElements() {
		elements.blocks.add(() -> new BlockCustom().setRegistryName("reinforced_concrete_slab"));
		elements.items.add(() -> new ItemHasVariantsAndSubtypes(block).setSubtypeNames(new String[]{"subtype0", "subtype1"}).setRegistryName(block.getRegistryName()));
	}

	@SideOnly(Side.CLIENT)
	public void registerModels(ModelRegistryEvent event) {
		BlockReinforcedConcreteSlab.BlockCustom.EnumType[] allSubtypes = BlockReinforcedConcreteSlab.BlockCustom.EnumType.values();
		for (BlockReinforcedConcreteSlab.BlockCustom.EnumType subtype : allSubtypes) {
			ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), subtype.getMetadata(), new ModelResourceLocation("nebulaecraft:reinforced_concrete_slab_" + subtype.getName(), "inventory"));
		}
	}

	public static class BlockCustom extends Block {
		public static final PropertyEnum<EnumType> SUBTYPE = PropertyEnum.create("subtype", EnumType.class);

		public BlockCustom() {
			super(Material.ROCK);
			setUnlocalizedName("reinforced_concrete_slab");
			setSoundType(SoundType.STONE);
			setHardness(1F);
			setResistance(10F);
			setLightLevel(0F);
			setLightOpacity(0);
			setCreativeTab(CreativeTabs.BUILDING_BLOCKS);
		}

		@Override
		@SideOnly(Side.CLIENT)
		public void getSubBlocks(CreativeTabs whichTab, NonNullList<ItemStack> items) {
			BlockReinforcedConcreteSlab.BlockCustom.EnumType[] allSubtypes = BlockReinforcedConcreteSlab.BlockCustom.EnumType.values();
			for (BlockReinforcedConcreteSlab.BlockCustom.EnumType subtype : allSubtypes) {
				items.add(new ItemStack(this, 1, subtype.getMetadata()));
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

		@Override
		public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
			if (state.getValue(SUBTYPE).getMetadata() == 0) {
				return new AxisAlignedBB(0, 0, 0, 1, 0.5, 1);
			}
			else {
				return new AxisAlignedBB(0, 0.5, 0, 1, 1, 1);
			}
		}

		@Override
		protected net.minecraft.block.state.BlockStateContainer createBlockState(){
			return new net.minecraft.block.state.BlockStateContainer(this, SUBTYPE);
		}

		@Override
		public IBlockState getStateFromMeta(int meta) {
			return this.getDefaultState().withProperty(SUBTYPE, EnumType.byMetadata(meta));
		}

		@Override
		public int getMetaFromState(IBlockState state) {
			int metadata=(state.getValue(SUBTYPE).getMetadata());
			return metadata;
		}

		@Override
		public ItemStack getPickBlock(IBlockState state, RayTraceResult target, World world, BlockPos pos, EntityPlayer player) {
			super.getPickBlock(state, target, world, pos, player);
			return new ItemStack(this,1,getMetaFromState(state));
		}

		@Override
		public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
			EnumType subtype = EnumType.byMetadata(meta);
			return this.getDefaultState().withProperty(SUBTYPE, subtype);
		}

		public enum EnumType implements IStringSerializable {
			SUBTYPE0(0, "subtype0"),
			SUBTYPE1(1, "subtype1");

			private static final BlockReinforcedConcreteSlab.BlockCustom.EnumType[] META_LOOKUP = new BlockReinforcedConcreteSlab.BlockCustom.EnumType[values().length];

			static {
				for (BlockReinforcedConcreteSlab.BlockCustom.EnumType type : values()) {
					META_LOOKUP[type.getMetadata()] = type;
				}
			}

			private final int meta;
			private final String name;

			EnumType(int i_meta, String i_name) {
				this.meta = i_meta;
				this.name = i_name;
			}

			public static BlockReinforcedConcreteSlab.BlockCustom.EnumType byMetadata(int meta) {
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
