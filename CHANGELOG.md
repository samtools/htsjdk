# CHANGELOG

This changelog covers htsjdk releases from 3.0.0 onwards.  For earlier releases, see
the [GitHub Releases](https://github.com/samtools/htsjdk/releases) page.

Prior to 3.0.0, htsjdk followed a 2.x versioning scheme for several years with incremental
improvements to SAM/BAM/CRAM/VCF support, all targeting Java 8.  The 3.0.0 release introduced
the first major API breaking changes (notably converting `Allele` to an interface) and added
early infrastructure for a plugin-based codec framework and resource bundles.

---

## 5.0.0

Adds **CRAM 3.1 write support** to htsjdk.  This is the culmination of the read-side codec work
in 4.2.0 and the reader wiring in 4.3.0: htsjdk can now produce CRAM 3.1 files that are
interoperable with samtools/htslib.

### CRAM 3.1 Write Support

- Enable CRAM 3.1 writing with all spec codecs: rANS Nx16, adaptive arithmetic Range coder, FQZComp, Name Tokenisation, and STRIPE
- Add configurable compression profiles (FAST, NORMAL, SMALL, ARCHIVE) with trial compression for automatic codec selection
- Implement `TrialCompressor` to replace ad-hoc triple-compression for tags and align trial candidates with htslib
- Add `GzipCodec` for direct Deflater/Inflater GZIP compression, wired into CRAM as a codec option
- Strip NM/MD tags on CRAM encode and regenerate on decode, matching htslib behavior
- Implement attached (same-slice) mate pair resolution
- Align DataSeries content IDs with htslib for cross-implementation debugging
- Remove content digest tags (BD/SD/B5/S5/B1/S1) from CRAM slice headers, matching htslib/samtools behavior. These are optional per the spec and were expensive to compute. Block-level CRC32 (required by CRAM 3.0+) provides data integrity. This is technically a breaking change but has zero practical impact since no known tools consume these tags.
- Default CRAM version for writing is now 3.1 (was 3.0)
- Add `CramConverter` command-line tool for testing and benchmarking CRAM write profiles

### Codec and Compression Optimizations

- Refactor and optimize all rANS codecs: byte-array API, backwards-write encoding, and general simplifications
- Optimize Name Tokeniser encoder: replace regex with hand-written parser; add per-type flags, STRIPE support, stream deduplication, and all-MATCH elimination
- Optimize FQZComp, Range coder, and rANS encoder hot paths
- Tune NORMAL profile codec assignments based on empirical compression testing

### Performance

- Replace `ByteArrayInputStream`/`ByteArrayOutputStream` with unsynchronized `CRAMByteReader`/`CRAMByteWriter` to eliminate synchronization overhead
- Fuse read base restoration, CIGAR building, and NM/MD computation into a single pass during decode
- Cache tag key metadata to eliminate per-record `String` allocation during CRAM decode
- Pool `RANSNx16Decode` instances in the Name Tokeniser
- Optimize BAM nibble-to-ASCII base decoding with a bulk lookup table

### Testing and Infrastructure

- Split CRAM 3.1 fidelity tests into per-profile classes for parallel execution
- Reduce memory pressure in unit tests to eliminate OOM failures
- Fix thread-safety bug in `VariantContextTestProvider` causing non-deterministic test counts

---

## 4.3.0 (2025-05-09)

Completes CRAM 3.1 read support by wiring the codec implementations (added in 4.2.0) into
the reader pipeline.  Also adds support for reference bundles with non-co-located resources.

- Wire up CRAM 3.1 codecs for reading; htsjdk can now read CRAM 3.1 files (#1736)
- Support reference bundles where FASTA, index, and dictionary need not share a parent directory (#1713)
- Restore `ReferenceSequenceFileFactory` four-argument factory method accidentally removed in #1713 (#1743)

## 4.2.0 (2025-02-21)

Adds CRAM 3.1 codec implementations (rANS Nx16, FQZComp, Name Tokenisation, Range coder)
but does not yet wire them into the reader/writer pipeline (read support shipped in 4.3.0).

- Implementation of CRAM 3.1 codecs with samtools interop tests (#1714)
- Add `ReferenceSequenceFile.getSubsequenceAt(Locatable)` convenience overload (#1725)
- Treat `.vcf.bgz` as a valid VCF extension in `FileExtensions` (#1727)
- GFF3 improvements: `Gff3BaseData` implements `Locatable`; new `getAttr(key)` and `hasAttribute` methods (#1726)
- Fix ambiguity in `SamUtils.PairOrientation` about reads needing to map to the same contig (#1709)
- Set MAPQ on supplemental alignments in `SamPairUtil` (#1737)
- Update commons-compress to 1.26.0 (#1720)

## 4.1.3 (2024-10-07)

- `SamReaderFactory` now opens non-regular files (e.g. named pipes, FIFOs) consistently for `File` and `Path` (#1717)
- Replace `toPath().toString()` with `getURIString()` in `HtsPath` (#1719)

## 4.1.2 (2024-09-17)

- Implement VCF bundles (#1703) and bundle collections (#1702)
- Make Snappy an optional dependency (#1715)
- Reduce memory in `IntervalMergerIterator` when not concatenating names (#1711)

## 4.1.1 (2024-06-04)

**Important CRAM bug fix:** A bug introduced in htsjdk 3.0.0 caused corrupted reads in CRAM
files when a read is aligned starting at exactly position 1 on a reference contig.  This
primarily affects T2T references and mitochondrial calling.  Affects Picard 2.27.3-3.1.1 and
GATK 4.3-4.5.  GATK 4.6 includes a `CRAMIssue8768Detector` tool to scan affected files.

- Fix `CRAMReferenceRegion` updating (#1708)
- Update to Gradle 8.5, samtools 1.19.1

## 4.1.0 (2023-12-13)

Updated Tribble to prefer available HTTP/FTP `FileSystemProvider` plugins over legacy support.

**Compatibility notes:**
- Tribble now requires absolute URIs to be percent-encoded (e.g. spaces as `%20`)
- Deprecated `SeekableStreamFactory.isFilePath()`; replaced with `isBeingHandledByLegacyUrlSupport`
- URIs with missing or unparseable schemes are now rejected rather than treated as local file paths

## 4.0.2 (2023-10-13)

- Lenient (optimistic) read-only support for VCF 4.4 (#1683)
- Add `IntervalFileFeature` for common interface between BED and interval_list (#1680)
- Update some methods in `BamFileIoUtils` to accept `Path` input (#1681)
- Fix `IOUtil.unrollPaths` for HTTP paths with query parameters (#1688)
- Update snappy-java to fix vulnerability (#1687)

## 4.0.1 (2023-08-08)

- Add `Genotype.hasRefAllele()` and `Genotype.hasAltAllele()` convenience methods (#1678)
- Update out-of-date dependencies (#1677)

## 4.0.0 (2023-08-03)

**Breaking: minimum Java version raised from 8 to 17.**

- Migrate to Java 17 (#1649)
- Replace MJSON with `org.json` to address CVEs (#1670)
- Update snappy-java to address CVEs (#1670)
- Add `SINGULAR` sequencing platform to read group (#1635)
- Remove incorrect zero-length B-array checks (#1674)

## 3.0.5 (2023-02-21)

Last release supporting Java 8.

- Fix NPE in `CRAMRecordReadFeatures.restoreReadBases` (#1655)
- Add `Cigar.fromCigarString()` factory method (#1647)
- Expose ability to encode a `Genotype` into a GT field (#1648)
- Update commons-compress to close vulnerabilities (#1639)
- Remove Scala test infrastructure (#1640)
- Update Gradle to 7.6 (#1650)

## 3.0.4 (2022-11-23)

Hotfix reverting the `VariantContext` sort-order change from 3.0.3 which caused valid VCFs
to be incorrectly flagged as invalid.

## 3.0.3 (2022-11-16)

**Do not use this release.**  A bug causes many valid VCFs to be incorrectly flagged as
invalid.  Use 3.0.4 or 3.0.2 instead.

- Use allele info in `VariantContext` comparisons for stable sorts (#1593)
- Allow the BAM index to be up to 5 seconds older than the BAM before warning (#1634)

## 3.0.2 (2022-10-07)

- Minor improvements to `AbstractLocusIterator` (#1624)
- Remove deprecation from `Allele.acceptableAlleleBases` (#1625)

## 3.0.1 (2022-09-23)

**Security fix:** Fixes a vulnerability around temporary directory creation that could expose
data to malicious users on shared systems (#1621).

**Compatibility note:** `IOUtil.createTempDir()` no longer accepts a prefix containing a full
file path.  Use `Files.createTemporaryDirectory(path, prefix)` instead.

- Fix temporary directory hijacking / information disclosure (#1621)
- Fix `EdgeReadIterator` (#1616)
- Add `ULTIMA` and `ELEMENT` as valid values for `RG-PL` (#1619)

## 3.0.0 (2022-06-03)

First major version bump.  Primary breaking change: `Allele` is now an interface;
`SimpleAllele` is the concrete implementation.

**Breaking changes:**
- `Allele` converted from concrete class to interface (#1454); `ByteArrayAllele` renamed to `SimpleAllele` (#1576)
- API marker annotations moved to new annotation package (#1558)
- New plugin framework for versioned file format codecs (#1525)

**New features:**
- CRAM reference regions support (#1605)
- CSI index loading from URLs/streams (#1595)
- Htsget POST request support (#1529)
- GVCF mode for `VariantContext` type determination (#1544)
- Beta implementation of Bundles (#1546)
- Fluent chaining setters for `SAMSequenceRecord` (#1563)
- Option to disable write-order checking in `SAMFileWriter` (#1599)

**Bug fixes:**
- Fix CRAM read base feature restoration (#1590)
- Fix CRAM scores read feature decoding during normalization (#1592)
- Respect genotype filtering when calculating AC/AN/AF (#1554)

**Other:**
- Add `DNBSEQ` platform tag for BGI/MGI sequencers (#1547)
- Deprecate `OTHER` as a `PL` value (#1552)
- Update snappy library for Apple Silicon compatibility (#1580)
- Migrate CI from Travis to GitHub Actions (#1572)
