package net.kuina.nebulaecraft;

import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.client.model.obj.OBJLoader;

public class ClientProxyNebulaecraftMod implements IProxyNebulaecraftMod {
	@Override
	public void init(FMLInitializationEvent event) {
	}

	@Override
	public void preInit(FMLPreInitializationEvent event) {
		OBJLoader.INSTANCE.addDomain("nebulaecraft");
		net.minecraftforge.fml.client.registry.ClientRegistry.bindTileEntitySpecialRenderer(
				net.kuina.nebulaecraft.tileentity.TileEntityRoadmarkText.class,
				new net.kuina.nebulaecraft.client.render.RenderRoadmarkText()
		);
	}

	@Override
	public void postInit(FMLPostInitializationEvent event) {
	}

	@Override
	public void serverLoad(FMLServerStartingEvent event) {
	}
}
