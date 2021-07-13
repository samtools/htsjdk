package htsjdk.beta.plugin;

/**
 * Placeholder interface for methods for upgrading one version of a format to a newer version.
 */
public interface Upgradeable {
    boolean runVersionUpgrade(final HtsVersion sourceCodecVersion, final HtsVersion targetCodecVersion);
}
