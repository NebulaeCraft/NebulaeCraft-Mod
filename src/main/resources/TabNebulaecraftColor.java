
package net.kuina.nebulaecraft.creativetab;

import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.fml.relauncher.Side;

import net.minecraft.item.ItemStack;
import net.minecraft.creativetab.CreativeTabs;

import net.kuina.nebulaecraft.block.BlockColor1;
import net.kuina.nebulaecraft.ElementsNebulaecraftMod;

import net.minecraft.util.NonNullList;
import java.util.Comparator;
import net.kuina.nebulaecraft.NebulaecraftMod;

@ElementsNebulaecraftMod.ModElement.Tag
public class TabNebulaecraftColor extends ElementsNebulaecraftMod.ModElement {
	public TabNebulaecraftColor(ElementsNebulaecraftMod instance) {
		super(instance, 4);
	}

	@Override
	public void initElements() {
		tab = new CreativeTabs("tabnebulaecraft_color") {
			@SideOnly(Side.CLIENT)
			@Override
			public ItemStack getTabIconItem() {
				return new ItemStack(BlockColor1.block, (int) (1));
			}

			@SideOnly(Side.CLIENT)
			public boolean hasSearchBar() {
				return true;
			}

			@SideOnly(Side.CLIENT)
			@Override
			public void displayAllRelevantItems(NonNullList<ItemStack> items) {
				// 1. 先让原版把所有东西都老老实实放进来
				super.displayAllRelevantItems(items);

				// 2. 强制按注册 ID (Registry Name) 的字母顺序进行排序！
				items.sort(new Comparator<ItemStack>() {
					@Override
					public int compare(ItemStack s1, ItemStack s2) {
						// 获取第一个物品的注册 ID（例如：nebulaecraft:asphalt）
						String name1 = s1.getItem().getRegistryName() != null ? s1.getItem().getRegistryName().toString() : "";
						// 获取第二个物品的注册 ID
						String name2 = s2.getItem().getRegistryName() != null ? s2.getItem().getRegistryName().toString() : "";

						// 让 Java 自动根据这两个字符串的字母顺序进行比较
						return name1.compareTo(name2);
					}
				});
			}
		}.setBackgroundImageName("item_search.png");
	}
	public static CreativeTabs tab;
}
