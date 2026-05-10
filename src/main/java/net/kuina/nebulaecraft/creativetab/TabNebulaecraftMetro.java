
package net.kuina.nebulaecraft.creativetab;

import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.fml.relauncher.Side;

import net.minecraft.item.ItemStack;
import net.minecraft.creativetab.CreativeTabs;

import net.kuina.nebulaecraft.block.BlockThirdrailYellowSupport;
import net.kuina.nebulaecraft.ElementsNebulaecraftMod;

import net.minecraft.util.NonNullList;
import java.util.Comparator;
import net.kuina.nebulaecraft.NebulaecraftMod;

@ElementsNebulaecraftMod.ModElement.Tag
public class TabNebulaecraftMetro extends ElementsNebulaecraftMod.ModElement {
	public TabNebulaecraftMetro(ElementsNebulaecraftMod instance) {
		super(instance, 2);
	}

	@Override
	public void initElements() {
		tab = new CreativeTabs("tabnebulaecraft_metro") {
			@SideOnly(Side.CLIENT)
			@Override
			public ItemStack getTabIconItem() {
				return new ItemStack(BlockThirdrailYellowSupport.block, 1);
			}

			@SideOnly(Side.CLIENT)
			public boolean hasSearchBar() {
				return false;
			}

			@SideOnly(Side.CLIENT)
			@Override
			public void displayAllRelevantItems(NonNullList<ItemStack> items) {
				// 1. 先让原版把所有东西都老老实实放进来
				super.displayAllRelevantItems(items);

				// 2. 启用“自然排序（智能识别数字）”进行排序
				items.sort(new Comparator<ItemStack>() {
					@Override
					public int compare(ItemStack s1, ItemStack s2) {
						String name1 = s1.getItem().getRegistryName() != null ? s1.getItem().getRegistryName().toString() : "";
						String name2 = s2.getItem().getRegistryName() != null ? s2.getItem().getRegistryName().toString() : "";

						// 使用正则表达式，将字符串在“字母”和“数字”的交界处切开
						// 例如 "roadsign_4" 会变成 ["nebulaecraft:roadsign_", "4"]
						String[] parts1 = name1.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
						String[] parts2 = name2.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");

						int minLength = Math.min(parts1.length, parts2.length);
						for (int i = 0; i < minLength; i++) {
							String p1 = parts1[i];
							String p2 = parts2[i];

							// 如果切出来的两段都是数字，就把它们当成真正的整数来比较！
							if (p1.matches("\\d+") && p2.matches("\\d+")) {
								int num1 = Integer.parseInt(p1);
								int num2 = Integer.parseInt(p2);
								if (num1 != num2) {
									return Integer.compare(num1, num2); // 数字 4 就会小于数字 39 了
								}
							} else {
								// 如果是字母部分（比如 asphalt 和 roadsign），就按老规矩普通字母比较
								int cmp = p1.compareTo(p2);
								if (cmp != 0) {
									return cmp;
								}
							}
						}
						// 如果前面都一模一样，谁的段数短谁排前面
						return Integer.compare(parts1.length, parts2.length);
					}
				});
			}
		};
	}
	public static CreativeTabs tab;
}
