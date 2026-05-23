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
public class BlockSignpoleEmptysign extends ElementsNebulaecraftMod.ModElement {
    @GameRegistry.ObjectHolder("nebulaecraft:signpole_emptysign")
    public static final Block block = null;

    public BlockSignpoleEmptysign(ElementsNebulaecraftMod instance) {
        super(instance, 32);
    }

    @Override
    public void initElements() {
        elements.blocks.add(() -> new BlockCustom().setRegistryName("signpole_emptysign"));

        elements.items.add(() -> new ItemHasVariantsAndSubtypes(block) {

            // 重写 addInformation 添加描述
            @SideOnly(Side.CLIENT)
            @Override
            public void addInformation(ItemStack stack, World worldIn, java.util.List<String> tooltip, net.minecraft.client.util.ITooltipFlag flagIn) {
                super.addInformation(stack, worldIn, tooltip, flagIn);

                // 获取当前物品的子类型 (metadata)
                int meta = stack.getMetadata();

                // 根据不同的 metadata 添加不同的灰色描述
                switch (meta) {
                    case 0: // 对应 subtype0
                        tooltip.add(net.minecraft.util.text.TextFormatting.GRAY + "28x21 - 适合用于道路方向指示牌");
                        break;
                    case 1: // 对应 subtype1
                        tooltip.add(net.minecraft.util.text.TextFormatting.GRAY + "8x16 - 适合用于指示单独目标");
                        break;
                    case 2: // 对应 subtype2
                        tooltip.add(net.minecraft.util.text.TextFormatting.GRAY + "12x24 - 适合用于信息密度较高的指示牌");
                        break;
                    case 3: // 对应 subtype3
                        tooltip.add(net.minecraft.util.text.TextFormatting.GRAY + "14x6 - 适合用于其他指示牌下方作为补充");
                        break;
                }
            }

        }.setSubtypeNames(new String[]{"subtype0", "subtype1", "subtype2", "subtype3"}).setRegistryName(block.getRegistryName()));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerModels(ModelRegistryEvent event) {
        BlockSignpoleEmptysign.BlockCustom.EnumType[] allSubtypes = BlockSignpoleEmptysign.BlockCustom.EnumType.values();
        for (BlockSignpoleEmptysign.BlockCustom.EnumType subtype : allSubtypes) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), subtype.getMetadata(), new ModelResourceLocation("nebulaecraft:signpole_emptysign_" + subtype.getName(), "inventory"));
        }
    }

    public static class BlockCustom extends Block {
        public static final PropertyDirection FACING = BlockHorizontal.FACING;
        public static final PropertyEnum<BlockCustom.EnumType> SUBTYPE = PropertyEnum.create("subtype", BlockCustom.EnumType.class);

        public BlockCustom() {
            super(Material.IRON);
            setUnlocalizedName("signpole_emptysign");
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
            BlockSignpoleEmptysign.BlockCustom.EnumType[] allSubtypes = BlockSignpoleEmptysign.BlockCustom.EnumType.values();
            for (BlockSignpoleEmptysign.BlockCustom.EnumType subtype : allSubtypes) {
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
						return new AxisAlignedBB(-0.375, 0, -0.1, 1.375, 1.3125, 0.1);
					case NORTH :
						return new AxisAlignedBB(-0.375, 0, 0.9, 1.375, 1.3125, 1.1);
					case EAST :
						return new AxisAlignedBB(-0.1, 0, -0.375, 0.1, 1.3125, 1.375);
					case WEST :
						return new AxisAlignedBB(0.9, 0, -0.375, 1.1, 1.3125, 1.375);

                }
            }
            else if(state.getValue(SUBTYPE).getMetadata()==1){
                switch (state.getValue(BlockHorizontal.FACING)) {
                    case SOUTH :
					default :
						return new AxisAlignedBB(0.25, 0, 0, 0.75, 1, 0.15);
					case NORTH :
						return new AxisAlignedBB(0.25, 0, 0.85, 0.75, 1, 1);
					case EAST :
						return new AxisAlignedBB(0, 0, 0.25, 0.15, 1, 0.75);
					case WEST :
						return new AxisAlignedBB(0.85, 0, 0.25, 1, 1, 0.75);

                }
            }
            else if(state.getValue(SUBTYPE).getMetadata()==2){
                switch (state.getValue(BlockHorizontal.FACING)) {
                    case SOUTH :
                    default :
                        return new AxisAlignedBB(0.125, 0, 0, 0.875, 1.5, 0.15);
                    case NORTH :
                        return new AxisAlignedBB(0.125, 0, 0.85, 0.875, 1.5, 1);
                    case EAST :
                        return new AxisAlignedBB(0, 0, 0.125, 0.15, 1.5, 0.875);
                    case WEST :
                        return new AxisAlignedBB(0.85, 0, 0.125, 1, 1.5, 0.875);

                }
            }
            else{
                switch (state.getValue(BlockHorizontal.FACING)) {
                    case SOUTH :
                    default :
                        return new AxisAlignedBB(0.0625, 0.5625, 0, 0.9375, 0.9375, 0.15);
                    case NORTH :
                        return new AxisAlignedBB(0.0625, 0.5625, 0.85, 0.9375, 0.9375, 1);
                    case EAST :
                        return new AxisAlignedBB(0, 0.5625, 0.0625, 0.15, 0.9375, 0.9375);
                    case WEST :
                        return new AxisAlignedBB(0.85, 0.5625, 0.0625, 1, 0.9375, 0.9375);

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
            BlockCustom.EnumType subtype = BlockCustom.EnumType.byMetadata(meta);
            return this.getDefaultState().withProperty(SUBTYPE, subtype).withProperty(FACING, placer.getHorizontalFacing().getOpposite());
        }

        public enum EnumType implements IStringSerializable {
            SUBTYPE0(0, "subtype0"),
            SUBTYPE1(1, "subtype1"),
            SUBTYPE2(2, "subtype2"),
            SUBTYPE3(3, "subtype3");

            private static final BlockSignpoleEmptysign.BlockCustom.EnumType[] META_LOOKUP = new BlockSignpoleEmptysign.BlockCustom.EnumType[values().length];

            static {
                for (BlockSignpoleEmptysign.BlockCustom.EnumType type : values()) {
                    META_LOOKUP[type.getMetadata()] = type;
                }
            }

            private final int meta;
            private final String name;

            EnumType(int i_meta, String i_name) {
                this.meta = i_meta;
                this.name = i_name;
            }

            public static BlockSignpoleEmptysign.BlockCustom.EnumType byMetadata(int meta) {
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
