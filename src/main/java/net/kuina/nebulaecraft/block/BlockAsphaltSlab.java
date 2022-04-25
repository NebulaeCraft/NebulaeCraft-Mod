
package net.kuina.nebulaecraft.block;

import net.kuina.nebulaecraft.creativetab.TabNebulaecraftMetro;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.event.ModelRegistryEvent;

import net.minecraft.world.IBlockAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.item.Item;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.block.SoundType;
import net.minecraft.block.Block;

import net.kuina.nebulaecraft.creativetab.TabNebulaecraftRoad;
import net.kuina.nebulaecraft.ElementsNebulaecraftMod;

@ElementsNebulaecraftMod.ModElement.Tag
public class BlockAsphaltSlab extends ElementsNebulaecraftMod.ModElement {
	@GameRegistry.ObjectHolder("nebulaecraft:asphalt_slab")
	public static final Block block = null;
	public BlockAsphaltSlab(ElementsNebulaecraftMod instance) {
		super(instance, 1);
	}

	@Override
	public void initElements() {
		elements.blocks.add(() -> new BlockCustom().setRegistryName("asphalt_slab"));
		elements.items.add(() -> new ItemAsphaltSlab(block).setRegistryName(block.getRegistryName()));
	}

	@SideOnly(Side.CLIENT)
	@Override
	public void registerModels(ModelRegistryEvent event) {
		BlockCustom.EnumColour[] allColours = BlockCustom.EnumColour.values();
		for (BlockCustom.EnumColour colour : allColours) {
			ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), colour.getMetadata(), new ModelResourceLocation("nebulaecraft:asphalt_slab_"+colour.getName(), "inventory"));
		}
	}
	public static class BlockCustom extends Block {
		public BlockCustom() {
			super(Material.ROCK);
			setUnlocalizedName("asphalt_slab");
			setSoundType(SoundType.STONE);
			setHardness(1F);
			setResistance(10F);
			setLightLevel(0F);
			setLightOpacity(255);
			setCreativeTab(TabNebulaecraftRoad.tab);
		}

		@SideOnly(Side.CLIENT)
		@Override
		public BlockRenderLayer getBlockLayer() {
			return BlockRenderLayer.SOLID;
		}

		@Override
		public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
			return new AxisAlignedBB(0, 0, 0, 1, 0.5, 1);
		}

		public static final PropertyEnum PROPERTYCOLOUR = PropertyEnum.create("colour", BlockCustom.EnumColour.class);

		@Override
		public int damageDropped(IBlockState state)
		{
			BlockCustom.EnumColour enumColour = (BlockCustom.EnumColour)state.getValue(PROPERTYCOLOUR);
			return enumColour.getMetadata();
		}

		@Override
		@SideOnly(Side.CLIENT)
		public void getSubBlocks(CreativeTabs whichTab, NonNullList<ItemStack> items)
		{
			BlockCustom.EnumColour[] allColours = BlockCustom.EnumColour.values();
			for (BlockCustom.EnumColour colour : allColours) {
				items.add(new ItemStack(this, 1, colour.getMetadata()));
			}
		}

		@Override
		public IBlockState getStateFromMeta(int meta)
		{
			return this.getDefaultState().withProperty(PROPERTYCOLOUR, BlockCustom.EnumColour.byMetadata(meta));
		}

		@Override
		public int getMetaFromState(IBlockState state)
		{
			BlockCustom.EnumColour colour = (BlockCustom.EnumColour)state.getValue(PROPERTYCOLOUR);
			return colour.getMetadata();
		}

		@Override
		public IBlockState getActualState(IBlockState state, IBlockAccess worldIn, BlockPos pos)
		{
			return state;
		}

		@Override
		protected BlockStateContainer createBlockState()
		{
			return new BlockStateContainer(this, PROPERTYCOLOUR);
		}

		@Override
		public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing blockFaceClickedOn, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer)
		{
			BlockCustom.EnumColour colour = BlockCustom.EnumColour.byMetadata(meta);

			return this.getDefaultState().withProperty(PROPERTYCOLOUR, colour);
		}

		public enum EnumColour implements IStringSerializable
		{
			BLACK(0, "black"),
			RED(1, "red"),
			GREEN(2, "green");

			public int getMetadata()
			{
				return this.meta;
			}

			@Override
			public String toString()
			{
				return this.name;
			}

			public static BlockCustom.EnumColour byMetadata(int meta)
			{
				if (meta < 0 || meta >= META_LOOKUP.length)
				{
					meta = 0;
				}

				return META_LOOKUP[meta];
			}

			public String getName()
			{
				return this.name;
			}

			private final int meta;
			private final String name;
			private static final BlockCustom.EnumColour[] META_LOOKUP = new BlockCustom.EnumColour[values().length];

			EnumColour(int i_meta, String i_name)
			{
				this.meta = i_meta;
				this.name = i_name;
			}

			static
			{
				for (BlockCustom.EnumColour colour : values()) {
					META_LOOKUP[colour.getMetadata()] = colour;
				}
			}
		}
	}
}
