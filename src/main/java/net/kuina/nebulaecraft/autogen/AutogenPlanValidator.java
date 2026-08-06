package net.kuina.nebulaecraft.autogen;

import net.minecraft.world.WorldServer;

/** Enforces template-independent safety limits before a plan can be previewed or executed. */
public final class AutogenPlanValidator {
    private AutogenPlanValidator() {
    }

    public static void validate(WorldServer world, AutogenPlan plan)
            throws AutogenBuildException {
        if (plan == null) {
            throw new AutogenBuildException("模板没有返回生成计划");
        }
        if (plan.templateId == null || plan.templateId.isEmpty()
                || plan.displayName == null || plan.displayName.isEmpty()
                || plan.kind == null) {
            throw new AutogenBuildException("模板返回了不完整的生成计划信息");
        }
        if (plan.dimension != world.provider.getDimension()) {
            throw new AutogenBuildException("生成计划与玩家所在维度不一致");
        }
        if (plan.length < 0.0 || Double.isNaN(plan.length)
                || Double.isInfinite(plan.length)
                || plan.maximumGrade < 0.0 || Double.isNaN(plan.maximumGrade)
                || Double.isInfinite(plan.maximumGrade)) {
            throw new AutogenBuildException("模板返回了无效的路线统计");
        }
        AutogenConfig.Settings settings = AutogenConfig.get();
        if (plan.length > settings.maxPathLength) {
            throw new AutogenBuildException(
                    "路线长度超过配置上限 " + settings.maxPathLength);
        }
        if (plan.operations.size() > settings.maxChangedBlocks) {
            throw new AutogenBuildException(
                    "预计修改方块数超过配置上限 " + settings.maxChangedBlocks);
        }
        for (AutogenPlan.Operation operation : plan.operations) {
            if (operation == null || operation.pos == null || operation.state == null) {
                throw new AutogenBuildException("模板返回了无效的方块操作");
            }
        }
    }
}
