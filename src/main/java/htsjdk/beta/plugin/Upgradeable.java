package htsjdk.beta.plugin;

public interface Upgradeable {
    boolean runVersionUpgrade(final HtsVersion sourceCodecVersion, final HtsVersion targetCodecVersion);
}
