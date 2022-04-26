package net.kuina.nebulaecraft.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public class ItemAsphaltSlab extends ItemBlock
{
    public ItemAsphaltSlab(Block block)
    {
        super(block);
        this.setMaxDamage(0);
        this.setHasSubtypes(true);
    }

    @Override
    public int getMetadata(int metadata)
    {
//        System.out.println("Ishirai DEBUG#Item#meta: "+metadata);
        return metadata;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack)
    {
        BlockAsphaltSlab.BlockCustom.EnumColour colour = BlockAsphaltSlab.BlockCustom.EnumColour.byMetadata(stack.getMetadata());
        return super.getUnlocalizedName() + "." + colour.toString();
    }
}

