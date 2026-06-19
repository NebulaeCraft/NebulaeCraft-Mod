
package net.kuina.nebulaecraft.creativetab;

import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.fml.relauncher.Side;

import net.minecraft.item.ItemStack;
import net.minecraft.creativetab.CreativeTabs;

import net.kuina.nebulaecraft.block.BlockAsphalt;
import net.kuina.nebulaecraft.ElementsNebulaecraftMod;

import net.minecraft.util.NonNullList;
import java.util.Comparator;

@ElementsNebulaecraftMod.ModElement.Tag
public class TabNebulaecraftRoad extends ElementsNebulaecraftMod.ModElement {
	private static final int[] WARNING_ROADSIGNS = range(39, 62, 65, 66, 91, 97);
	private static final int[] GUIDE_ROADSIGNS = {63, 64};
	private static final int[] DIRECTION_ROADSIGNS = range(75, 81, 94, 95, 96, 67, 68, 69, 70, 71, 72, 90, 73, 74, 89, 93, 87, 82, 83, 84);
	private static final int[] PROHIBITION_ROADSIGNS = range(8, 33, 85, 86, 34, 35, 36, 37, 88, 38, 1, 2, 3, 4, 5, 6, 7, 98, 92);

	public TabNebulaecraftRoad(ElementsNebulaecraftMod instance) {
		super(instance, 3);
	}

	@Override
	public void initElements() {
		tab = new CreativeTabs("tabnebulaecraft_road") {
			@SideOnly(Side.CLIENT)
			@Override
			public ItemStack getTabIconItem() {
				return new ItemStack(BlockAsphalt.block, 1);
			}

			@SideOnly(Side.CLIENT)
			public boolean hasSearchBar() {
				return false;
			}

			@SideOnly(Side.CLIENT)
			@Override
			public void displayAllRelevantItems(NonNullList<ItemStack> items) {
				super.displayAllRelevantItems(items);

				items.sort(new Comparator<ItemStack>() {
					@Override
					public int compare(ItemStack s1, ItemStack s2) {
						String name1 = s1.getItem().getRegistryName() != null ? s1.getItem().getRegistryName().toString() : "";
						String name2 = s2.getItem().getRegistryName() != null ? s2.getItem().getRegistryName().toString() : "";

						int roadsignNumber1 = getRoadsignNumber(name1);
						int roadsignNumber2 = getRoadsignNumber(name2);
						if (roadsignNumber1 >= 0 && roadsignNumber2 >= 0) {
							int roadsignOrder1 = getRoadsignOrder(roadsignNumber1);
							int roadsignOrder2 = getRoadsignOrder(roadsignNumber2);
							if (roadsignOrder1 != roadsignOrder2) {
								return Integer.compare(roadsignOrder1, roadsignOrder2);
							}
						}

						int cmp = compareNaturally(name1, name2);
						if (cmp != 0) {
							return cmp;
						}
						return Integer.compare(s1.getMetadata(), s2.getMetadata());
					}
				});
			}
		};
	}

	private static int getRoadsignOrder(int roadsignNumber) {
		int index = indexOf(WARNING_ROADSIGNS, roadsignNumber);
		if (index >= 0) {
			return index;
		}

		index = indexOf(GUIDE_ROADSIGNS, roadsignNumber);
		if (index >= 0) {
			return 100 + index;
		}

		index = indexOf(DIRECTION_ROADSIGNS, roadsignNumber);
		if (index >= 0) {
			return 200 + index;
		}

		index = indexOf(PROHIBITION_ROADSIGNS, roadsignNumber);
		if (index >= 0) {
			return 300 + index;
		}

		return 400 + roadsignNumber;
	}

	private static int getRoadsignNumber(String registryName) {
		String prefix = "nebulaecraft:roadsign_";
		if (!registryName.startsWith(prefix)) {
			return -1;
		}

		try {
			return Integer.parseInt(registryName.substring(prefix.length()));
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	private static int compareNaturally(String name1, String name2) {
		String[] parts1 = name1.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");
		String[] parts2 = name2.split("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)");

		int minLength = Math.min(parts1.length, parts2.length);
		for (int i = 0; i < minLength; i++) {
			String p1 = parts1[i];
			String p2 = parts2[i];

			if (p1.matches("\\d+") && p2.matches("\\d+")) {
				int num1 = Integer.parseInt(p1);
				int num2 = Integer.parseInt(p2);
				if (num1 != num2) {
					return Integer.compare(num1, num2);
				}
			} else {
				int cmp = p1.compareTo(p2);
				if (cmp != 0) {
					return cmp;
				}
			}
		}

		return Integer.compare(parts1.length, parts2.length);
	}

	private static int indexOf(int[] values, int target) {
		for (int i = 0; i < values.length; i++) {
			if (values[i] == target) {
				return i;
			}
		}
		return -1;
	}

	private static int[] range(int start, int end, int... extraValues) {
		int[] values = new int[end - start + 1 + extraValues.length];
		int index = 0;
		for (int value = start; value <= end; value++) {
			values[index++] = value;
		}
		for (int value : extraValues) {
			values[index++] = value;
		}
		return values;
	}

	public static CreativeTabs tab;
}
