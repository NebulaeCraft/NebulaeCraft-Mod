package net.kuina.nebulaecraft.procedure;

import net.minecraft.world.GameType;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.client.Minecraft;

import net.kuina.nebulaecraft.ElementsNebulaecraftMod;

import java.util.Map;

@ElementsNebulaecraftMod.ModElement.Tag
public class ProcedureModeSwitchingEvent extends ElementsNebulaecraftMod.ModElement {
	public ProcedureModeSwitchingEvent(ElementsNebulaecraftMod instance) {
		super(instance, 147);
	}

	public static void executeProcedure(Map<String, Object> dependencies) {
		if (dependencies.get("entity") == null) {
			System.err.println("Failed to load dependency entity for procedure ModeSwitchingEvent!");
			return;
		}
		Entity entity = (Entity) dependencies.get("entity");
		if(entity instanceof EntityPlayer){
			EntityPlayer player=(EntityPlayer)entity;
			if(player.isCreative()){
				Minecraft.getMinecraft().player.sendChatMessage("/gamemode spectator");
			} else if (player.isSpectator()) {
				Minecraft.getMinecraft().player.sendChatMessage("/gamemode creative");
			}
		}
	}
}
