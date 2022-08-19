
package net.kuina.nebulaecraft.entities;

import net.kuina.nebulaecraft.ElementsNebulaecraftMod;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityMinecartEmpty;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import net.minecraftforge.fml.common.registry.GameRegistry;

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
        public double getMountedYOffset() {
            return 0.875D;
        }
    }
}
