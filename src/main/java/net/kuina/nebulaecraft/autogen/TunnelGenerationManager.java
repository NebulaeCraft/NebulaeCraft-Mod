package net.kuina.nebulaecraft.autogen;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketParticles;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TunnelGenerationManager {
    public static final TunnelGenerationManager INSTANCE = new TunnelGenerationManager();
    private final Map<UUID, Preview> previews = new HashMap<>();
    private ActiveJob activeJob;

    private TunnelGenerationManager() {
    }

    public void setPreview(EntityPlayerMP player, TunnelPlan plan) {
        long expiresAt = System.currentTimeMillis() + TunnelConfig.get().settings.previewSeconds * 1000L;
        previews.put(player.getUniqueID(), new Preview(plan, expiresAt));
        sendParticles(player, plan.route);
    }

    public String confirm(EntityPlayerMP player) {
        if (activeJob != null) {
            return "已有隧道生成或撤销任务正在运行";
        }
        Preview preview = previews.get(player.getUniqueID());
        if (preview == null || preview.expiresAt < System.currentTimeMillis()) {
            previews.remove(player.getUniqueID());
            return "没有有效预览，请重新执行 preview";
        }
        WorldServer world = player.getServer().getWorld(preview.plan.dimension);
        if (world == null) {
            return "预览所在维度当前不可用";
        }
        TunnelUndoData undo = TunnelUndoData.get(player.getServer());
        undo.begin(preview.plan.dimension);
        activeJob = ActiveJob.build(player.getUniqueID(), world, preview.plan, undo);
        previews.remove(player.getUniqueID());
        return "隧道生成已开始，共 " + preview.plan.operations.size() + " 个方块";
    }

    public String cancel(EntityPlayerMP player) {
        if (activeJob == null) {
            Preview removed = previews.remove(player.getUniqueID());
            return removed == null ? "没有可取消的预览或任务" : "预览已取消";
        }
        if (activeJob.undoing) {
            return "撤销任务不能再次取消";
        }
        activeJob = ActiveJob.undo(player.getUniqueID(), activeJob.world, activeJob.undoData);
        return "生成已停止，正在恢复已经修改的方块";
    }

    public String undo(EntityPlayerMP player) {
        if (activeJob != null) {
            return "已有隧道生成或撤销任务正在运行";
        }
        TunnelUndoData data = TunnelUndoData.get(player.getServer());
        if (!data.hasEntries()) {
            return "没有可撤销的隧道生成记录";
        }
        WorldServer world = player.getServer().getWorld(data.getDimension());
        if (world == null) {
            return "撤销记录所在维度当前不可用";
        }
        activeJob = ActiveJob.undo(player.getUniqueID(), world, data);
        return data.isIncomplete() ? "正在恢复上次未完成的生成任务" : "隧道撤销已开始";
    }

    public String status(EntityPlayerMP player) {
        if (activeJob != null) {
            return (activeJob.undoing ? "撤销" : "生成") + "进度: " + activeJob.index + "/" + activeJob.total();
        }
        Preview preview = previews.get(player.getUniqueID());
        if (preview != null && preview.expiresAt >= System.currentTimeMillis()) {
            return "预览等待确认，预计修改 " + preview.plan.operations.size() + " 个方块";
        }
        TunnelUndoData data = TunnelUndoData.get(player.getServer());
        return data.hasEntries() ? "空闲；最近一次生成可撤销（" + data.size() + " 个方块）" : "空闲";
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || activeJob == null) {
            return;
        }
        int budget = TunnelConfig.get().settings.blocksPerTick;
        while (budget-- > 0 && activeJob.index < activeJob.total()) {
            if (activeJob.undoing) {
                activeJob.undoEntries.get(activeJob.index).restore(activeJob.world);
            } else {
                TunnelPlan.Operation operation = activeJob.plan.operations.get(activeJob.index);
                if (operation.captureUndo) {
                    activeJob.undoData.capture(activeJob.world, operation.pos);
                }
                activeJob.world.setBlockState(operation.pos, operation.state, 2);
            }
            activeJob.index++;
        }
        if (activeJob.index >= activeJob.total()) {
            finishActiveJob();
        }
    }

    private void finishActiveJob() {
        ActiveJob finished = activeJob;
        activeJob = null;
        if (finished.undoing) {
            finished.undoData.clear();
            message(finished.world.getMinecraftServer(), finished.owner, "隧道撤销完成");
        } else {
            finished.undoData.finish();
            EntityPlayerMP owner = finished.world.getMinecraftServer().getPlayerList().getPlayerByUUID(finished.owner);
            if (owner != null) {
                AutogenSelection.clear(owner);
                owner.sendMessage(new TextComponentString("隧道生成完成，共修改 " + finished.index + " 个方块"));
            }
        }
    }

    private static void sendParticles(EntityPlayerMP player, List<BlockPos> route) {
        int stride = Math.max(1, route.size() / 384);
        for (int i = 0; i < route.size(); i += stride) {
            BlockPos pos = route.get(i);
            player.connection.sendPacket(new SPacketParticles(EnumParticleTypes.REDSTONE, true,
                    pos.getX() + 0.5F, pos.getY() + 1.25F, pos.getZ() + 0.5F,
                    0, 0, 0, 0, 1));
            if (i % (stride * 4) == 0) {
                player.connection.sendPacket(new SPacketParticles(EnumParticleTypes.END_ROD, true,
                        pos.getX() + 0.5F, pos.getY() + 6.25F, pos.getZ() + 0.5F,
                        3.0F, 0, 3.0F, 0, 2));
            }
        }
    }

    private static void message(MinecraftServer server, UUID playerId, String text) {
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
        if (player != null) {
            player.sendMessage(new TextComponentString(text));
        }
    }

    private static final class Preview {
        final TunnelPlan plan;
        final long expiresAt;

        Preview(TunnelPlan plan, long expiresAt) {
            this.plan = plan;
            this.expiresAt = expiresAt;
        }
    }

    private static final class ActiveJob {
        final UUID owner;
        final WorldServer world;
        final TunnelPlan plan;
        final TunnelUndoData undoData;
        final List<TunnelUndoData.Entry> undoEntries;
        final boolean undoing;
        int index;

        private ActiveJob(UUID owner, WorldServer world, TunnelPlan plan, TunnelUndoData undoData,
                          List<TunnelUndoData.Entry> undoEntries, boolean undoing) {
            this.owner = owner;
            this.world = world;
            this.plan = plan;
            this.undoData = undoData;
            this.undoEntries = undoEntries;
            this.undoing = undoing;
        }

        static ActiveJob build(UUID owner, WorldServer world, TunnelPlan plan, TunnelUndoData undoData) {
            return new ActiveJob(owner, world, plan, undoData, null, false);
        }

        static ActiveJob undo(UUID owner, WorldServer world, TunnelUndoData undoData) {
            return new ActiveJob(owner, world, null, undoData, undoData.reversedEntries(), true);
        }

        int total() {
            return undoing ? undoEntries.size() : plan.operations.size();
        }
    }
}
