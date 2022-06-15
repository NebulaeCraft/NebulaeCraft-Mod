package net.kuina.nebulaecraft.entities;

import net.minecraft.entity.item.EntityMinecartEmpty;
import net.minecraft.world.World;

public class EntityCameraCart extends EntityMinecartEmpty {
    public EntityCameraCart(World worldIn)
    {
        super(worldIn);
    }

    public EntityCameraCart(World worldIn, double x, double y, double z)
    {
        super(worldIn, x, y, z);
    }

    @Override
    public double getMountedYOffset()
    {
        return 0.9D;
    }

}


