package net.kuina.nebulaecraft.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

public class TileEntityRoadmarkText extends TileEntity {

    public static final int COLOR_WHITE = 0xF9F9F9;
    public static final int COLOR_YELLOW = 0xFCD667;

    // 保存方块显示的文字，默认值为 "字"
    private String text = "字";
    private int color = COLOR_WHITE;

    // 获取当前文字
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
        this.markDirty();
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = normalizeColor(color);
        this.markDirty();
    }

    // 1. 将数据写入 NBT 以保存到存档硬盘中
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setString("RoadmarkText", this.text);
        compound.setInteger("RoadmarkColor", this.color);
        return compound;
    }

    // 2. 从存档硬盘中读取 NBT 数据
    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("RoadmarkText")) {
            this.text = compound.getString("RoadmarkText");
        }
        if (compound.hasKey("RoadmarkColor")) {
            this.color = normalizeColor(compound.getInteger("RoadmarkColor"));
        }
    }

    // --- 下面三个方法用于确保客户端和服务端的数据同步（让玩家能实时看到文字变化） ---

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(this.pos, 3, this.getUpdateTag());
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return this.writeToNBT(new NBTTagCompound());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        this.readFromNBT(pkt.getNbtCompound()); // 读取新文字
    }

    // 追加在类内部
    /* @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public net.minecraft.client.renderer.texture.DynamicTexture texture;

    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public boolean needsUpdate = true; */

    // --- 突破原版渲染限制的代码 ---

    // 1. 突破距离限制：只要所在的区块被加载（比如客户端设置视距为 16 个区块即 256 格），就能看到它
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    @Override
    public double getMaxRenderDistanceSquared() {
        // 65536.0D 代表 256 格的平方。你也可以设置为 Double.MAX_VALUE 让它永远不因距离而消失
        return 50176.0D;
    }

    // 2. 突破视锥剔除限制：防止因为你画的文字比原版 1x1 方块大，导致你的视角刚移开方块中心，文字就闪烁消失
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    @Override
    public net.minecraft.util.math.AxisAlignedBB getRenderBoundingBox() {
        // 告诉游戏这个方块的渲染范围是“无限大”，让游戏引擎把渲染决定权完全交给我们
        return net.minecraft.tileentity.TileEntity.INFINITE_EXTENT_AABB;
    }

    private static int normalizeColor(int color) {
        return color == COLOR_YELLOW ? COLOR_YELLOW : COLOR_WHITE;
    }
}
