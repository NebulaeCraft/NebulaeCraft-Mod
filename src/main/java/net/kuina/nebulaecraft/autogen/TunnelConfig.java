package net.kuina.nebulaecraft.autogen;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TunnelConfig {
    private static final Data DATA = builtIns();

    private TunnelConfig() {
    }

    public static Data get() {
        return DATA;
    }

    public static Preset getPreset(String id) {
        return DATA.presets.get(id);
    }

    private static Data builtIns() {
        Settings settings = new Settings(
                60,
                2048,
                500000,
                4096,
                9,
                9,
                3);

        Map<String, Preset> presets = new LinkedHashMap<>();
        presets.put("tunnel_metro_1", new Preset(
                5,
                4,
                1,
                12.0,
                0.1,
                "railcraft:reinforced_concrete@8",
                "nebulaecraft:reinforced_concrete_slab@1",
                "nebulaecraft:reinforced_concrete_vertical_slab@0",
                "minecraft:double_stone_slab@8",
                "minecraft:stone_slab@0",
                "railcraft:track_flex_reinforced@0",
                "railcraft:post_metal_platform@0"));
        return new Data(settings, presets);
    }

    public static final class Data {
        public final Settings settings;
        public final Map<String, Preset> presets;

        private Data(Settings settings, Map<String, Preset> presets) {
            this.settings = settings;
            this.presets = Collections.unmodifiableMap(new LinkedHashMap<>(presets));
        }
    }

    public static final class Settings {
        public final int previewSeconds;
        public final int maxPathLength;
        public final int maxChangedBlocks;
        public final int blocksPerTick;
        public final int lightSpacing;
        public final int catenarySupportSpacing;
        public final int thirdRailSupportSpacing;

        private Settings(int previewSeconds, int maxPathLength, int maxChangedBlocks, int blocksPerTick,
                         int lightSpacing, int catenarySupportSpacing, int thirdRailSupportSpacing) {
            this.previewSeconds = previewSeconds;
            this.maxPathLength = maxPathLength;
            this.maxChangedBlocks = maxChangedBlocks;
            this.blocksPerTick = blocksPerTick;
            this.lightSpacing = lightSpacing;
            this.catenarySupportSpacing = catenarySupportSpacing;
            this.thirdRailSupportSpacing = thirdRailSupportSpacing;
        }
    }

    public static final class Preset {
        public final int clearWidth;
        public final int clearHeight;
        public final int catenaryRecessHeight;
        public final double minimumRadius;
        public final double maximumGrade;
        public final String fullConcrete;
        public final String horizontalConcreteSlab;
        public final String verticalConcreteSlab;
        public final String centerTrackbed;
        public final String sideTrackbed;
        public final String reinforcedTrack;
        public final String platform;

        private Preset(int clearWidth, int clearHeight, int catenaryRecessHeight,
                       double minimumRadius, double maximumGrade, String fullConcrete,
                       String horizontalConcreteSlab, String verticalConcreteSlab,
                       String centerTrackbed, String sideTrackbed, String reinforcedTrack,
                       String platform) {
            this.clearWidth = clearWidth;
            this.clearHeight = clearHeight;
            this.catenaryRecessHeight = catenaryRecessHeight;
            this.minimumRadius = minimumRadius;
            this.maximumGrade = maximumGrade;
            this.fullConcrete = fullConcrete;
            this.horizontalConcreteSlab = horizontalConcreteSlab;
            this.verticalConcreteSlab = verticalConcreteSlab;
            this.centerTrackbed = centerTrackbed;
            this.sideTrackbed = sideTrackbed;
            this.reinforcedTrack = reinforcedTrack;
            this.platform = platform;
        }
    }
}
