package htsjdk.beta.plugin;

//TODO: This is a tagging interface currently used as a type-bound for codec/encoder/decoder
// header type params, and used to tag SAMFileHeader, VCFHeader, and SAMSequenceDictionary. But
// so far it seems to serve no purpose, and may be removed.
public interface HtsHeader {
}
