package net.kuina.nebulaecraft.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public class ItemHasVariantsAndSubtypes extends ItemBlock
{
    private String[] subtypeNames;

    public ItemHasVariantsAndSubtypes(Block block)
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


    public ItemHasVariantsAndSubtypes setSubtypeNames(String[] names)
    {
        this.subtypeNames = names;
        return this;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack)
    {
        if (this.subtypeNames == null)
        {
            return super.getUnlocalizedName(stack);
        }
        else
        {
            int i = stack.getMetadata();
            return i >= 0 && i < this.subtypeNames.length ? super.getUnlocalizedName(stack) + "." + this.subtypeNames[i] : super.getUnlocalizedName(stack);
        }
    }


}

