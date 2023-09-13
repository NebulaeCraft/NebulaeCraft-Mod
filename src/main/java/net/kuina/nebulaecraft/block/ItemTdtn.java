package net.kuina.nebulaecraft.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

public class ItemTdtn extends ItemBlock
{
    public ItemTdtn(Block block)
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
        BlockTdtn.BlockCustom.EnumTdtn countdown = BlockTdtn.BlockCustom.EnumTdtn.byMetadata(stack.getMetadata());
        return super.getUnlocalizedName() + "." + countdown.toString();
    }
}

