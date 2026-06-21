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

        // 设置物理边框（玩家准星瞄准的黑色框）。根据渲染贴图的实际尺寸（以方块为中心，
        // 宽 0.6*2=1.2、长 1.3*2=2.6）贴合，仅用于准星高亮，不影响通行。
        @Override
        public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
            // 与渲染保持一致：单字符宽 14px，2 个及以上字符（空格也算）宽 24px。
            TileEntity te = source.getTileEntity(pos);
            int len = 0;
            if (te instanceof TileEntityRoadmarkText) {
                String t = ((TileEntityRoadmarkText) te).getText();
                if (t != null) len = t.length();
            }
            double hw = (len >= 2) ? 0.75 : 0.4375; // 半宽：24/16 或 14/16
            switch (state.getValue(BlockHorizontal.FACING)) {
                case SOUTH:
                case NORTH:
                    return new AxisAlignedBB(0.5 - hw, 0, -0.4375, 0.5 + hw, 0.05, 1.4375); // 长 30px
                case EAST:
                case WEST:
                default:
                    return new AxisAlignedBB(-0.4375, 0, 0.5 - hw, 1.4375, 0.05, 0.5 + hw); // 旋转 90°，宽长互换
            }
        }

        // --- 核心交互逻辑：右键打开 GUI ---
        @Override
        public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state, EntityPlayer playerIn, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ) {
            if (worldIn.isRemote) {
                TileEntity te = worldIn.getTileEntity(pos);
                // 交给代理处理，这样服务端加载这个类时就不会触发客户端类缺失的崩溃
                NebulaecraftMod.proxy.openRoadmarkGui(te);
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