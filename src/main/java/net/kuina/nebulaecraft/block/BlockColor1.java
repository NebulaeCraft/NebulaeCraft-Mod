
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

import net.kuina.nebulaecraft.creativetab.TabNebulaecraftColor;
import net.kuina.nebulaecraft.ElementsNebulaecraftMod;

@ElementsNebulaecraftMod.ModElement.Tag
public class BlockColor1 extends ElementsNebulaecraftMod.ModElement {
	@GameRegistry.ObjectHolder("nebulaecraft:color_1")
	public static final Block block = null;
	
	public BlockColor1(ElementsNebulaecraftMod instance) {
		super(instance, 91);
	}

	@Override
	public void initElements() {
		elements.blocks.add(() -> new BlockCustom().setRegistryName("color_1"));
		elements.items.add(() -> new ItemHasVariantsAndSubtypes(block).setSubtypeNames(new String[]{"subtype0", "subtype1", "subtype2", "subtype3", "subtype4", "subtype5", "subtype6", "subtype7", "subtype8", "subtype9", "subtype10", "subtype11"}).setRegistryName(block.getRegistryName()));
	}

	@SideOnly(Side.CLIENT)
	public void registerModels(ModelRegistryEvent event) {
        BlockColor1.BlockCustom.EnumType[] allSubtypes = BlockColor1.BlockCustom.EnumType.values();
        for (BlockColor1.BlockCustom.EnumType subtype : allSubtypes) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), subtype.getMetadata(), new ModelResourceLocation("nebulaecraft:color_1_" + subtype.getName(), "inventory"));
        }
    }
	
	public static class BlockCustom extends Block {
		public static final PropertyEnum<BlockCustom.EnumType> SUBTYPE = PropertyEnum.create("subtype", BlockCustom.EnumType.class);
		
		public BlockCustom() {
			super(Material.ROCK);
			setUnlocalizedName("color_1");
			setSoundType(SoundType.STONE);
			setHardness(1F);
			setResistance(10F);
			setLightLevel(0F);
			setLightOpacity(255);
			setCreativeTab(TabNebulaecraftColor.tab);
		}

		@Override
        @SideOnly(Side.CLIENT)
        public void getSubBlocks(CreativeTabs whichTab, NonNullList<ItemStack> items) {
            BlockColor1.BlockCustom.EnumType[] allSubtypes = BlockColor1.BlockCustom.EnumType.values();
            for (BlockColor1.BlockCustom.EnumType subtype : allSubtypes) {
                items.add(new ItemStack(this, 1, subtype.getMetadata()));
            }
        }

		@Override
		public BlockRenderLayer getBlockLayer() {
			return BlockRenderLayer.SOLID;
		}

		@Override
		public boolean isOpaqueCube(IBlockState state) {
			return true;
		}

		@Override
        public boolean isFullCube(IBlockState state) {
            return true;
        }

        @Override
		public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
			return new AxisAlignedBB(0, 0, 0, 1, 1, 1);
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
            SUBTYPE2(2, "subtype2"),
            SUBTYPE3(3, "subtype3"),
            SUBTYPE4(4, "subtype4"),
            SUBTYPE5(5, "subtype5"),
            SUBTYPE6(6, "subtype6"),
            SUBTYPE7(7, "subtype7"),
            SUBTYPE8(8, "subtype8"),
            SUBTYPE9(9, "subtype9"),
            SUBTYPE10(10, "subtype10"),
            SUBTYPE11(11, "subtype11");

            private static final BlockColor1.BlockCustom.EnumType[] META_LOOKUP = new BlockColor1.BlockCustom.EnumType[values().length];

            static {
                for (BlockColor1.BlockCustom.EnumType type : values()) {
                    META_LOOKUP[type.getMetadata()] = type;
                }
            }

            private final int meta;
            private final String name;

            EnumType(int i_meta, String i_name) {
                this.meta = i_meta;
                this.name = i_name;
            }

            public static BlockColor1.BlockCustom.EnumType byMetadata(int meta) {
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
