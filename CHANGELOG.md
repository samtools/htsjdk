# CHANGELOG

This changelog covers htsjdk releases from 3.0.0 onwards.  For earlier releases, see
the [GitHub Releases](https://github.com/samtools/htsjdk/releases) page.

Prior to 3.0.0, htsjdk followed a 2.x versioning scheme for several years with incremental
improvements to SAM/BAM/CRAM/VCF support, all targeting Java 8.  The 3.0.0 release introduced
the first major API breaking changes (notably converting `Allele` to an interface) and added
early infrastructure for a plugin-based codec framework and resource bundles.

---

## 5.0.0

Major release.  

### Headlines

- **CRAM 3.1 write support** (the culmination of the read-side codec work in 4.2.0 and the reader wiring in 4.3.0 — htsjdk can now produce CRAM 3.1 files that are interoperable with samtools/htslib).
- **CRAM 3.1 is now the default write version** (previously 3.0).  On the same input, files written with the new default `NORMAL` profile are roughly 36% smaller and encode 18-20% faster than what htsjdk 4.3 produced with its `FAST` (3.0) default.
- **Major speed-ups across the BAM and CRAM read/write paths** vs htsjdk 4.3.0.  Measured on AWS m8gd / m8id (single thread, 32.7M-read input), the headline wins are: BAM write 50-58% faster, CRAM encode (FAST) 41-47% faster, CRAM read 42-46% faster, BAM read 30-31% faster.
- **`jlibdeflate` is now the default DEFLATE engine** ([jlibdeflate](https://github.com/fulcrumgenomics/jlibdeflate) wrapping native libdeflate); falls back to the JDK `Deflater`/`Inflater` if the native library cannot be loaded.
- **Slimmed-down runtime dependency tree** (SRA support removed, Nashorn moved to an opt-in dependency, several stale or misleading dependency declarations cleaned up).
- **Enforced automatic code formatting** via Palantir Java Format on every build.
- **Unit test improvements**: pass/fail stats now reported correctly when run via Gradle, and total suite runtime massively reduced (now 2-3 minutes).

### ⚠️ Breaking changes

Consumers should review these before upgrading.

- **SRA support removed.**  All `htsjdk.samtools.sra.*` types, `SRAFileReader`, `SRAIterator`,
  `SRAIndex`, `SamInputResource.of(SRAAccession)`, `SamReader.Type.SRA_TYPE`, and the
  `InputResource.Type.SRA_ACCESSION` enum value have been deleted.  The
  `gov.nih.nlm.ncbi:ngs-java` dependency (and the `samjdk.sra_libraries_download` system
  property) are gone.  Consumers needing SRA access must use NCBI's tooling or a different
  library (#1774).
- **Nashorn is no longer a transitive runtime dependency.**  The `JavascriptSamRecordFilter`
  and `JavascriptVariantFilter` classes still exist but htsjdk no longer ships
  `org.openjdk.nashorn:nashorn-core` (or its 5 ASM transitives) on consumers' runtime
  classpath.  Consumers who use the JavaScript filter classes must add
  `org.openjdk.nashorn:nashorn-core:15.7` (or another JSR-223 `"js"` engine) to their own
  runtime classpath; the no-engine error message names the artifact and prints both Gradle
  and Maven coordinates (#1775).
- **`SAMRecord.toString()` now returns the full SAM-format string** for the record (all 11
  mandatory SAM fields plus tags), replacing the previous minimal summary.  The previous
  output was usually insufficient to debug failures in `println()` calls or test-assertion
  messages; the new output is the same line you would see in a SAM file.  Anything that
  parses or asserts against the exact old format will need updating (#1762).
- **CRAM slice headers no longer include the optional content digest tags** (BD/SD/B5/S5/B1/S1).
  Matches htslib/samtools behavior.  Block-level CRC32 (required since CRAM 3.0) still
  provides data integrity.  Technically a wire-format change but with zero known practical
  impact, since no known tools consume these tags.
- **Default CRAM version for writing is now 3.1** (was 3.0).  CRAM 3.0 readers will not be
  able to read newly-produced files; pass an explicit version to the writer if you need 3.0
  output.

### CRAM 3.1 Write Support

- Enable CRAM 3.1 writing with all spec codecs: rANS Nx16, adaptive arithmetic Range coder, FQZComp, Name Tokenisation, and STRIPE
- Add configurable compression profiles (FAST, NORMAL, SMALL, ARCHIVE) with trial compression for automatic codec selection
- Implement `TrialCompressor` to replace ad-hoc triple-compression for tags and align trial candidates with htslib
- Add `GzipCodec` for direct Deflater/Inflater GZIP compression, wired into CRAM as a codec option
- Strip NM/MD tags on CRAM encode and regenerate on decode, matching htslib behavior
- Implement attached (same-slice) mate pair resolution
- Align DataSeries content IDs with htslib for cross-implementation debugging
- Remove content digest tags (BD/SD/B5/S5/B1/S1) from CRAM slice headers, matching htslib/samtools behavior (see Breaking changes)
- Default CRAM version for writing is now 3.1 (was 3.0; see Breaking changes)
- Add `CramConverter` command-line tool for testing and benchmarking CRAM write profiles
- Add cross-implementation CRAM validation pipeline (`validation/`) for round-tripping against samtools/htslib
- Add bases-per-slice threshold to bound slice memory when writing long reads
- Refine `CompressionHeader` map serialization
- Resolve a pile of in-tree `TODO`s in CRAM structure classes

### CRAM correctness and cross-implementation fixes

These fixes apply to both reading and writing CRAM and substantially improve interoperability with samtools/htslib.

- Fix CRAM `TLEN` computation to match htslib (cross-tool comparisons of the same input now produce matching `TLEN` values)
- Fix `CIGAR` reconstruction when the sequence is `*` (`CF_UNKNOWN_BASES`)
- Fix `=`/`X` `CIGAR` op comparison in cross-implementation tests
- Fix CRAM archive header overflow on large containers
- Fix crash when reading a CRAM container with no slices
- Fix unmapped-read query in the hts-specs compliance harness
- Document the supplementary/secondary read-name resolution limitation in the writer

### Codec and Compression Optimizations

- Refactor and optimize all rANS codecs: byte-array API, backwards-write encoding, and general simplifications
- Optimize Name Tokeniser encoder: replace regex with hand-written parser; add per-type flags, STRIPE support, stream deduplication, and all-MATCH elimination
- Optimize FQZComp, Range coder, and rANS encoder hot paths
- Tune NORMAL profile codec assignments based on empirical compression testing

### Performance

- Integrate [jlibdeflate](https://github.com/fulcrumgenomics/jlibdeflate) for native libdeflate-backed DEFLATE compression and decompression. Used by default; falls back to the JDK Deflater/Inflater if the native library cannot be loaded (#1768)
- A few targeted optimizations to the BAM decoding path yielding ~6-7% improvement in BAM read performance (#1764)
- Replace `ByteArrayInputStream`/`ByteArrayOutputStream` with unsynchronized `CRAMByteReader`/`CRAMByteWriter` to eliminate synchronization overhead in CRAM
- Fuse read base restoration, CIGAR building, and NM/MD computation into a single pass during CRAM decode
- Cache tag key metadata to eliminate per-record `String` allocation during CRAM decode
- Pool `RANSNx16Decode` instances in the Name Tokeniser
- Optimize BAM nibble-to-ASCII base decoding with a bulk lookup table

### Bug fixes

- Fix LTF8 9-byte write bug: wrong bit shift (`>> 28` instead of `>> 24`) corrupted the high byte of large CRAM offsets (#1765)
- Fix `SamLocusIterator` so that read position is not incorrectly offset (#1758)
- Fix asymmetric `SamPairUtil.getPairOrientation` on dovetail pairs (#1771)
- Catch `UnsatisfiedLinkError` when loading the snappy native library so failure to load it does not abort downstream consumers (#1753)

### Build, tooling, and dependency clean-up

- **Code formatting:** apply [Palantir Java Format](https://github.com/palantir/palantir-java-format) to the entire codebase and enforce it on every build via [Spotless](https://github.com/diffplug/spotless).  `compileJava` auto-formats source in place; CI separately runs `spotlessCheck` as the enforcement boundary.  See `CONTRIBUTING.md` for details, including the `.git-blame-ignore-revs` opt-in for the bulk-format commit (#1761)
- **Maven Central publishing migrated** from the legacy OSSRH endpoint to the new [Sonatype Central Portal](https://central.sonatype.com), via the [NMCP Gradle plugin](https://github.com/GradleUp/nmcp).  Consumer-visible groupId/artifactId/version coordinates are unchanged (#1769)
- **Snapshot versioning** now embeds the short commit hash (e.g. `5.0.0-23c681a-SNAPSHOT`) so each snapshot is a distinct, pinnable artifact rather than a moving Maven SNAPSHOT (#1772)
- **Test runner** now correctly reports failures rather than silently skipping them when a `@DataProvider` throws (#1759)
- **Existing API deprecations** cleaned up across `htsjdk.samtools` and `htsjdk.variant` (#1767)
- **`commons-logging` direct declaration removed.**  htsjdk does not use commons-logging itself; the version pin is now expressed as a Gradle dependency constraint and only kicks in transitively when JEXL pulls it
- **Nashorn moved to `compileOnly`** — see Breaking changes
- **`gov.nih.nlm.ncbi:ngs-java` removed** — see Breaking changes (SRA support)

### Compatibility

- Compiled and tested against JDK 17 (CI default), 21, and 24.  CI continues to build only on 17.  htsjdk's published minimum remains Java 17 (set in 4.0.0)

### Testing and Infrastructure

- Add hts-specs CRAM 3.0 / 3.1 decode-compliance tests, plus FQZComp round-trip tests using hts-specs quality data
- Add CRAI index query correctness tests and codec round-trip property tests
- Split CRAM 3.1 fidelity tests into per-profile classes for parallel execution
- Speed up BCF2 and SeekableStream integration tests; cache test data in CRAM index test classes
- Reduce `CRAMFileBAIIndexTest` from 4 to 2 slice-size variants, sampling every 200th
- Downsample the CEUTrio test CRAM from ~654K to ~150K records (47 MB → 11 MB)
- Reduce memory pressure in unit tests to eliminate OOM failures
- Fix thread-safety bug in `VariantContextTestProvider` causing non-deterministic test counts
- Bulk up the JavaScript filter test suites: replace 4 checked-in `.js` fixtures with 46 small inline-script tests covering all three constructors, return-type semantics, bindings, and error paths (#1775)

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
