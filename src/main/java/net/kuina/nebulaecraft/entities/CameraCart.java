
package net.kuina.nebulaecraft.entities;

import net.kuina.nebulaecraft.ElementsNebulaecraftMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.dispenser.BehaviorDefaultDispenseItem;
import net.minecraft.dispenser.IBehaviorDispenseItem;
import net.minecraft.dispenser.IBlockSource;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MoverType;
import net.minecraft.entity.item.EntityMinecartEmpty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@ElementsNebulaecraftMod.ModElement.Tag
public class CameraCart extends ElementsNebulaecraftMod.ModElement {
    @GameRegistry.ObjectHolder("nebulaecraft:cameracart")
    public static final Block block = null;
    public CameraCart(ElementsNebulaecraftMod instance) {
        super(instance, 91);
    }

    @Override
    public void initElements() {
        elements.entities.add(() -> EntityEntryBuilder.create().entity(EntityCameraCart.class).id(new ResourceLocation("nebulaecraft:cameracart"), 0).name("cameracart").tracker(64, 1, true).build());
    }

    /**
     * 自带动力的拍摄用车。动力模型对齐 Railcraft 永动机车（EntityLocomotive.applyDrag），
     * 但不引用 Railcraft 的类；全部状态只在服务端使用。
     */
    public static class EntityCameraCart extends EntityMinecartEmpty {
        public static final int ACTION_REVERSE = 0;
        public static final int ACTION_FASTER = 1;
        public static final int ACTION_SLOWER = 2;
        public static final int ACTION_MODE = 3;
        public static final int ACTION_COUNT = 4;

        private static final String NBT_RUNNING = "CameraCartRunning";
        private static final String NBT_GEAR = "CameraCartGear";
        private static final String NBT_REVERSE = "CameraCartReverse";

        private static final double DRAG = 0.9D;
        /** Railcraft 默认马力 15 × 0.01。 */
        private static final double FORCE = 0.15D;
        /** 各档位的水平合速度上限（格/tick），最高档不额外限速。 */
        private static final double[] GEAR_LIMITS = {0.1D, 0.2D, 0.3D, Double.POSITIVE_INFINITY};
        private static final int MAX_GEAR = GEAR_LIMITS.length - 1;
        private static final int MAX_REVERSE_GEAR = 2;
        private static final int DEFAULT_GEAR = 2;

        private boolean running;
        private int gear = DEFAULT_GEAR;
        private boolean reverse;

        public EntityCameraCart(World worldIn) {
            super(worldIn);
        }

        public EntityCameraCart(World worldIn, double x, double y, double z) {
            super(worldIn, x, y, z);
        }

        @Override
        public void updatePassenger(Entity entity) {
            super.updatePassenger(entity);
            if(entity instanceof EntityPlayer){
                entity.setPosition(this.posX, this.posY + 0.875D + entity.getYOffset(), this.posZ);
            }
        }

        @Override
        public boolean isPoweredCart() {
            return true;
        }

        @Override
        protected void applyDrag() {
            this.motionX *= DRAG;
            this.motionY *= 0.0D;
            this.motionZ *= DRAG;

            if (this.reverse && this.gear > MAX_REVERSE_GEAR) {
                this.gear = MAX_REVERSE_GEAR;
            }

            if (this.running) {
                double force = this.reverse ? -FORCE : FORCE;
                double yaw = this.rotationYaw * Math.PI / 180.0D;
                this.motionX += Math.cos(yaw) * force;
                this.motionZ += Math.sin(yaw) * force;
            }

            double scale = gearScale(this.motionX, this.motionZ);
            this.motionX *= scale;
            this.motionZ *= scale;
        }

        /**
         * 不套用原版载人时的 0.75 倍减速，使档位速度与机车一致；
         * 同时保证外部（如强化坡道）写入的速度不会超过当前档位。
         */
        @Override
        public void moveMinecartOnRail(BlockPos pos) {
            double max = this.getMaxSpeed();
            double mX = MathHelper.clamp(this.motionX, -max, max);
            double mZ = MathHelper.clamp(this.motionZ, -max, max);
            double scale = gearScale(mX, mZ);
            this.move(MoverType.SELF, mX * scale, 0.0D, mZ * scale);
        }

        private double gearScale(double x, double z) {
            double limit = GEAR_LIMITS[this.gear];
            double magnitude = Math.sqrt(x * x + z * z);
            return magnitude > limit ? limit / magnitude : 1.0D;
        }

        /** 没有乘客就无法操作，离车时停机，避免无人车一直行驶。 */
        @Override
        protected void removePassenger(Entity passenger) {
            super.removePassenger(passenger);
            if (!this.world.isRemote) {
                this.running = false;
            }
        }

        /** 仅由服务端在校验乘坐关系后调用。 */
        public void handleControl(int action) {
            switch (action) {
                case ACTION_REVERSE:
                    this.reverse = !this.reverse;
                    if (this.reverse && this.gear > MAX_REVERSE_GEAR) {
                        this.gear = MAX_REVERSE_GEAR;
                    }
                    break;
                case ACTION_FASTER:
                    this.gear = Math.min(this.gear + 1, this.reverse ? MAX_REVERSE_GEAR : MAX_GEAR);
                    break;
                case ACTION_SLOWER:
                    this.gear = Math.max(this.gear - 1, 0);
                    break;
                case ACTION_MODE:
                    this.running = !this.running;
                    break;
                default:
                    break;
            }
        }

        @Override
        protected void writeEntityToNBT(NBTTagCompound compound) {
            super.writeEntityToNBT(compound);
            compound.setBoolean(NBT_RUNNING, this.running);
            compound.setByte(NBT_GEAR, (byte) this.gear);
            compound.setBoolean(NBT_REVERSE, this.reverse);
        }

