package net.kuina.nebulaecraft.item;

import net.kuina.nebulaecraft.autogen.AutogenSelection;
import net.kuina.nebulaecraft.creativetab.TabNebulaecraftMetro;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;

public class ItemAutogenWand extends Item {
    public ItemAutogenWand() {
        setMaxStackSize(1);
        setCreativeTab(TabNebulaecraftMetro.tab);
    }

    @Override
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        if (!world.isRemote) {
            if (player.isSneaking()) {
                AutogenSelection.clear(player);
                player.sendMessage(new TextComponentTranslation("message.nebulaecraft.autogen.selection_cleared"));
            } else {
                player.sendMessage(new TextComponentTranslation("message.nebulaecraft.autogen.wand_help"));
            }
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }
}
