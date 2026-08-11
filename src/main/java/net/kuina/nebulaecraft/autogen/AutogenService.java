package net.kuina.nebulaecraft.autogen;

import net.kuina.nebulaecraft.NebulaecraftMod;
import net.kuina.nebulaecraft.autogen.template.AutogenTemplate;
import net.kuina.nebulaecraft.autogen.template.AutogenTemplateRegistry;
import net.kuina.nebulaecraft.network.PacketAutogenGui;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.Locale;

/** Server-authoritative entry point shared by the wand UI and the legacy command. */
public final class AutogenService {
    private AutogenService() {
    }

    public static void openGui(EntityPlayerMP player) {
        if (!AutogenPermissions.canUse(player)) {
            AutogenPermissions.sendDenied(player);
            return;
        }
        NebulaecraftMod.PACKET_HANDLER.sendTo(PacketAutogenGui.create(player), player);
    }

    public static String preview(EntityPlayerMP player, String templateId, String[] arguments)
            throws AutogenBuildException {
        requirePermission(player);
        AutogenTemplate template = AutogenTemplateRegistry.get(templateId);
        if (template == null) {
            throw new AutogenBuildException("未知自动生成模板: " + templateId);
        }
        AutogenPlan plan = template.build(
                player.getServerWorld(), AutogenSelection.get(player), arguments);
        AutogenPlanValidator.validate(player.getServerWorld(), plan);
        TunnelGenerationManager.INSTANCE.setPreview(player, plan);
        String radius = Double.isInfinite(plan.minimumRadius)
                ? "∞" : String.format(Locale.ROOT, "%.2f", plan.minimumRadius);
        return String.format(Locale.ROOT,
                "%s 预览完成：长度 %.1f，修改 %d 方块，最小半径 %s，最大坡度 %.2f%%；请检查描边后用魔杖界面确认",
                plan.displayName, plan.length, plan.operations.size(), radius,
                plan.maximumGrade * 100.0);
    }

    public static String confirm(EntityPlayerMP player) throws AutogenBuildException {
        requirePermission(player);
        return TunnelGenerationManager.INSTANCE.confirm(player);
    }

    public static String cancel(EntityPlayerMP player) throws AutogenBuildException {
        requirePermission(player);
        return TunnelGenerationManager.INSTANCE.cancel(player);
    }

    public static String undo(EntityPlayerMP player) throws AutogenBuildException {
        requirePermission(player);
        return TunnelGenerationManager.INSTANCE.undo(player);
    }

    public static String status(EntityPlayerMP player) throws AutogenBuildException {
        requirePermission(player);
        return TunnelGenerationManager.INSTANCE.status(player);
    }

    public static String clear(EntityPlayerMP player) throws AutogenBuildException {
        requirePermission(player);
        AutogenSelection.clear(player);
        return "自动生成标记选区已清除";
    }

    private static void requirePermission(EntityPlayerMP player) throws AutogenBuildException {
        if (!AutogenPermissions.canUse(player)) {
            throw new AutogenBuildException("没有权限：需要 " + AutogenPermissions.USE);
        }
    }
}
