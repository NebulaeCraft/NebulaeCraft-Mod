package net.kuina.nebulaecraft.autogen;

import net.kuina.nebulaecraft.NebulaecraftMod;
import net.kuina.nebulaecraft.network.PacketTunnelPreview;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class TunnelGenerationManager {
    public static final TunnelGenerationManager INSTANCE = new TunnelGenerationManager();
    private final Map<UUID, Preview> previews = new HashMap<>();
    private ActiveJob activeJob;

    private TunnelGenerationManager() {
    }

    public void setPreview(EntityPlayerMP player, AutogenPlan plan) {
        int lifetimeMillis = AutogenConfig.get().previewSeconds * 1000;
        long expiresAt = System.currentTimeMillis() + lifetimeMillis;
        previews.put(player.getUniqueID(), new Preview(plan, expiresAt, player.getServer()));
        NebulaecraftMod.PACKET_HANDLER.sendTo(
                PacketTunnelPreview.show(plan, lifetimeMillis), player);
    }

    public String confirm(EntityPlayerMP player) {
        if (activeJob != null) {
            return "已有自动生成或撤销任务正在运行";
        }
        Preview preview = previews.get(player.getUniqueID());
        if (preview == null || preview.expiresAt < System.currentTimeMillis()) {
            previews.remove(player.getUniqueID());
            clearPreview(player);
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
        clearPreview(player);
        return preview.plan.displayName + "生成已开始，共 "
                + preview.plan.operations.size() + " 个方块";
    }

    public String cancel(EntityPlayerMP player) {
        if (activeJob == null) {
            Preview removed = previews.remove(player.getUniqueID());
            if (removed != null) {
                clearPreview(player);
            }
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
            return "已有自动生成或撤销任务正在运行";
        }
        TunnelUndoData data = TunnelUndoData.get(player.getServer());
        if (!data.hasEntries()) {
            return "没有可撤销的自动生成记录";
        }
        WorldServer world = player.getServer().getWorld(data.getDimension());
        if (world == null) {
            return "撤销记录所在维度当前不可用";
        }
        activeJob = ActiveJob.undo(player.getUniqueID(), world, data);
        return data.isIncomplete() ? "正在恢复上次未完成的生成任务" : "结构撤销已开始";
    }

    public String status(EntityPlayerMP player) {
        if (activeJob != null) {
            return (activeJob.undoing ? "撤销" : "生成") + "进度: " + activeJob.index + "/" + activeJob.total();
        }
        Preview preview = previews.get(player.getUniqueID());
        if (preview != null && preview.expiresAt >= System.currentTimeMillis()) {
            return "预览等待确认，预计修改 " + preview.plan.operations.size() + " 个方块";
        }
        if (preview != null) {
            previews.remove(player.getUniqueID());
            clearPreview(player);
        }
        TunnelUndoData data = TunnelUndoData.get(player.getServer());
        return data.hasEntries() ? "空闲；最近一次生成可撤销（" + data.size() + " 个方块）" : "空闲";
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        expirePreviews();
        if (activeJob == null) {
            return;
        }
        int budget = AutogenConfig.get().blocksPerTick;
        while (budget-- > 0 && activeJob.index < activeJob.total()) {
            if (activeJob.undoing) {
                activeJob.undoEntries.get(activeJob.index).restore(activeJob.world);
            } else {
                AutogenPlan.Operation operation = activeJob.plan.operations.get(activeJob.index);
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
            message(finished.world.getMinecraftServer(), finished.owner, "结构撤销完成");
        } else {
            finished.undoData.finish();
            EntityPlayerMP owner = finished.world.getMinecraftServer().getPlayerList().getPlayerByUUID(finished.owner);
            if (owner != null) {
                AutogenSelection.clear(owner);
                owner.sendMessage(new TextComponentString(
                        finished.plan.displayName + "生成完成，共修改 "
                                + finished.index + " 个方块"));
            }
        }
    }

    private void expirePreviews() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Preview>> iterator = previews.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Preview> entry = iterator.next();
            Preview preview = entry.getValue();
            if (preview.expiresAt >= now) {
                continue;
            }
            EntityPlayerMP player = preview.server.getPlayerList()
                    .getPlayerByUUID(entry.getKey());
            if (player != null) {
                clearPreview(player);
            }
            iterator.remove();
        }
    }

    private static void clearPreview(EntityPlayerMP player) {
        NebulaecraftMod.PACKET_HANDLER.sendTo(PacketTunnelPreview.clear(), player);
    }

    private static void message(MinecraftServer server, UUID playerId, String text) {
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
        if (player != null) {
            player.sendMessage(new TextComponentString(text));
        }
    }

    private static final class Preview {
        final AutogenPlan plan;
        final long expiresAt;
        final MinecraftServer server;

        Preview(AutogenPlan plan, long expiresAt, MinecraftServer server) {
            this.plan = plan;
            this.expiresAt = expiresAt;
            this.server = server;
        }
    }

    private static final class ActiveJob {
        final UUID owner;
        final WorldServer world;
        final AutogenPlan plan;
        final TunnelUndoData undoData;
        final List<TunnelUndoData.Entry> undoEntries;
        final boolean undoing;
        int index;

        private ActiveJob(UUID owner, WorldServer world, AutogenPlan plan, TunnelUndoData undoData,
                          List<TunnelUndoData.Entry> undoEntries, boolean undoing) {
            this.owner = owner;
            this.world = world;
            this.plan = plan;
            this.undoData = undoData;
            this.undoEntries = undoEntries;
            this.undoing = undoing;
        }

        static ActiveJob build(UUID owner, WorldServer world, AutogenPlan plan, TunnelUndoData undoData) {
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
