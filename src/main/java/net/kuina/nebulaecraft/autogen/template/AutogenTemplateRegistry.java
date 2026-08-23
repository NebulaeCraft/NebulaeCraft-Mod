package net.kuina.nebulaecraft.autogen.template;

import net.kuina.nebulaecraft.autogen.template.bridge.MetroBridge1Template;
import net.kuina.nebulaecraft.autogen.template.bridge.MetroBridge2Template;
import net.kuina.nebulaecraft.autogen.template.tunnel.MetroTunnel1Template;
import net.kuina.nebulaecraft.autogen.template.tunnel.MetroTunnel2Template;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/** Runtime registry for built-in and third-party automatic structure templates. */
public final class AutogenTemplateRegistry {
    private static final Map<String, AutogenTemplate> TEMPLATES = new LinkedHashMap<>();
    private static boolean builtInsRegistered;

    private AutogenTemplateRegistry() {
    }

    public static synchronized void registerBuiltIns() {
        if (builtInsRegistered) {
            return;
        }
        register(new MetroTunnel1Template());
        register(new MetroTunnel2Template());
        register(new MetroBridge1Template());
        register(new MetroBridge2Template());
        builtInsRegistered = true;
    }

    /** Register during pre-initialization, before server commands become available. */
    public static synchronized void register(AutogenTemplate template) {
        if (template == null) {
            throw new IllegalArgumentException("Autogen template cannot be null");
        }
        if (template.getId() == null || template.getId().isEmpty()) {
            throw new IllegalArgumentException("Autogen template id cannot be empty");
        }
        String id = normalizeId(template.getId());
        if (!id.matches("[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Invalid autogen template id: " + template.getId());
        }
        if (TEMPLATES.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate autogen template id: " + id);
        }
        TEMPLATES.put(id, template);
    }

    public static synchronized AutogenTemplate get(String id) {
        return id == null ? null : TEMPLATES.get(normalizeId(id));
    }

    public static synchronized Set<String> getIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(TEMPLATES.keySet()));
    }

    public static synchronized List<AutogenTemplate> getAll() {
        return Collections.unmodifiableList(new ArrayList<>(TEMPLATES.values()));
    }

    private static String normalizeId(String id) {
        return id.toLowerCase(Locale.ENGLISH);
    }
}
