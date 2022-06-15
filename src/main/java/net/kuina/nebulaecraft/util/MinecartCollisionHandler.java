package net.kuina.nebulaecraft.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.common.IMinecartCollisionHandler;

public final class MinecartCollisionHandler implements IMinecartCollisionHandler {

    @Override
    public void onEntityCollision(EntityMinecart cart, Entity other) {
        System.out.println("Ishirai#DEBUG#onEntityCollision "+cart+"with"+other);
    }

    @Override
    public AxisAlignedBB getCollisionBox(EntityMinecart cart, Entity other) {
        System.out.println("Ishirai#DEBUG#getCollisionBox "+cart+"with"+other);
        return new AxisAlignedBB(2.0D,2.0D,2.0D,2.0D,2.0D,2.0D);
    }

    @Override
    public AxisAlignedBB getMinecartCollisionBox(EntityMinecart cart) {
//        return null;
        System.out.println("Ishirai#DEBUG#getMinecartCollisionBox "+cart);
        return new AxisAlignedBB(2.0D,2.0D,2.0D,2.0D,2.0D,2.0D);
    }

    @Override
    public AxisAlignedBB getBoundingBox(EntityMinecart cart) {
//        return null;
        System.out.println("Ishirai#DEBUG#getBoundingBox "+cart);
        return new AxisAlignedBB(2.0D,2.0D,2.0D,2.0D,2.0D,2.0D);
    }
}
