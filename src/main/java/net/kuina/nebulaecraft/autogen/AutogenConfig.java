package net.kuina.nebulaecraft.autogen;

/** Global execution limits shared by tunnel, bridge, and future structure templates. */
public final class AutogenConfig {
    private static final Settings SETTINGS = new Settings(
            60,
            2048,
            500000,
            4096,
            9,
            9,
            3);

    private AutogenConfig() {
    }

    public static Settings get() {
        return SETTINGS;
    }

    public static final class Settings {
        public final int previewSeconds;
        public final int maxPathLength;
        public final int maxChangedBlocks;
        public final int blocksPerTick;
        public final int lightSpacing;
        public final int catenarySupportSpacing;
        public final int thirdRailSupportSpacing;

        private Settings(int previewSeconds, int maxPathLength, int maxChangedBlocks,
                         int blocksPerTick, int lightSpacing, int catenarySupportSpacing,
                         int thirdRailSupportSpacing) {
            this.previewSeconds = previewSeconds;
            this.maxPathLength = maxPathLength;
            this.maxChangedBlocks = maxChangedBlocks;
            this.blocksPerTick = blocksPerTick;
            this.lightSpacing = lightSpacing;
            this.catenarySupportSpacing = catenarySupportSpacing;
            this.thirdRailSupportSpacing = thirdRailSupportSpacing;
        }
    }
}
