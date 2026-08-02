package net.kuina.nebulaecraft;

import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

public interface IProxyNebulaecraftMod {
	void preInit(FMLPreInitializationEvent event);

	void init(FMLInitializationEvent event);

	void postInit(FMLPostInitializationEvent event);

	void serverLoad(FMLServerStartingEvent event);

	void openRoadmarkGui(net.minecraft.tileentity.TileEntity te);

	void handleTunnelPreview(net.kuina.nebulaecraft.network.PacketTunnelPreview message);
}
