package net.kuina.nebulaecraft.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public class ItemColor1 extends ItemBlock
{
    public ItemColor1(Block block)
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

    @Override
    public String getUnlocalizedName(ItemStack stack)
    {
        BlockColor1.BlockCustom.EnumColour colour = BlockColor1.BlockCustom.EnumColour.byMetadata(stack.getMetadata());
        return super.getUnlocalizedName() + "." + colour.toString();
    }
}

