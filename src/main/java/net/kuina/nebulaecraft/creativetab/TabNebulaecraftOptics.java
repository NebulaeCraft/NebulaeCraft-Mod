
package net.kuina.nebulaecraft.creativetab;

import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.fml.relauncher.Side;

import net.minecraft.item.ItemStack;
import net.minecraft.creativetab.CreativeTabs;

import net.kuina.nebulaecraft.block.BlockLamp;
import net.kuina.nebulaecraft.ElementsNebulaecraftMod;

@ElementsNebulaecraftMod.ModElement.Tag
public class TabNebulaecraftOptics extends ElementsNebulaecraftMod.ModElement {
	public TabNebulaecraftOptics(ElementsNebulaecraftMod instance) {
		super(instance, 1);
	}

	@Override
	public void initElements() {
		tab = new CreativeTabs("tabnebulaecraft_optics") {
			@SideOnly(Side.CLIENT)
			@Override
			public ItemStack getTabIconItem() {
				return new ItemStack(BlockLamp.block, (int) (1));
			}

			@SideOnly(Side.CLIENT)
			public boolean hasSearchBar() {
				return false;
			}
		};
	}
	public static CreativeTabs tab;
}
