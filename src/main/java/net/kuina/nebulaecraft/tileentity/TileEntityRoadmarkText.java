package net.kuina.nebulaecraft.tileentity;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

public class TileEntityRoadmarkText extends TileEntity {

    // 保存方块显示的文字，默认值为 "字"
    private String text = "字";

    // 获取当前文字
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
        if (this.world != null && this.world.isRemote) {
            this.needsUpdate = true; // 客户端收到新文字，标记需要刷新贴图
        }
        this.markDirty();
    }

    // 1. 将数据写入 NBT 以保存到存档硬盘中
    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setString("RoadmarkText", this.text);
        return compound;
    }

    // 2. 从存档硬盘中读取 NBT 数据
    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (compound.hasKey("RoadmarkText")) {
            this.text = compound.getString("RoadmarkText");
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
        String oldText = this.text; // 记录旧文字
        this.readFromNBT(pkt.getNbtCompound()); // 读取新文字

        // 【新增】如果是客户端，且文字发生了变化，标记重新生成贴图
        if (this.world != null && this.world.isRemote && !this.text.equals(oldText)) {
            this.needsUpdate = true;
        }
    }

    // 追加在类内部
    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public net.minecraft.client.renderer.texture.DynamicTexture texture;

    @net.minecraftforge.fml.relauncher.SideOnly(net.minecraftforge.fml.relauncher.Side.CLIENT)
    public boolean needsUpdate = true;

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
}