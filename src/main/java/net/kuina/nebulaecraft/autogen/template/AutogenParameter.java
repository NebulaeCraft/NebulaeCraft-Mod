package net.kuina.nebulaecraft.autogen.template;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Structured template parameter metadata shared by the command adapter and the wand UI. */
public final class AutogenParameter {
    public enum Type {
        CHOICE,
        BOOLEAN
    }

    private final String id;
    private final String labelKey;
    private final Type type;
    private final String defaultValue;
    private final List<String> choices;
    private final String trueArgument;

    private AutogenParameter(String id, String labelKey, Type type, String defaultValue,
                             List<String> choices, String trueArgument) {
        if (id == null || !id.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid autogen parameter id: " + id);
        }
        if (labelKey == null || labelKey.isEmpty()) {
            throw new IllegalArgumentException("Autogen parameter label cannot be empty");
        }
        this.id = id;
        this.labelKey = labelKey;
        this.type = type;
        this.defaultValue = defaultValue;
        this.choices = Collections.unmodifiableList(new ArrayList<>(choices));
        this.trueArgument = trueArgument;
    }

    public static AutogenParameter choice(String id, String labelKey, String defaultValue,
                                           List<String> choices) {
        if (choices == null || choices.isEmpty() || !choices.contains(defaultValue)) {
            throw new IllegalArgumentException("Choice parameter needs a valid default value: " + id);
        }
        return new AutogenParameter(id, labelKey, Type.CHOICE, defaultValue,
                choices, "");
    }

    public static AutogenParameter flag(String id, String labelKey, String trueArgument) {
        if (trueArgument == null || trueArgument.isEmpty()) {
            throw new IllegalArgumentException("Boolean parameter needs a command argument: " + id);
        }
        return new AutogenParameter(id, labelKey, Type.BOOLEAN, "false",
                Collections.<String>emptyList(), trueArgument);
    }

    public String getId() {
        return id;
    }

    public String getLabelKey() {
        return labelKey;
    }

    public Type getType() {
        return type;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public List<String> getChoices() {
        return choices;
    }

    public String getTrueArgument() {
        return trueArgument;
    }
}
