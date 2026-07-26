package net.kuina.nebulaecraft.autogen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TunnelConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile;
    private static Data data = defaults();

    private TunnelConfig() {
    }

    public static synchronized void initialize(File file) {
        configFile = file;
        reload();
    }

    public static synchronized boolean reload() {
        if (configFile == null) {
            return false;
        }
        try {
            File parent = configFile.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) {
                return false;
            }
            if (!configFile.exists()) {
                try (FileWriter writer = new FileWriter(configFile)) {
                    GSON.toJson(defaults(), writer);
                }
            }
            Data loaded;
            try (FileReader reader = new FileReader(configFile)) {
                loaded = GSON.fromJson(reader, Data.class);
            }
            boolean migrated = migrate(loaded);
            validate(loaded);
            data = loaded;
            if (migrated) {
                try (FileWriter writer = new FileWriter(configFile)) {
                    GSON.toJson(loaded, writer);
                }
            }
            return true;
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static Data get() {
        return data;
    }

    public static Preset getPreset(String id) {
        return data.presets.get(id);
    }

    private static void validate(Data value) {
        if (value == null || value.settings == null || value.presets == null || value.presets.isEmpty()) {
            throw new IllegalArgumentException("Tunnel configuration is missing settings or presets");
        }
        if (value.settings.blocksPerTick < 1 || value.settings.maxChangedBlocks < 1
                || value.settings.maxPathLength < 2 || value.settings.lightSpacing < 1
                || value.settings.catenarySupportSpacing < 1
                || value.settings.thirdRailSupportSpacing < 1) {
            throw new IllegalArgumentException("Tunnel configuration limits must be positive");
        }
        for (Map.Entry<String, Preset> entry : value.presets.entrySet()) {
            Preset preset = entry.getValue();
            if (preset == null || preset.clearWidth != 5 || preset.clearHeight != 4
                    || preset.catenaryRecessHeight != 1) {
                throw new IllegalArgumentException("Preset " + entry.getKey()
                        + " must use the supported 5x4 cross-section with a 1x1 catenary recess");
            }
            if (preset.minimumRadius <= 0 || preset.maximumGrade <= 0) {
                throw new IllegalArgumentException("Preset " + entry.getKey() + " has invalid geometry limits");
            }
        }
    }

    private static boolean migrate(Data value) {
        if (value == null || value.presets == null) {
            return false;
        }
        boolean changed = value.schemaVersion < 5;
        if (value.schemaVersion < 4 && value.settings != null) {
            if (value.settings.lightSpacing == 8) {
                value.settings.lightSpacing = 9;
            }
            if (value.settings.catenarySupportSpacing == 8) {
                value.settings.catenarySupportSpacing = 9;
            }
        }
        if (value.schemaVersion < 5 && value.settings != null
                && value.settings.thirdRailSupportSpacing < 1) {
            value.settings.thirdRailSupportSpacing = 3;
        }
        for (Preset preset : value.presets.values()) {
            if (preset != null && preset.clearWidth == 6 && preset.clearHeight == 5) {
                preset.clearWidth = 5;
                preset.clearHeight = 4;
                preset.catenaryRecessHeight = 1;
                changed = true;
            }
            if (preset != null && "nebulaecraft:reinforced_concrete_slab@0".equals(preset.horizontalConcreteSlab)) {
                preset.horizontalConcreteSlab = "nebulaecraft:reinforced_concrete_slab@1";
                changed = true;
            }
        }
        if (changed) {
            value.schemaVersion = 5;
        }
        return changed;
    }

    private static Data defaults() {
        Data result = new Data();
        result.schemaVersion = 5;
        result.settings = new Settings();
        result.presets = new LinkedHashMap<>();
        result.presets.put("metro_6x5", new Preset());
        return result;
    }

    public static final class Data {
        public int schemaVersion;
        public Settings settings;
        public Map<String, Preset> presets;
    }

    public static final class Settings {
        public int previewSeconds = 60;
        public int maxPathLength = 2048;
        public int maxChangedBlocks = 500000;
        public int blocksPerTick = 4096;
        public int lightSpacing = 9;
        public int catenarySupportSpacing = 9;
        public int thirdRailSupportSpacing = 3;
    }

    public static final class Preset {
        public int clearWidth = 5;
        public int clearHeight = 4;
        public int catenaryRecessHeight = 1;
        public double minimumRadius = 12.0;
        public double maximumGrade = 0.0625;
        public String fullConcrete = "railcraft:reinforced_concrete@8";
        public String horizontalConcreteSlab = "nebulaecraft:reinforced_concrete_slab@1";
        public String verticalConcreteSlab = "nebulaecraft:reinforced_concrete_vertical_slab@0";
        public String centerTrackbed = "minecraft:double_stone_slab@8";
        public String sideTrackbed = "minecraft:stone_slab@0";
        public String reinforcedTrack = "railcraft:track_flex_reinforced@0";
        public String platform = "railcraft:post_metal_platform@0";
    }
}
