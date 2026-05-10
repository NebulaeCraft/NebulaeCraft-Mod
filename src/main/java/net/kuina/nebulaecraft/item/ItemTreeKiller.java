package net.kuina.nebulaecraft.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;

public class ItemTreeKiller extends Item {

    public ItemTreeKiller() {
        // 设置物品的内部名称
        this.setUnlocalizedName("tree_killer");
        // 设置创造模式的标签页（你可以替换成你模组专属的 Tab，比如 TabNebulaecraftRoad.tab）
        this.setCreativeTab(CreativeTabs.TOOLS);
        // 限制物品最大堆叠数为 1
        this.setMaxStackSize(1);
    }

    /**
     * 当玩家右键使用此物品时触发的方法
     */
    @Override
    public ActionResult<ItemStack> onItemRightClick(World worldIn, EntityPlayer playerIn, EnumHand handIn) {
        // 指令通常需要在服务端（Server）进行处理
        if (!worldIn.isRemote) {
            // 你指定的 WorldEdit 替换指令
            String command = "//replacenear 30 17,18,30,31,32,37,38,39,40,48,59,81,83,86,99,100,106,111,161,162,175,1124,1125,1127 0";

            // 获取服务器的指令管理器，并以当前玩家的身份执行该指令
            worldIn.getMinecraftServer().getCommandManager().executeCommand(playerIn, command);

            // 给这个物品添加 20 ticks（即 1 秒）的冷却时间，防止连点导致服务器卡顿或崩溃
            playerIn.getCooldownTracker().setCooldown(this, 20);
        }

        // 播放玩家挥动手臂的动画
        playerIn.swingArm(handIn);

        // 返回成功结果
        return new ActionResult<ItemStack>(EnumActionResult.SUCCESS, playerIn.getHeldItem(handIn));
    }
}