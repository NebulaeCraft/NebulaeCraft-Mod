package net.kuina.nebulaecraft.block;

import net.kuina.nebulaecraft.NebulaecraftMod;
import net.kuina.nebulaecraft.creativetab.TabNebulaecraftRoad;
import net.kuina.nebulaecraft.tileentity.TileEntityRoadmarkText;
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
import net.minecraft.util.EnumHand;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.Item;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.material.Material;
import net.minecraft.block.SoundType;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.kuina.nebulaecraft.ElementsNebulaecraftMod;

@ElementsNebulaecraftMod.ModElement.Tag
public class BlockRoadmarkText extends ElementsNebulaecraftMod.ModElement {

    @GameRegistry.ObjectHolder("nebulaecraft:roadmark_text")
    public static final Block block = null;

    // 我们暂时预留一个 GUI ID，之后会在第三步用到
    public static final int GUI_ID = 1001;

    public BlockRoadmarkText(ElementsNebulaecraftMod instance) {
        super(instance, 200); // 注意：请确保 200 这个排序ID在你的工程中不与其他方块冲突
    }

    @Override
    public void initElements() {
        elements.blocks.add(() -> new BlockCustom().setRegistryName("roadmark_text"));
        elements.items.add(() -> new ItemBlock(block).setRegistryName(block.getRegistryName()));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerModels(ModelRegistryEvent event) {
        ModelLoader.setCustomModelResourceLocation(Item.getItemFromBlock(block), 0,
                new ModelResourceLocation("nebulaecraft:roadmark_text", "inventory"));
    }

    public static final class BlockCustom extends Block {
        public static final PropertyDirection FACING = BlockHorizontal.FACING;

        public BlockCustom() {
            super(Material.CLOTH);
            setUnlocalizedName("roadmark_text");
            setSoundType(SoundType.CLOTH);
            setHardness(1F);
            setResistance(10F);
            setLightLevel(0F);
            setLightOpacity(0);
            setCreativeTab(TabNebulaecraftRoad.tab);
            this.setDefaultState(this.blockState.getBaseState().withProperty(FACING, EnumFacing.NORTH));
        }

        // 将方块设置为透明类型
        @SideOnly(Side.CLIENT)
        @Override
        public BlockRenderLayer getBlockLayer() {
            return BlockRenderLayer.TRANSLUCENT;
        }

        @Override
        public boolean isFullCube(IBlockState state) { return false; }

        @Override
        public boolean isOpaqueCube(IBlockState state) { return false; }

        // 设置碰撞箱，允许直接走过去
        @Override
        @javax.annotation.Nullable
        public AxisAlignedBB getCollisionBoundingBox(IBlockState blockState, IBlockAccess worldIn, BlockPos pos) {
            return NULL_AABB;
        }

        // 设置物理边框（玩家准星瞄准的框）。为了贴合 1.25x1.75，我们根据朝向设定稍大的边框
        @Override
        public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
            switch (state.getValue(BlockHorizontal.FACING)) {
                case SOUTH:
                case NORTH:
                    return new AxisAlignedBB(-0.1, 0, -0.25, 1.125, 0.05, 1.); // 宽度 1.25，高度 1.75
                case EAST:
                case WEST:
                default:
                    return new AxisAlignedBB(-0.25, 0, -0.1, 1.25, 0.05, 1.1);
            }
        }

        // --- 核心交互逻辑：右键打开 GUI ---
        @Override
        public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
            // 注意这里：改成了 worldIn.isRemote (客户端执行)
            if (worldIn.isRemote) {
                TileEntity te = worldIn.getTileEntity(pos);
                if (te instanceof TileEntityRoadmarkText) {
                    // 绕过原版的服务端容器限制，直接打开纯客户端输入界面
                    net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
                            new net.kuina.nebulaecraft.gui.GuiRoadmarkText((TileEntityRoadmarkText) te)
                    );
                }
            }
            return true;
        }

        // --- TileEntity 绑定逻辑 ---
        @Override
        public boolean hasTileEntity(IBlockState state) {
            return true; // 告诉游戏这个方块有附加数据
        }

        @Override
        public TileEntity createTileEntity(World world, IBlockState state) {
            return new TileEntityRoadmarkText(); // 绑定刚刚创建的实体
        }

        // --- 朝向相关逻辑 ---
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
            return this.getDefaultState().withProperty(FACING, EnumFacing.getFront(meta + 2));
        }

        @Override
        public int getMetaFromState(IBlockState state) {
            return (state.getValue(FACING).getIndex() - 2);
        }

        @Override
        public IBlockState getStateForPlacement(World worldIn, BlockPos pos, EnumFacing facing, float hitX, float hitY, float hitZ, int meta, EntityLivingBase placer) {
            return this.getDefaultState().withProperty(FACING, placer.getHorizontalFacing().getOpposite());
        }
    }
}