        @Override
        protected void readEntityFromNBT(NBTTagCompound compound) {
            super.readEntityFromNBT(compound);
            this.running = compound.getBoolean(NBT_RUNNING);
            this.reverse = compound.getBoolean(NBT_REVERSE);
            if (compound.hasKey(NBT_GEAR)) {
                this.gear = MathHelper.clamp(compound.getByte(NBT_GEAR), 0, MAX_GEAR);
            }
        }
    }

    public static class ItemCameraCart extends Item
    {
        private static final IBehaviorDispenseItem MINECART_DISPENSER_BEHAVIOR = new BehaviorDefaultDispenseItem()
        {
            private final BehaviorDefaultDispenseItem behaviourDefaultDispenseItem = new BehaviorDefaultDispenseItem();
            public ItemStack dispenseStack(IBlockSource source, ItemStack stack)
            {
                EnumFacing enumfacing = source.getBlockState().getValue(BlockDispenser.FACING);
                World world = source.getWorld();
                double d0 = source.getX() + (double)enumfacing.getFrontOffsetX() * 1.125D;
                double d1 = Math.floor(source.getY()) + (double)enumfacing.getFrontOffsetY();
                double d2 = source.getZ() + (double)enumfacing.getFrontOffsetZ() * 1.125D;
                BlockPos blockpos = source.getBlockPos().offset(enumfacing);
                IBlockState iblockstate = world.getBlockState(blockpos);
                BlockRailBase.EnumRailDirection blockrailbase$enumraildirection = iblockstate.getBlock() instanceof BlockRailBase ? ((BlockRailBase)iblockstate.getBlock()).getRailDirection(world, blockpos, iblockstate, null) : BlockRailBase.EnumRailDirection.NORTH_SOUTH;
                double d3;

                if (BlockRailBase.isRailBlock(iblockstate))
                {
                    if (blockrailbase$enumraildirection.isAscending())
                    {
                        d3 = 0.6D;
                    }
                    else
                    {
                        d3 = 0.1D;
                    }
                }
                else
                {
                    if (iblockstate.getMaterial() != Material.AIR || !BlockRailBase.isRailBlock(world.getBlockState(blockpos.down())))
                    {
                        return this.behaviourDefaultDispenseItem.dispense(source, stack);
                    }

                    IBlockState iblockstate1 = world.getBlockState(blockpos.down());
                    BlockRailBase.EnumRailDirection blockrailbase$enumraildirection1 = iblockstate1.getBlock() instanceof BlockRailBase ? ((BlockRailBase)iblockstate1.getBlock()).getRailDirection(world, blockpos.down(), iblockstate1, null) : BlockRailBase.EnumRailDirection.NORTH_SOUTH;

                    if (enumfacing != EnumFacing.DOWN && blockrailbase$enumraildirection1.isAscending())
                    {
                        d3 = -0.4D;
                    }
                    else
                    {
                        d3 = -0.9D;
                    }
                }

                EntityCameraCart entityCameraCart = new EntityCameraCart(world, d0, d1 + d3, d2);

                if (stack.hasDisplayName())
                {
                    entityCameraCart.setCustomNameTag(stack.getDisplayName());
                }

                world.spawnEntity(entityCameraCart);
                stack.shrink(1);
                return stack;
            }
            protected void playDispenseSound(IBlockSource source)
            {
                source.getWorld().playEvent(1000, source.getBlockPos(), 0);
            }
        };
        private final EntityCameraCart.Type minecartType;

        public ItemCameraCart()
        {
            this.maxStackSize = 1;
            this.minecartType = EntityCameraCart.Type.RIDEABLE;
            this.setCreativeTab(CreativeTabs.TRANSPORTATION);
            BlockDispenser.DISPENSE_BEHAVIOR_REGISTRY.putObject(this, MINECART_DISPENSER_BEHAVIOR);
        }

        public EnumActionResult onItemUse(EntityPlayer player, World worldIn, BlockPos pos, EnumHand hand, EnumFacing facing, float hitX, float hitY, float hitZ)
        {
            IBlockState iblockstate = worldIn.getBlockState(pos);

            if (!BlockRailBase.isRailBlock(iblockstate))
            {
                return EnumActionResult.FAIL;
            }
            else
            {
                ItemStack itemstack = player.getHeldItem(hand);

                if (!worldIn.isRemote)
                {
                    BlockRailBase.EnumRailDirection blockrailbase$enumraildirection = iblockstate.getBlock() instanceof BlockRailBase ? ((BlockRailBase)iblockstate.getBlock()).getRailDirection(worldIn, pos, iblockstate, null) : BlockRailBase.EnumRailDirection.NORTH_SOUTH;
                    double d0 = 0.0D;

                    if (blockrailbase$enumraildirection.isAscending())
                    {
                        d0 = 0.5D;
                    }

                    EntityCameraCart entityCameraCart = new EntityCameraCart(worldIn, (double)pos.getX() + 0.5D, (double)pos.getY() + 0.0625D + d0, (double)pos.getZ() + 0.5D);

                    if (itemstack.hasDisplayName())
                    {
                        entityCameraCart.setCustomNameTag(itemstack.getDisplayName());
                    }

                    // 推力沿 rotationYaw 施加：让“前进”等于放置时玩家面对的方向。
                    float yaw = player.getHorizontalFacing().getHorizontalAngle() + 90.0F;
                    entityCameraCart.rotationYaw = yaw;
                    entityCameraCart.prevRotationYaw = yaw;

                    worldIn.spawnEntity(entityCameraCart);
                }

                itemstack.shrink(1);
                return EnumActionResult.SUCCESS;
            }
        }
    }
}
