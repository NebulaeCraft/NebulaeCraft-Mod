package net.kuina.nebulaecraft.autogen;

/**
 * Base checked exception for every automatic structure template.
 *
 * <p>Template implementations should use this type (or a more specific subtype) for validation
 * failures that can be shown directly to the command sender.</p>
 */
public class AutogenBuildException extends Exception {
    public AutogenBuildException(String message) {
        super(message);
    }
}
