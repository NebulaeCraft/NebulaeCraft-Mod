package net.kuina.nebulaecraft.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public class ItemHasVarientsAndSubtypes extends ItemBlock
{
    private String[] subtypeNames;

    public ItemHasVarientsAndSubtypes(Block block)
    {
        super(block);
        this.setMaxDamage(0);
        this.setHasSubtypes(true);
    }

    @Override
    public int getMetadata(int metadata)
    {
        System.out.println("Ishirai DEBUG#Item#meta: "+metadata);
        return metadata;
    }


    public ItemHasVarientsAndSubtypes setSubtypeNames(String[] names)
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

