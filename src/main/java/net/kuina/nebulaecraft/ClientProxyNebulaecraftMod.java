package net.kuina.nebulaecraft;

import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.client.model.obj.OBJLoader;
import net.minecraftforge.common.MinecraftForge;

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
		MinecraftForge.EVENT_BUS.register(
				net.kuina.nebulaecraft.client.render.TunnelPreviewRenderer.INSTANCE);
	}

	@Override
	public void postInit(FMLPostInitializationEvent event) {
	}

	@Override
	public void serverLoad(FMLServerStartingEvent event) {
	}

	@Override
	public void openRoadmarkGui(net.minecraft.tileentity.TileEntity te) {
		if (te instanceof net.kuina.nebulaecraft.tileentity.TileEntityRoadmarkText) {
			net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
					new net.kuina.nebulaecraft.gui.GuiRoadmarkText((net.kuina.nebulaecraft.tileentity.TileEntityRoadmarkText) te)
			);
		}
	}

	@Override
	public void handleTunnelPreview(net.kuina.nebulaecraft.network.PacketTunnelPreview message) {
		net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(() ->
				net.kuina.nebulaecraft.client.render.TunnelPreviewRenderer.INSTANCE.handle(message));
	}
}
