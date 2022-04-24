
package net.kuina.nebulaecraft.creativetab;

import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.fml.relauncher.Side;

import net.minecraft.item.ItemStack;
import net.minecraft.init.Blocks;
import net.minecraft.creativetab.CreativeTabs;

import net.kuina.nebulaecraft.ElementsNebulaecraftMod;

@ElementsNebulaecraftMod.ModElement.Tag
public class TabNebulaecraftRoad extends ElementsNebulaecraftMod.ModElement {
	public TabNebulaecraftRoad(ElementsNebulaecraftMod instance) {
		super(instance, 3);
	}

	@Override
	public void initElements() {
		tab = new CreativeTabs("tabnebulaecraft_road") {
			@SideOnly(Side.CLIENT)
			@Override
			public ItemStack getTabIconItem() {
				return new ItemStack(Blocks.AIR, (int) (1));
			}

			@SideOnly(Side.CLIENT)
			public boolean hasSearchBar() {
				return false;
			}
		};
	}
	public static CreativeTabs tab;
}
