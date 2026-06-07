
package net.kuina.nebulaecraft.block;

import net.kuina.nebulaecraft.ElementsNebulaecraftMod;
import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.util.BlockRenderLayer;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@ElementsNebulaecraftMod.ModElement.Tag
public class BlockSolid extends ElementsNebulaecraftMod.ModElement {

	public BlockSolid(ElementsNebulaecraftMod instance) {
		super(instance, 0);
	}

	public static final class BlockCustom extends Block {
		public BlockCustom() {
            super(Material.ROCK);
			setSoundType(SoundType.METAL);
            setHardness(1F);
			setResistance(10F);
			setLightLevel(0F);
			setLightOpacity(255);
		}

		@SideOnly(Side.CLIENT)
		@Override
		public BlockRenderLayer getBlockLayer() {
			return BlockRenderLayer.SOLID;
		}
	}
}
