
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
import net.minecraft.entity.item.EntityMinecartEmpty;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
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

    public static class EntityCameraCart extends EntityMinecartEmpty {
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
    }

    public static class ItemCameraCart extends Item
    {
        private static final IBehaviorDispenseItem MINECART_DISPENSER_BEHAVIOR = new BehaviorDefaultDispenseItem()
        {
            private final BehaviorDefaultDispenseItem behaviourDefaultDispenseItem = new BehaviorDefaultDispenseItem();
            public ItemStack dispenseStack(IBlockSource source, ItemStack stack)
            {
                EnumFacing enumfacing = (EnumFacing)source.getBlockState().getValue(BlockDispenser.FACING);
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

                    worldIn.spawnEntity(entityCameraCart);
                }

                itemstack.shrink(1);
                return EnumActionResult.SUCCESS;
            }
        }
    }
}
