package net.kuina.nebulaecraft.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public class ItemLamp extends ItemBlock
{
    public ItemLamp(Block block)
    {
        super(block);
        this.setMaxDamage(0);
        this.setHasSubtypes(true);
    }

    @Override
    public int getMetadata(int metadata)
    {
        return metadata;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack)
    {
        BlockLamp.BlockCustom.EnumColour colour = BlockLamp.BlockCustom.EnumColour.byMetadata(stack.getMetadata());
        return super.getUnlocalizedName() + "." + colour.toString();
    }
}

