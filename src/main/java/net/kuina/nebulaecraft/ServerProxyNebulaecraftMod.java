package net.kuina.nebulaecraft;

import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

public class ServerProxyNebulaecraftMod implements IProxyNebulaecraftMod {
	@Override
	public void preInit(FMLPreInitializationEvent event) {
	}

	@Override
	public void init(FMLInitializationEvent event) {
	}

	@Override
	public void postInit(FMLPostInitializationEvent event) {
	}

	@Override
	public void serverLoad(FMLServerStartingEvent event) {
	}

	@Override
	public void openRoadmarkGui(net.minecraft.tileentity.TileEntity te) {
		// 服务端留空，安全隔离
	}

	@Override
	public void handleTunnelPreview(net.kuina.nebulaecraft.network.PacketTunnelPreview message) {
		// 仅由客户端处理
	}

	@Override
	public void handleAutogenGui(net.kuina.nebulaecraft.network.PacketAutogenGui message) {
		// 仅由客户端处理
	}
}
