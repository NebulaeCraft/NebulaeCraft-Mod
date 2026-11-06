package net.kuina.nebulaecraft.autogen;

/** Global execution limits shared by tunnel, bridge, and future structure templates. */
public final class AutogenConfig {
    private static final Settings SETTINGS = new Settings(
            60,
            2048,
            500000,
            8,
            4096,
            2,
            9,
            9);

    private AutogenConfig() {
    }

    public static Settings get() {
        return SETTINGS;
    }

    public static final class Settings {
        public final int previewSeconds;
        public final int maxPathLength;
        public final int maxChangedBlocks;
        /** Visible changes per tick; at 20 TPS the default places 160 blocks per second. */
        public final int blocksPerTick;
        /** Work cap for undo, unchanged states, and internal rail shape corrections. */
        public final int operationsPerTick;
        public final int placementSoundIntervalTicks;
        public final int lightSpacing;
        public final int catenarySupportSpacing;

        private Settings(int previewSeconds, int maxPathLength, int maxChangedBlocks,
                         int blocksPerTick, int operationsPerTick, int placementSoundIntervalTicks,
                         int lightSpacing, int catenarySupportSpacing) {
            this.previewSeconds = previewSeconds;
            this.maxPathLength = maxPathLength;
            this.maxChangedBlocks = maxChangedBlocks;
            this.blocksPerTick = blocksPerTick;
            this.operationsPerTick = operationsPerTick;
            this.placementSoundIntervalTicks = placementSoundIntervalTicks;
            this.lightSpacing = lightSpacing;
            this.catenarySupportSpacing = catenarySupportSpacing;
        }
    }
}
