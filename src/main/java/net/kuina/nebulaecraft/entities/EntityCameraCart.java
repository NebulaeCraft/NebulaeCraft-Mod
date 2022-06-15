package net.kuina.nebulaecraft.entities;

import net.kuina.nebulaecraft.util.MinecartCollisionHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecartEmpty;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;

import javax.annotation.Nullable;

public class EntityCameraCart extends EntityMinecartEmpty {

    private static final net.minecraftforge.common.IMinecartCollisionHandler collisionHandler = new MinecartCollisionHandler();

    public EntityCameraCart(World worldIn)
    {
        super(worldIn);
        setCollisionHandler(collisionHandler);
    }

    public EntityCameraCart(World worldIn, double x, double y, double z)
    {
        super(worldIn, x, y, z);
        setCollisionHandler(collisionHandler);
    }

    @Override
    public double getMountedYOffset()
    {
        return 0.875D;
    }


}


