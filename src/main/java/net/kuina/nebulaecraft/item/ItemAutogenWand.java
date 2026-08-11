package net.kuina.nebulaecraft.item;

import net.kuina.nebulaecraft.autogen.AutogenPermissions;
import net.kuina.nebulaecraft.autogen.AutogenService;
import net.kuina.nebulaecraft.autogen.AutogenBuildException;
import net.kuina.nebulaecraft.creativetab.TabNebulaecraftMetro;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
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
            if (!AutogenPermissions.canUse(player)) {
                AutogenPermissions.sendDenied(player);
            } else if (player.isSneaking()) {
                try {
                    AutogenService.clear((EntityPlayerMP) player);
                    player.sendMessage(new TextComponentTranslation("message.nebulaecraft.autogen.selection_cleared"));
                } catch (AutogenBuildException e) {
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(e.getMessage()));
                }
            } else {
                AutogenService.openGui((EntityPlayerMP) player);
            }
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, player.getHeldItem(hand));
    }
}
