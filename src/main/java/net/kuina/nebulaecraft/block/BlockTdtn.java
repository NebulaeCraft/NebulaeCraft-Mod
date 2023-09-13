package net.kuina.nebulaecraft.block;


import net.kuina.nebulaecraft.ElementsNebulaecraftMod;
import net.kuina.nebulaecraft.creativetab.TabNebulaecraftMetro;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.PropertyEnum;
import net.minecraft.block.state.BlockStateContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IStringSerializable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Random;

@ElementsNebulaecraftMod.ModElement.Tag
public class BlockTdtn extends ElementsNebulaecraftMod.ModElement {
    @GameRegistry.ObjectHolder("nebulaecraft:tdt_n")
    public static final Block block = null;

    public BlockTdtn(ElementsNebulaecraftMod instance) {
        super(instance, 108);
    }

    @Override
    public void initElements() {
        elements.blocks.add(() -> new BlockCustom().setRegistryName("tdt_n"));
        elements.items.add(() -> new ItemTdtn(block).setRegistryName(block.getRegistryName()));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerModels(ModelRegistryEvent event) {
        BlockCustom.EnumTdtn[] allTdtns = BlockCustom.EnumTdtn.values();
        for (BlockCustom.EnumTdtn countdown : allTdtns) {
            ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), countdown.getMetadata(), new ModelResourceLocation("nebulaecraft:tdt_n_" + countdown.getName(), "inventory"));
        }
    }

    public static class BlockCustom extends Block {
        public static final PropertyEnum PROPERTYCOUNTDOWN = PropertyEnum.create("countdown", BlockCustom.EnumTdtn.class);

        public boolean canCountdown;

        public BlockCustom() {
            super(Material.GLASS);
            setUnlocalizedName("tdt_n");
            setSoundType(SoundType.METAL);
            setHardness(1F);
            setResistance(10F);
            setLightLevel(0.8F);
            setLightOpacity(0);
            setCreativeTab(TabNebulaecraftMetro.tab);
            setDefaultState(this.blockState.getBaseState().withProperty(PROPERTYCOUNTDOWN, EnumTdtn.DISABLED));
            canCountdown = false;
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
        public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
            return new AxisAlignedBB(0.21875, 0.15625, 0, 0.78125, 0.59375, 0.0625);
        }

        @Override
        public boolean isOpaqueCube(IBlockState state) {
            return false;
        }

        @Override
        public int damageDropped(IBlockState state) {
            BlockCustom.EnumTdtn enumTdtn = (BlockCustom.EnumTdtn) state.getValue(PROPERTYCOUNTDOWN);
            return enumTdtn.getMetadata();
        }

        @Override
        public IBlockState getStateFromMeta(int meta) {
            return this.getDefaultState().withProperty(PROPERTYCOUNTDOWN, BlockCustom.EnumTdtn.byMetadata(meta));
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            BlockCustom.EnumTdtn countdown = (BlockCustom.EnumTdtn) state.getValue(PROPERTYCOUNTDOWN);
            return countdown.getMetadata();
        }

        @Override
        public IBlockState getActualState(IBlockState state, IBlockAccess worldIn, BlockPos pos) {
            return state;
        }

        @Override
        protected BlockStateContainer createBlockState() {
            return new BlockStateContainer(this, PROPERTYCOUNTDOWN);
        }

        @Override
        public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing blockFaceClickedOn, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
            BlockCustom.EnumTdtn countdown = BlockCustom.EnumTdtn.byMetadata(meta);

            return this.getDefaultState().withProperty(PROPERTYCOUNTDOWN, countdown);
        }

        // Default blockstate is disabled
        @Override
        public void onBlockPlacedBy(World worldIn, BlockPos pos, IBlockState state, EntityLivingBase placer, ItemStack stack) {
            worldIn.setBlockState(pos, state.withProperty(PROPERTYCOUNTDOWN, EnumTdtn.DISABLED), 3);
            worldIn.scheduleUpdate(pos, this, 5);
        }


        // Callback on World#scheduleUpdate
        @Override
        public void updateTick(World worldIn, BlockPos pos, IBlockState state, Random rand) {
            super.updateTick(worldIn, pos, state, rand);
            if (state.getBlock() == this) {
                BlockCustom.EnumTdtn countdown = (BlockCustom.EnumTdtn) state.getValue(PROPERTYCOUNTDOWN);

                if(!worldIn.isBlockPowered(pos)){
                    if (countdown != EnumTdtn.DISABLED) {
                        worldIn.setBlockState(pos, state.withProperty(PROPERTYCOUNTDOWN, EnumTdtn.DISABLED), 3);
                    }
                    // detect if block is powered every 5 ticks
                    worldIn.scheduleUpdate(pos, this, 5);
                    canCountdown = true;
                    return;
                }

                // if block is newly powered, start countdown
                if(countdown == EnumTdtn.DISABLED && canCountdown && worldIn.isBlockPowered(pos)){
                    worldIn.setBlockState(pos, state.withProperty(PROPERTYCOUNTDOWN, EnumTdtn.FOURTEEN), 3);
                    worldIn.scheduleUpdate(pos, this, 20);
                    return;
                }

                // display ZERO state for 3 seconds
                if(countdown == EnumTdtn.ONE){
                    worldIn.setBlockState(pos, state.withProperty(PROPERTYCOUNTDOWN, EnumTdtn.ZERO), 3);
                    worldIn.scheduleUpdate(pos, this, 3*20);
                    return;
                }

                // end countdown, detect isBlockPowered every 5 ticks
                if(countdown == EnumTdtn.ZERO){
                    worldIn.setBlockState(pos, state.withProperty(PROPERTYCOUNTDOWN, EnumTdtn.DISABLED), 3);
                    canCountdown = false;
                    worldIn.scheduleUpdate(pos, this, 5);
                    return;
                }

                // countdown-=1
                if(countdown.getMetadata()>=2&&countdown.getMetadata()<=14){
                    worldIn.setBlockState(pos, state.withProperty(PROPERTYCOUNTDOWN, BlockCustom.EnumTdtn.byMetadata(countdown.getMetadata()-1)), 3);
                    worldIn.scheduleUpdate(pos, this, 20);
                    return;
                }

                // default schedule update
                worldIn.scheduleUpdate(pos, this, 5);
            }
        }

        public enum EnumTdtn implements IStringSerializable {
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
            DISABLED(15, "disabled");


            private static final BlockCustom.EnumTdtn[] META_LOOKUP = new BlockCustom.EnumTdtn[values().length];

            static {
                for (BlockCustom.EnumTdtn countdown : values()) {
                    META_LOOKUP[countdown.getMetadata()] = countdown;
                }
            }

            private final int meta;
            private final String name;

            EnumTdtn(int i_meta, String i_name) {
                this.meta = i_meta;
                this.name = i_name;
            }

            public static BlockCustom.EnumTdtn byMetadata(int meta) {
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
