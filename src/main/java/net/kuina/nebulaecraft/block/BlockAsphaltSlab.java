
package net.kuina.nebulaecraft.block;

import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.event.ModelRegistryEvent;

import net.minecraft.util.*;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.block.SoundType;
import net.minecraft.block.Block;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.IBlockState;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

import net.kuina.nebulaecraft.creativetab.TabNebulaecraftRoad;
import net.kuina.nebulaecraft.ElementsNebulaecraftMod;

@ElementsNebulaecraftMod.ModElement.Tag
public class BlockAsphaltSlab extends ElementsNebulaecraftMod.ModElement {
	@GameRegistry.ObjectHolder("nebulaecraft:asphalt_slab")
	public static final Block block = null;
	
	public BlockAsphaltSlab(ElementsNebulaecraftMod instance) {
		super(instance, 2);
	}

	@Override
	public void initElements() {
		elements.blocks.add(() -> new BlockCustom().setRegistryName("asphalt_slab"));
		elements.items.add(() -> new ItemHasVariantsAndSubtypes(block).setSubtypeNames(new String[]{"subtype0", "subtype1", "subtype2"}).setRegistryName(block.getRegistryName()));
	}

	@SideOnly(Side.CLIENT)
	public void registerModels(ModelRegistryEvent event) {
        BlockAsphaltSlab.BlockCustom.EnumType[] allSubtypes = BlockAsphaltSlab.BlockCustom.EnumType.values();
        for (BlockAsphaltSlab.BlockCustom.EnumType subtype : allSubtypes) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), subtype.getMetadata(), new ModelResourceLocation("nebulaecraft:asphalt_slab_" + subtype.getName(), "inventory"));
        }
    }
	
	public static class BlockCustom extends Block {
		public static final PropertyEnum<BlockCustom.EnumType> SUBTYPE = PropertyEnum.create("subtype", BlockCustom.EnumType.class);
		
		public BlockCustom() {
			super(Material.ROCK);
			setUnlocalizedName("asphalt_slab");
			setSoundType(SoundType.STONE);
			setHardness(1F);
			setResistance(10F);
			setLightLevel(0F);
			setLightOpacity(0);
			setCreativeTab(TabNebulaecraftRoad.tab);
		}

		@Override
        @SideOnly(Side.CLIENT)
        public void getSubBlocks(CreativeTabs whichTab, NonNullList<ItemStack> items) {
            BlockAsphaltSlab.BlockCustom.EnumType[] allSubtypes = BlockAsphaltSlab.BlockCustom.EnumType.values();
            for (BlockAsphaltSlab.BlockCustom.EnumType subtype : allSubtypes) {
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
			return new AxisAlignedBB(0, 0, 0, 1, 0.5, 1);
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
            BlockCustom.EnumType subtype = BlockCustom.EnumType.byMetadata(meta);
            return this.getDefaultState().withProperty(SUBTYPE, subtype);
        }
        
        public enum EnumType implements IStringSerializable {
            SUBTYPE0(0, "subtype0"),
            SUBTYPE1(1, "subtype1"),
            SUBTYPE2(2, "subtype2");

            private static final BlockAsphaltSlab.BlockCustom.EnumType[] META_LOOKUP = new BlockAsphaltSlab.BlockCustom.EnumType[values().length];

            static {
                for (BlockAsphaltSlab.BlockCustom.EnumType type : values()) {
                    META_LOOKUP[type.getMetadata()] = type;
                }
            }

            private final int meta;
            private final String name;

            EnumType(int i_meta, String i_name) {
                this.meta = i_meta;
                this.name = i_name;
            }

            public static BlockAsphaltSlab.BlockCustom.EnumType byMetadata(int meta) {
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
