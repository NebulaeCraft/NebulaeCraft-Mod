package com.mojang.text2speech;

import java.util.Locale;

public interface Narrator {
    void say(String text);

    void clear();

    boolean active();

    static Narrator getNarrator() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String osArch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);

        if (osName.contains("mac") && (osArch.equals("aarch64") || osArch.equals("arm64"))) {
            return new Narrator() {
                @Override
                public void say(String text) {
                }

                @Override
                public void clear() {
                }

                @Override
                public boolean active() {
                    return false;
                }
            };
        }

        if (osName.contains("linux")) {
            setJNAPath(":");
            return new NarratorLinux();
        }
        if (osName.contains("win")) {
            setJNAPath(";");
            return new NarratorWindows();
        }
        if (osName.contains("mac")) {
            setJNAPath(":");
            return new NarratorOSX();
        }
        return new NarratorDummy();
    }

    static void setJNAPath(String separator) {
        System.setProperty("jna.library.path", System.getProperty("jna.library.path") + separator + "./src/natives/resources/");
        System.setProperty("jna.library.path", System.getProperty("jna.library.path") + separator + System.getProperty("java.library.path"));
    }
}
