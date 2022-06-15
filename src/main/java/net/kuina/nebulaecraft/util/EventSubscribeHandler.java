package net.kuina.nebulaecraft.util;

import net.kuina.nebulaecraft.entities.EntityCameraCart;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;
import org.lwjgl.Sys;

import javax.annotation.Nonnull;

@Mod.EventBusSubscriber(modid = "nebulaecraft")
public final class EventSubscribeHandler {

    private static int entityId = 0;

    @SubscribeEvent
    public static void onRegisterEntitiesEvent(@Nonnull final RegistryEvent.Register<EntityEntry> event) {

        final ResourceLocation exampleEntity1RegistryName = new ResourceLocation("nebulaecraft", "cameracart");

        // Comment out the code from here all the way down to "LOGGER.debug(" if you don't have entities
        event.getRegistry().registerAll(
                EntityEntryBuilder.create()
                        .entity(EntityCameraCart.class)
                        .id(exampleEntity1RegistryName, entityId++)
                        .name("cameracart")
                        .tracker(60, 5, true)
                        .build()

        );

        System.out.println("Registered entities");
//        LOGGER.debug("Registered entities");

    }
}
