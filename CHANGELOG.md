# CHANGELOG

This changelog covers htsjdk releases from 3.0.0 onwards.  For earlier releases, see
the [GitHub Releases](https://github.com/samtools/htsjdk/releases) page.

Prior to 3.0.0, htsjdk followed a 2.x versioning scheme for several years with incremental
improvements to SAM/BAM/CRAM/VCF support, all targeting Java 8.  The 3.0.0 release introduced
the first major API breaking changes (notably converting `Allele` to an interface) and added
early infrastructure for a plugin-based codec framework and resource bundles.

---

## 6.0.0

Major release.  It includes every change in 5.0.2.  Before upgrading, read "Upgrading from 5.x"; to explain differences from output written by 5.x, see "Output that differs from 5.x".

### Headlines

- **`java.nio.file.Path` throughout the public API.**  Every reader, writer, index and reference works with any NIO file system provider (S3, GCS, HDFS, jimfs), and `String` and `URI` arguments are resolved by their scheme.
- **BCF 2.2, as bcftools writes it.**  htsjdk reads BCF 2.1 and 2.2, raw or BGZF-compressed, queries BGZF BCF through a CSI index, and writes BGZF BCF 2.2 with a CSI index by default.
- **VCF 3.2 through 4.5 are read by default, and 4.0 through 4.5 written**, with VCF 4.4 and 4.5's new `Number` codes, per-allele phasing, structural-variant alleles and breakends, and record ends from `SVLEN` and `LEN`.
- **CRAM is indexed with a `.crai` and queried from it directly**, so a CRAM on a reference longer than 512 Mbp is queryable.  The CRAM 3.1 profiles choose codecs as htslib does.
- **CSI indexes for BAM and tabix-indexed files**, for references longer than 512 Mbp, and **bgzipped SAM** that is written, indexed and queried like BAM.
- **Faster than 5.0.2**: converting BAM to CRAM takes 29% less time, reading VCF 9–11% less, and writing bgzipped VCF 12% less.

### Upgrading from 5.x

These changes can break code that compiles or runs against 5.x.

#### Paths and files

- **`java.io.File` APIs are replaced by `java.nio.file.Path`** (224 methods and constructors).  Pass a `Path`, or a `String` or `URI`, which `IOUtil.getPath` resolves by scheme, falling back to the local file system.  Bridge a `File` with `file.toPath()` or the null-safe `IOUtil.toPath(file)`.  HTTP(S) and FTP still go through htsjdk's `SeekableStream`s.

  ```java
  SamReader r = SamReaderFactory.makeDefault().open(Path.of("/data/x.bam"));
  SAMFileWriter w = new SAMFileWriterFactory().makeBAMWriter(header, true, Path.of("/data/out.bam"));
  ReferenceSequenceFile ref = ReferenceSequenceFileFactory.getReferenceSequenceFile(Path.of("/data/ref.fasta"));
  SamReader s3 = SamReaderFactory.makeDefault().open(URI.create("s3://bucket/x.bam")); // with an S3 provider on the class path
  ```

- **A few `File` APIs remain**: temporary files (`IOUtil.newTempFile`, `getDefaultTmpDir`, `createTempDir`), the bridges `IOUtil.toPath` and `filesToPaths`, and sixteen deprecated overloads, each with a `Path` twin, in `CRAMFileReader`, `MetricsFile`, `BlockCompressedInputStream` and `CRAIIndex`.
- `Defaults.REFERENCE_FASTA` is a `Path`; an unparseable `samjdk.reference_fasta` is logged and treated as unset.
- **`ReferenceSequenceFile.close()` no longer declares `IOException`** ([#1179]); a failure to close throws `RuntimeIOException`.  Only source is affected: delete catch blocks that only closing a reference needed, and `throws IOException` from implementations of `close()`.
- Removed: `NotPrimarySkippingIterator`, deprecated since 2.13.0 (use `SecondaryAlignmentSkippingIterator`), and `TestUtil.BASE_URL_FOR_HTTP_TESTS`.

#### SAM, BAM and CRAM

- **An indexed CRAM is written with a `.crai`, not a `.cram.bai`.**  `SamFiles.findIndex` finds either, but code that names or globs for `.cram.bai` must change.  `SAMFileWriterFactory.setCreateBaiIndexForCram(true)` writes a BAI for one more release; it is deprecated and goes in 7.0.0.
- **A reader's index is no longer a `DiskBasedBAMFileIndex`, `CSIIndex` or `AbstractBAMFileIndex`**, so casts to them fail.  Ask `SamReader.Indexing.getHtsIndex` for what you need: `getHtsIndex(BrowseableBAMIndex.class)` for bins, `getHtsIndex(BAMIndex.class)` for per-reference counts, `getHtsIndex(CRAIQueryIndex.class)` for CRAM container offsets.  `getIndex()` is deprecated; for a CRAI-indexed CRAM it returns a BAM index made from the CRAI, whose record counts are zero.  A CRAI keeps no counts, so `getUnplacedRecordCount()` is empty for a CRAM with unplaced reads; `CRAMBAIIndexer` builds an index that counts.

  ```java
  // 5.x: ((AbstractBAMFileIndex) reader.indexing().getIndex()).getNoCoordinateCount()
  OptionalLong unplaced = reader.indexing().getHtsIndex().getUnplacedRecordCount();
  ```

- The deprecated BAM index classes still work when constructed directly, but a query that finds nothing returns an empty span rather than `null`, their memory-mapping arguments do nothing, and `AbstractBAMFileIndex`'s protected methods for reading the index file are gone.
- `BAMIndexMerger` merges BAI parts only and rejects a CSI; `processIndex` reads the part's index at once, so the caller may close it.
- **A CRAM writer refuses, when it is created, a codec that its CRAM version lacks**, where 5.x wrote a file labelled 3.0 full of 3.1 codecs.  A default strategy asked for CRAM 3.0 takes the new `NORMAL_3_0` profile's codecs, keeping its other settings; SMALL, ARCHIVE and codecs set by hand are kept, and refused with a message naming the profiles that write 3.0 (`FAST`, `NORMAL_3_0`).  `setCramVersion` accepts only 3.0 and 3.1; htsjdk still reads CRAM 2.1.
- **`SAMFileWriterFactory` writes `x.sam.gz` as bgzipped SAM**, where it wrote BAM, and recognises names in any case: `x.SAM` and `x.CRAM`, which it wrote as BAM, are SAM and CRAM, and the index beside `x.BAM` is `x.bai`.
- **Text is encoded as the SAM specification says, and a value that would corrupt the file is refused** ([#1108], [#1202]).  Header text is UTF-8 in SAM, BAM and CRAM, and a header line that isn't valid UTF-8, as 5.x wrote non-ASCII ones, is read as ISO-8859-1; read names and Z and A tags are ISO-8859-1, so bytes 0x80–0xFF pass through as in htslib.  Writing throws `IllegalArgumentException` for a tab, line break or NUL a field can't hold, and for a char above 0xFF in a read name or tag.  A non-ASCII read name in a CRAM from 5.x, which stored it as UTF-8, reads back as those bytes.
- **Records the specification allows on unmapped and unpaired reads are accepted** ([#1584]): RNAME `*` with a MAPQ or CIGAR parses, and MAPQ 255 on an unmapped read is valid.  Pair and mate flags on an unpaired read, and an unmapped read's MAPQ other than 0 or 255, are now warnings rather than errors, among them the new `INVALID_UNPAIRED_FLAG_MATE_UNMAPPED` and `INVALID_UNMAPPED_MAPPING_QUALITY`: `SamFileValidator` reports them as `WARNING:`, and STRICT readers still reject them.
- **`SAMSequenceRecord` accepts names outside the SAM RNAME pattern** ([#1295]), rejecting only an empty name, spaces, control characters, `*` and `=`.  Such a name in a SAM or BAM header is the new `INVALID_SEQUENCE_NAME` error, which STRICT reading rejects; `.dict` files and VCF headers load it with a warning.  Check names with `SAMSequenceRecord.validateSequenceName` or `isValidSequenceName`.
- **A read group's `DT` is kept as written** ([#561]), where 5.x rewrote it in the JVM's time zone, so header text and `SAMUtils.calculateReadGroupRecordChecksum` change for headers 5.x rewrote.  An unparseable `DT` is the warning `INVALID_DATE_STRING`, and `getRunDate()` returns null for it.

#### VCF and BCF

- **BCF is written as BGZF BCF 2.2 with a `.csi` index**, as bcftools writes it.  `VariantContextWriterBuilder.setBCFVersion(BCFVersion.BCF_2_1)` writes raw BCF 2.1 with a Tribble `.idx`, as 5.x did; `build()` refuses a custom `IndexCreator` for BGZF BCF.
- **Records out of order with on-the-fly indexing throw `IllegalArgumentException`** ([#787]) rather than running out of memory or writing an index that misses records.  `INDEX_ON_THE_FLY` is a default option, so this applies to any VCF or BCF written to a file with a reference dictionary: sort the records, or unset the option.
- **VCF and BCF are recognised by one rule** ([#664]): `.vcf`, `.gvcf` and `.bcf`, and `.vcf` or `.gvcf` followed by `.gz`, `.gzip`, `.bgz` or `.bgzf`, in any case.  `VariantContextWriterBuilder` throws for `x.gz`, `x.txt.gz` and `x.bcf.gz`, which it wrote as BGZF VCF; call `setOutputFileType` after `setOutputPath` to write any format under any name.  Readers tell VCF from BCF by the file's contents.
- **Sample and header-line collections are unmodifiable.**  `VCFHeader`'s sample and header-line accessors return unmodifiable views, `getSampleNamesInOrder()` and `getSampleNameToOffset()` return `List` and `Map` (they were `ArrayList` and `HashMap`), and changing the result of `getSampleNames()` or `getSampleNamesOrderedByName()` on a `VariantContext` or `GenotypesContext` throws; for a record read from VCF those were the header's own collections.  Copy before changing.
- **A header keeps the first INFO, FORMAT, FILTER or structured line per ID** ([#250], [#929]), as bcftools does, and drops later ones, which lookups never saw.  It numbers its contig lines itself and keeps every distinct contig ([#1573]); a repeated contig ID whose attributes conflict throws.
- **A standard INFO or FORMAT line declared with another type is kept as declared**, where 5.x replaced it with the standard line.  `FORMAT/GQ` declared as `Float` stays `Float`, and `VCFUtils.smartMergeHeaders` refuses headers that declare a key with different types (`INFO/DB` as `Integer` in one and `Flag` in another); fix such a header first, for example with `VCFStandardHeaderLines.getInfoLine("DB")`.  The standard `PQ` line is `Integer` ([#751]).
- **A header keeps the version it declares** through copies and merges, and `smartMergeHeaders` takes the highest; `VCFHeader.validateVersionTransition` is gone.  A writer writes the version given to `setVCFVersion`, else the header's (at least 4.2), and refuses a header line that needs a later version than that.  A header that declares no version returns null from `getVCFHeaderVersion()`, and its lines no longer begin with a 4.2 `##fileformat` line.
- **A record's end is worked out as htslib works it out**: the furthest of the REF allele, `END`, `POS + SVLEN` for `<DEL>`, `<DUP>`, `<CNV>` and `<INV>`, and `LEN` for a reference block without `END`.  An `END` of `.` or before `POS` is ignored.  `getEnd()`, indexing and BCF `rlen` change for symbolic alleles with an `SVLEN` but no `END`.
- **A genotype keeps each allele's phase** where one flag is not enough (`0/1|2`, and VCF 4.4's leading `|0/1`).  Writing a first-allele phase the output version can't express throws; `VCFEncoder.writeGtField` has an overload that allows it.
- **`BCF2Type`'s integer ranges leave the sentinel values unused**, as the specification and htslib do: INT8 is −120..127, INT16 −32760..32767 and INT32 −2147483640..2147483647.  So −127..−121 are written as INT16, the seven values above `Integer.MIN_VALUE` are refused with a `TribbleException`, and an INT8 −127, INT16 −32767 or INT32 −2147483647 in a BCF from an earlier htsjdk reads as the end of its vector.
- **`BCF2Codec` reads decompressed bytes**, so it refuses a compressed stream, and a Tribble index can't be built over BGZF BCF ([#945]), where 5.x built one that found nothing; index it with CSI.
- **The `samjdk.optimistic_vcf_4_4` property and `Defaults.OPTIMISTIC_VCF_4_4` and `OPTIMISTIC_VCF_4_4_PROPERTY` are removed**, since VCF 4.4 and 4.5 are read by default.  `VCF3Codec` no longer takes part in codec discovery.
- **VCF, GFF3 and Tribble text are read and written as UTF-8**, where 5.x used Latin-1 or the platform default: in a Latin-1 file, a byte that is not valid UTF-8 reads as U+FFFD and is lost on rewrite.
- `StructuralVariantType` gains `MIXED`, which `VariantContext.getStructuralVariantType()` returns when the alleles have more than one major type or disagree with `SVTYPE`.
- `VCFCompoundHeaderLine.equals` compares `Source` and `Version`.  Header attribute values are taken as given and escaped when written, so a caller that escaped quotes itself must stop.
- For `AbstractVCFCodec` subclasses: `parseFilters` takes the line number, the scratch fields (`parts`, `genotypeParts`, `alleleMap`, `lineNo`, `filterHash`) are gone, and `stringCache` is private (use `getCachedString`).

#### Tabix and Tribble

- **`TabixIndex` is built on the new `BinningIndex`**: `TabixIndex(TabixFormat, List<String>, BinningIndex)` and `getBinningIndex()` replace the `BinningIndexContent[]` constructor and `getIndices()`, and the internal index types of `TabixReader` and `TabixUtils` are gone.  A truncated `.tbi` throws `TribbleException`.
- **A `.csi` beside a tabix-indexed file is preferred to a `.tbi`**, as htslib prefers it.
- **`AbstractFeatureReader.getFeatureReader(resource, codec, Index)` refuses a `TabixIndex` or `BCFCsiIndex`**, whose BGZF offsets it would misread; open the file with the index's path instead.
- **Tribble iterators close when read to their end, and closing a reader closes any still open** ([#494]), which can then no longer be used.
- A query whose start is after its end returns nothing from a Tribble `.idx` ([#204]), as from every other index.

#### Other

- **`DiskBackedQueue` and `SamRecordTrackingBuffer` are `AutoCloseable`, and no longer delete their spill files when garbage-collected**: close or clear them.
- Runtime dependencies are updated: commons-compress 1.28.0, which brings in commons-codec, xz 1.12, snappy-java 1.1.10.8, org.json 20260522, and commons-logging 1.4.0 for JEXL.
- VCF objects stored with Java serialization by 5.x, such as a `VCFHeader`, its header lines or genotypes, can't be deserialized by 6.0 (`InvalidClassException`), since their classes changed.  This concerns serialized Java objects only; VCF and BCF files written by 5.x read as before.
- Some public classes inside the CRAM and BCF codecs changed signatures: `TrialCompressor`'s constructor, `CompressionUtils.encodePack`, and the constructors of `BCF2FieldEncoder`'s subclasses.

### Output that differs from 5.x

What htsjdk writes, for anyone comparing output with files 5.x wrote (expected test outputs, for example).  Each is a fix or a documented change above.

- **VCF header**: only the first line per ID is kept; a standard line declared with another type stays as declared (`GQ` as `Float`, for example); `##contig` lines carry `md5`, `URL` and `species` from a sequence dictionary ([#730]); `##fileformat` is the header's version, at least 4.2, where a copy of a 4.3 or later header used to be written as 4.2; INFO and FORMAT lines keep every attribute, the standard ones first and the rest in their original order, and quoted values are escaped as the specification requires; repeated unstructured lines and structured lines with distinct IDs are kept ([#500], [#277]).
- **VCF records**: negative doubles are written like positive ones (`-5.00`, not `-5.000e+00`); a missing GT is written as `.` where 5.x threw, and a sample with every value missing as `.` rather than an empty column; String values are percent-encoded from VCF 4.3; `0/1|2` is written as it is, not as `0|1|2`; `LAA` follows `GT`.
- **BCF**: BGZF BCF 2.2 with a `.csi`; `IDX=` on every FILTER, INFO, FORMAT and contig line; flags as `BCF_BT_NULL`; vectors sized from their values and padded with END_OF_VECTOR; the GT width follows the allele count; the first allele's phase bit is set as htslib sets it; −127..−121 are stored as INT16; `rlen` comes from the record's end as htslib works it out.
- **CRAM**: a `.crai` rather than a `.cram.bai`; `M5` added to `@SQ` lines that lack it ([#506]); the file id holds the file name, so the same records written to different places give identical files ([#1068]); empty blocks are written RAW ([#1633]); reads without bases, unsigned `B` arrays and pairs whose template lengths depend on each other are stored as htslib stores them; the profiles choose different codecs (see "Performance").
- **SAM and BAM**: header text is UTF-8; `@RG DT` is as written; a CIGAR too long for BAM goes in `CG:B:I`, not `CG:B:i` ([#1560]); a U in SEQ is read and written as T ([#1728]); `x.sam.gz` is bgzipped SAM.
- **Indexes**: BAI, TBI and CSI have htslib's content (small bins folded into their parents, empty linear-index windows given the next window's offset) and equal samtools' and tabix's once read, except that a CSI for bgzipped SAM also carries a tabix header naming every sequence; a TBI carries the record counts tabix writes ([#1586]); a CRAM's BAI has real offsets in its metadata ([#401]); the last chunk of an index written with a BAM ends where samtools ends it.
- **Elsewhere**: `FastaReferenceWriter`'s `.fai` gives a sequence shorter than one line its own line widths, as samtools does; the `.md5` beside a gzipped file is of the compressed file ([#758]); `BasicFastqWriter` ends lines with `\n` on every platform ([#1105]); warnings go through `Log`, with its time and class prefix ([#1261]).

### New features

#### Indexes

- **CRAM is queried from its CRAI** through the new `CRAIQueryIndex` ([#851]), so a CRAM on a reference longer than 512 Mbp is queryable ([#1747]) and opening one is about 70% faster.
- **`SamReader.Indexing.getHtsIndex()`** returns a file's index in its own form, as the new format-neutral `htsjdk.index.HtsQueryIndex`, with `getSpanOfUnplaced()` and `getUnplacedRecordCount()`; `iterator(HtsFileSpan)` reads a span from it.
- **`htsjdk.index.BinningIndex`**: one sparse implementation of the BAI, TBI and CSI binning index, with a `Builder`, `merge`, and readers and writers for each layout.  An index over 50,000 one-record contigs takes 10 MB rather than 951 MB.  `FileBackedBinningIndex` reads one from its file.
- **BAI and CSI indexes are read a reference at a time, when a query needs them**, and held while memory allows: 1,000 queries on one chromosome of a whole-genome BAI take 2 ms rather than 64, and a CSI 11 ms rather than 932.  `SamReaderFactory.indexLoading(IndexLoading)` chooses `LAZY`, `EAGER` or `AUTO`, the default, which reads an index whole when it is given as a stream, is not on the default file system, or is under a megabyte, and lazily otherwise.  Every BAI and CSI is browseable, and `BamIndexValidator` checks any of them.
- **CSI for BAM**: `SAMFileWriterFactory.setBamIndexType(BamIndexType)` writes a BAI (the default), a CSI (`x.bam.csi`), or `AUTO`, a CSI only when a sequence is too long for a BAI; `setCsiMinShift` sets the smallest bins.  `BAMIndexer.createIndex(reader, output, log, indexType)` indexes an existing BAM.

  ```java
  SAMFileWriter w = new SAMFileWriterFactory()
          .setCreateIndex(true)
          .setBamIndexType(BamIndexType.AUTO)
          .makeBAMWriter(header, true, Path.of("out.bam"));
  ```

- **CSI for tabix-indexed files** (VCF, BED, GFF): `TabixIndexType.CSI` for `TabixIndexCreator`, `TabixIndexMerger`, `IndexFactory` (`IndexType.CSI`) and `VariantContextWriterBuilder.setTabixIndexType`.  The binning scheme and record counts are tabix's.
- **`TabixIndex.getRecordCount(String)` and `getRecordCount()`** give record counts from the index, as `bcftools index -s` and `-n` do ([#1586]).
- **BGZF BCF is queried through a CSI** by the new `BCFFileReader`, which `VCFFileReader` and `getFeatureReader` use; `getFeatureReader` finds `x.bcf.csi` or `x.csi`, from htsjdk or bcftools.  `IndexFactory.createIndex(path, codec, IndexType.CSI, dictionary)` builds the CSI of an existing BGZF BCF whose contigs are in the header's order.

#### SAM, BAM and CRAM

- **Bgzipped SAM** (`x.sam.gz`, also `.gzip`, `.bgz` and `.bgzf`) is written by `SAMFileWriterFactory`, indexed on the fly (CSI by default; `setSamIndexType`), queried through a samtools or tabix index like a BAM when opened from a `Path`, and indexed after the fact with `BAMIndexer.createIndex` from a reader opened with `INCLUDE_SOURCE_IN_RECORDS`.
- **`SAMRecordQueryNameNaturalComparator`** sorts read names in samtools' natural order (`SS:queryname:natural`).
- `CRAMContainerStreamWriter.finish(writeEOFContainer, closeStream)` lets a caller keep its stream open ([#1092]).
- **The `NORMAL_3_0` CRAM profile** writes CRAM 3.0 with rANS 4x8 and GZIP, which htsjdk before 4.3.0 and other readers without CRAM 3.1 support read.

#### VCF and BCF

- **BCF 2.1 and 2.2 are read, raw or BGZF-compressed** ([#946]), with htslib's dictionaries (sparse `IDX` values included) and values as the VCF reader holds them.  `BCF2Writer` is public, and `VariantContextWriterBuilder.setBCFVersion` chooses the version.
- **VCF 3.2 through 4.5 are read by default; 4.0 through 4.5 are written**, chosen with `VariantContextWriterBuilder.setVCFVersion`.  `VCFHeaderVersion` gains `VCF4_5` and methods that say what a version implies.
- **VCF 4.4 and 4.5 additions**: `Number=P`, `LA`, `LR`, `LG` and `M`; per-allele phase (`Genotype.isAllelePhased`, `hasPerAllelePhasing`, `GenotypeBuilder.allelePhasing`); every reserved INFO and FORMAT key as a constant in the new nested `VCFConstants.INFO` and `VCFConstants.FORMAT`, and in `VCFStandardHeaderLines` each one whose definition is the same in every version.
- **Structural variants**: `StructuralVariantAllele` and `Breakend` parse symbolic and breakend alleles in every form the specification gives (`Allele.asStructuralVariant()`, `getBreakend()`); `StructuralVariantType.parse` and `VariantContext.getStructuralVariantType()` consult both `SVTYPE` and the alleles.
- **INFO and FORMAT header lines keep every attribute, in any order**, reachable through `getGenericFieldValue` and `getGenericFields`.
- **`VariantFileType`** tells VCF from BCF by name (`fromFileName`) or by content (`fromContents`); `FileExtensions.GVCF`.
- **`smartMergeHeaders`** keeps the line with the later `Version` when two inputs define a field alike for the same `Source`, and reports the choice.
- **The VCF and BCF codecs are thread-safe once their header is read** ([#1026]): records, and lazily decoded genotypes, may be decoded on several threads at once.
- **VCF, GFF3 and every Tribble text format are read and written as UTF-8**, through `Utf8LineReader` (formerly `AsciiLineReader`).
- `VariantContext.getIDs()`, and `VCFHeader.getOtherHeaderLines(String)` for every line with a key.

#### Other

- `IOUtil.openGzipOrBgzfStream` reads BGZF, and gzip of any number of members, from any stream.
- `htsjdk.samtools.util.UnsynchronizedByteArrayOutputStream`, a `ByteArrayOutputStream` without locking.
- The jar names its automatic module `htsjdk` ([#1724]), so a modular application can `requires htsjdk;`.  htsjdk is not yet an explicit module, so it can't be used with jlink.

### Deprecations

- **`VCFConstants`' top-level INFO and FORMAT key names** (`GENOTYPE_KEY`, `DEPTH_KEY` and the rest), in favour of `VCFConstants.INFO` and `VCFConstants.FORMAT`; each one's Javadoc names its replacement, and `OLD_DEPTH_KEY` has none.  They remain compile-time constants, so only builds that treat deprecation warnings as errors notice.
- For removal in 7.0.0: `SamReader.Indexing.getIndex()`; `SAMFileWriterFactory.setCreateBaiIndexForCram`; `DiskBasedBAMFileIndex`, `CSIIndex`, `AbstractBAMFileIndex`, `BinningIndexBuilder`, `BinningIndexContent`, `LinearIndex`, `BinWithOffset`, and `BAMIndexMerger.mergeBins` and `mergeLinearIndexes`; and `SamIndexes.openIndexFileAsBaiOrNull`, `openIndexUrlAsBaiOrNull`, `asBaiStreamOrNull` and `asBaiSeekableStreamOrNull` ([#1425]).
- `SamReaderFactory.Option.CACHE_FILE_BASED_INDEXES` and `DONT_MEMORY_MAP_INDEX`, which have no effect.
- The sixteen `File` overloads that remain (see "Paths and files").
- `AsciiLineReader` and `AsciiLineReaderIterator`, in favour of `Utf8LineReader` and `Utf8LineReaderIterator`.
- `VCF3Codec`; `VCFFileReader.isBCF`, `AbstractVCFCodec.canDecodeFile` and `BCF2Codec.tryReadBCFVersion`, in favour of `VariantFileType`; `BCF2Codec.ALLOWED_MINOR_VERSION`, `ALLOWED_BCF_VERSION`, `validateVersionCompatibility` and `error(String)`.
- `VariantContextUtils.getMetaDataForField`, in favour of `VCFHeader.getInfoHeaderLine` and `getFormatHeaderLine`.

### Performance

Against 5.0.2, each run in a fresh JVM, with BGZF level 1 and the NORMAL CRAM profile, on chromosome 3 of a 1000 Genomes WGS BAM (1.37 million reads), a 1,000-sample VCF and a one-million-record gVCF:

| | Change |
|---|---:|
| BAM to CRAM | −29% |
| CRAM to BAM | −7% |
| Reading VCF | −9% to −11% |
| VCF to bgzipped VCF | −12% |
| VCF to BCF 2.1 | −17% |
| Reading BAM, BAM to BAM, BCF to VCF | unchanged |

- **The CRAM 3.1 profiles try the codecs htslib's do, and choose among them as htslib does.**  NORMAL's files are 0.7–1.1% larger than samtools'; SMALL's and ARCHIVE's are 2–9% smaller than before but take longer to write.  The rANS Nx16 and Range coders encode faster, and a CRAM 3.1 writer no longer holds 6 MB of rANS tables for every distinct tag.
- **BCF**: writing takes genotypes by position and encodes without a lock per byte; decoding skips padding and missing vectors.
- **SAM text** is parsed about 2.2 times as fast, with fewer allocations per record; writing it is about 4.5% slower for reads with long Z tags such as OQ, for the checks that keep its text valid.
- **BAM**: bases are packed into BAM's 4-bit codes through a lookup table.
- **Indexes**: queries through a tabix index are faster (`VCFFileReader.query` −20%); see also "Indexes" under "New features".
- **`SamLocusIterator` and `EdgeReadIterator` no longer slow down as reads get longer** ([#1490]).

### Bug fixes

Fixes that change output are listed under "Output that differs from 5.x".

#### SAM, BAM and CRAM

- A mapped read without bases (SEQ `*`) keeps its CIGAR, NM and MD through a CRAM htsjdk writes; 5.x read it back as `0M`.
- htsjdk 4.x can read the CRAMs htsjdk writes again, so far as it reads their CRAM version: 5.0.0 wrote empty GZIP blocks that 4.x rejects ([#1633]).
- A CRAM labelled 3.0 no longer contains CRAM 3.1 codecs: the `FAST` profile compressed tags with rANS Nx16.
- CRAM template lengths of mates stored together are derived as htslib derives them ([#1189]).
- Unsigned `B` arrays keep their type through CRAM ([#499]), and bytes 0x80–0xFF in CRAM read names and tags are kept.
- The NM worked out for a CRAM record counts every inserted base ([#1187]); a read running past the end of its reference keeps its bases, NM and MD.
- Reading CRAM matches samtools in MD past the end of the reference, the internal `cF` tag, and the PNEXT of an unpaired read.
- The CRAM writer keeps an unpaired read's 0x2, 0x8, 0x20, 0x40 and 0x80 flags and its PNEXT, and an `RG` tag with no `@RG` line.  Such unpaired reads now fail STRICT validation when read from CRAM, as they do from SAM and BAM.
- The CRAM writer handles an empty quality array as missing qualities, and a slice whose placed reads are all unmapped ([#714]).
- A CRAM with an EOF container partway through is read to its end ([#1271]).
- CRAM quality scores with a HUFFMAN encoding can be read ([#518]).
- CRAM iterators over a seekable stream no longer cut each other short ([#563]).
- A CRAM opened by `Path` is read through a buffer; unbuffered, it was very slow on NFS ([#1756]).
- `ReferenceSource` no longer returns another contig's bases when one source serves CRAMs whose `@SQ` lines are in different orders.
- `CRAMFileReader` constructors without a validation stringency use `SILENT`.
- The beta API's CRAM 3.0 encoder writes CRAM 3.0, and its 2.1 encoder throws.
- Multi-interval queries return every record when a file's references are not in dictionary order ([#1271]).
- `queryUnmapped()` on a CSI-indexed BAM no longer seeks to the wrong place.
- A BAM writer that can't build or write its index fails the write, and refuses later writes.
- Reading a BAI or TBI whose metadata bin has the wrong number of chunks suggests a CSI index, since the reference is probably longer than 2^29 ([#823], [#643]).
- A sort-order check no longer fails because the caller changed a record it was given ([#760]).
- `SamReaderFactory.makeDefault()` uses the default inflater factory in force when it is called ([#1666], [#831]).
- A plain SAM file can be iterated again after its first iterator is closed ([#681]).
- A reader whose header can't be read no longer leaks its file handle ([#494]).
- `SAMFileWriterFactory`'s copies keep the deflater factory and the flag format.
- `SAMRecord.isUnsignedArrayAttribute` no longer throws `NullPointerException`.
- `SAMUtils.getNumOverlappingAlignedBasesToClip` and `clipOverlappingAlignedBases` clip part of a partly overlapping `=` or `X` element, as they do `M` (5.x clipped `150=` to `150S` where `50=100S` was right), and no longer clip mates on different references.
- A `SAMFileWriter` given records out of order names the record before the offending one, rather than the offending one twice.
- Bgzipped SAM is read with the `InflaterFactory` set on `SamReaderFactory`, rather than always the default.
- A CRAM reader on a stream that can't seek throws a clear `IllegalStateException` for a query or index call, rather than `NullPointerException` ([#426]).
- A failed CRAM reference lookup names the sequence and the `.fai`, `.gzi` and `.dict` files a CRAM's reference needs ([#998], [#1732]).
- Dates with a space or a lower-case `t` or `z` are parsed ([#1217]).

#### VCF and BCF

- The BCF writer keeps an interior missing value in a vector, sizes each FORMAT vector from its values, sizes strings by their UTF-8 length, writes String INFO values whatever their comma count ([#1063]), accepts a `Flag` line with a non-zero `Number`, and masks n_sample to 24 bits.
- A missing value inside a BCF vector no longer shifts the values after it.
- INFO values are typed by their INFO line and FORMAT values by their FORMAT line, where an INFO field sharing an ID with a FORMAT field took the FORMAT line's type.
- `getAttributeAsInt`, `getAttributeAsDouble`, `getAttributeAsBoolean`, `getAttributeAsIntList` and `getAttributeAsDoubleList` return the default for a missing value read from VCF ([#1228], [#1073]).
- Spaces in INFO values are accepted ([#1667]).
- A malformed genotype is reported as a `TribbleException` naming the sample and key.
- `VCFFileReader` opens VCF 3.2 and 3.3, and reads a 3.x record with `FILTER=PASS` as passing.
- `smartMergeHeaders` promotes `Integer` and `Float` to `Float` in either order, and keeps every contig when the inputs' dictionaries differ ([#1574]).
- `Genotype.sameGenotype` ignoring phase tells polyploid genotypes apart by allele counts.
- `getStructuralVariantType()` returns `DEL` for an `SVTYPE` of `DEL:ME`.
- `VariantContext.hasID()` is false for a deserialized record without an ID.
- An empty VCF is reported as empty ([#973]).
- `toString()` no longer decodes lazily decoded genotypes ([#1011]).
- Closing a VCF or BCF writer, or a `BlockCompressedOutputStream`, twice does nothing ([#469]).
- VCF and BCF are read and written without commons-jexl on the class path.
- `VCFFileReader` opens a path in a zip or jar file system.
- `VariantContextWriterBuilder` keeps an `IndexCreator` the caller supplied for block-compressed VCF.

#### Indexes and tabix

- A tabix index with a sequence that has no records is written correctly.
- `TabixReader` returns a last line without a newline, strips a CR before the newline, accepts contig names containing `:` or `-` ([#1017]), rejects a plain-gzip file, and names the index files it looked for.
- A Tribble index with a byte of 0x80 or above in a name is read ([#1614]).

#### FASTA and references

- `FastaSequenceIndexCreator` indexes a FASTA with blank lines ([#1748]) or a last line without a newline.
- A FASTA whose first lines are `;` comments can be read and indexed.
- A sequence longer than 2^30 bases read without a dictionary no longer fails ([#1380]).
- A gzipped (not bgzipped) FASTA with a `.fai` is read sequentially rather than rejected ([#1143]).
- Opening an indexed FASTA checks that it matches its `.fai`.
- Indexed lookups from several threads return the right bases ([#1749]), and bgzipped lookups no longer re-inflate a block each time ([#1131]).
- A bgzipped FASTA from `FastaReferenceWriter` can be opened as indexed, and `GZIIndex.buildIndex` handles more than 2 GiB of data.
- Closing a `FastaReferenceWriter` given no sequences writes an empty FASTA ([#1498]).

#### Input, output and utilities

- Gzipped and bgzipped input from pipes, sockets and URLs is no longer silently truncated ([#1691]).
- A BGZF file made by joining others is read to its end.
- `IOUtil.isGZIPInputStream` checks only the gzip header.
- FTP works with strict servers, on any port, and fails cleanly for a missing file ([#470], [#797], [#463], [#464], [#618], [#623]).  A reader picks the format of a file over FTP by its name, so a CRAM or SAM over FTP no longer opens as BAM, and a BAM whose name doesn't end in `.bam` is no longer opened as one.
- `SeekableMemoryStream.read(buffer, offset, 0)` returns 0, and `SeekablePathStream.read()` no longer returns 0 for an empty channel read ([#1399]).
- `IOUtil.deletePaths` given a single `Path` deletes only that path.
- `IOUtil.assertFileIsWritable` and `assertDirectoryIsWritable` accept outputs on NFS servers whose access checks disagree with their ACLs ([#1671]).
- `ProcessExecutor.executeAndReturnInterleavedOutput` no longer deadlocks on large output.
- `SortingCollection` buffers its temporary files on large heaps ([#1741]), and `SortingLongCollection` clamps its buffer as documented.
- `TagFilter` accepts a list of any type ([#1082]).
- `Histogram.getMin`, `getMax` and `getMode` throw `IllegalStateException` on an empty histogram ([#1657]).
- A malformed BED or GFF3 line is reported with its file, column and text ([#709]).
- The published POM lists the Apache 2.0 and LGPL licences as well as MIT ([#1710]).

[#204]: https://github.com/samtools/htsjdk/issues/204
[#250]: https://github.com/samtools/htsjdk/issues/250
[#277]: https://github.com/samtools/htsjdk/issues/277
[#401]: https://github.com/samtools/htsjdk/issues/401
[#426]: https://github.com/samtools/htsjdk/issues/426
[#463]: https://github.com/samtools/htsjdk/issues/463
[#464]: https://github.com/samtools/htsjdk/issues/464
[#469]: https://github.com/samtools/htsjdk/issues/469
[#470]: https://github.com/samtools/htsjdk/issues/470
[#494]: https://github.com/samtools/htsjdk/issues/494
[#499]: https://github.com/samtools/htsjdk/issues/499
[#500]: https://github.com/samtools/htsjdk/issues/500
[#506]: https://github.com/samtools/htsjdk/issues/506
[#518]: https://github.com/samtools/htsjdk/issues/518
[#561]: https://github.com/samtools/htsjdk/issues/561
[#563]: https://github.com/samtools/htsjdk/issues/563
[#618]: https://github.com/samtools/htsjdk/issues/618
[#623]: https://github.com/samtools/htsjdk/issues/623
[#643]: https://github.com/samtools/htsjdk/issues/643
[#664]: https://github.com/samtools/htsjdk/issues/664
[#681]: https://github.com/samtools/htsjdk/issues/681
[#709]: https://github.com/samtools/htsjdk/issues/709
[#714]: https://github.com/samtools/htsjdk/issues/714
[#730]: https://github.com/samtools/htsjdk/issues/730
[#751]: https://github.com/samtools/htsjdk/issues/751
[#758]: https://github.com/samtools/htsjdk/issues/758
[#760]: https://github.com/samtools/htsjdk/issues/760
[#787]: https://github.com/samtools/htsjdk/issues/787
[#797]: https://github.com/samtools/htsjdk/issues/797
[#823]: https://github.com/samtools/htsjdk/issues/823
[#831]: https://github.com/samtools/htsjdk/issues/831
[#851]: https://github.com/samtools/htsjdk/issues/851
[#929]: https://github.com/samtools/htsjdk/issues/929
[#945]: https://github.com/samtools/htsjdk/issues/945
[#946]: https://github.com/samtools/htsjdk/issues/946
[#973]: https://github.com/samtools/htsjdk/issues/973
[#998]: https://github.com/samtools/htsjdk/issues/998
[#1011]: https://github.com/samtools/htsjdk/issues/1011
[#1017]: https://github.com/samtools/htsjdk/issues/1017
[#1026]: https://github.com/samtools/htsjdk/issues/1026
[#1063]: https://github.com/samtools/htsjdk/issues/1063
[#1068]: https://github.com/samtools/htsjdk/issues/1068
[#1073]: https://github.com/samtools/htsjdk/issues/1073
[#1082]: https://github.com/samtools/htsjdk/issues/1082
[#1092]: https://github.com/samtools/htsjdk/issues/1092
[#1105]: https://github.com/samtools/htsjdk/issues/1105
[#1108]: https://github.com/samtools/htsjdk/issues/1108
[#1131]: https://github.com/samtools/htsjdk/issues/1131
[#1143]: https://github.com/samtools/htsjdk/issues/1143
[#1179]: https://github.com/samtools/htsjdk/issues/1179
[#1187]: https://github.com/samtools/htsjdk/issues/1187
[#1189]: https://github.com/samtools/htsjdk/issues/1189
[#1202]: https://github.com/samtools/htsjdk/issues/1202
[#1217]: https://github.com/samtools/htsjdk/issues/1217
[#1228]: https://github.com/samtools/htsjdk/issues/1228
[#1261]: https://github.com/samtools/htsjdk/issues/1261
[#1271]: https://github.com/samtools/htsjdk/issues/1271
[#1295]: https://github.com/samtools/htsjdk/issues/1295
[#1380]: https://github.com/samtools/htsjdk/issues/1380
[#1399]: https://github.com/samtools/htsjdk/issues/1399
[#1425]: https://github.com/samtools/htsjdk/issues/1425
[#1490]: https://github.com/samtools/htsjdk/issues/1490
[#1498]: https://github.com/samtools/htsjdk/issues/1498
[#1560]: https://github.com/samtools/htsjdk/issues/1560
[#1573]: https://github.com/samtools/htsjdk/issues/1573
[#1574]: https://github.com/samtools/htsjdk/issues/1574
[#1584]: https://github.com/samtools/htsjdk/issues/1584
[#1586]: https://github.com/samtools/htsjdk/issues/1586
[#1614]: https://github.com/samtools/htsjdk/issues/1614
[#1633]: https://github.com/samtools/htsjdk/issues/1633
[#1657]: https://github.com/samtools/htsjdk/issues/1657
[#1666]: https://github.com/samtools/htsjdk/issues/1666
[#1667]: https://github.com/samtools/htsjdk/issues/1667
[#1671]: https://github.com/samtools/htsjdk/issues/1671
[#1691]: https://github.com/samtools/htsjdk/issues/1691
[#1710]: https://github.com/samtools/htsjdk/issues/1710
[#1724]: https://github.com/samtools/htsjdk/issues/1724
[#1728]: https://github.com/samtools/htsjdk/issues/1728
[#1732]: https://github.com/samtools/htsjdk/issues/1732
[#1741]: https://github.com/samtools/htsjdk/issues/1741
[#1747]: https://github.com/samtools/htsjdk/issues/1747
[#1748]: https://github.com/samtools/htsjdk/issues/1748
[#1749]: https://github.com/samtools/htsjdk/issues/1749
[#1756]: https://github.com/samtools/htsjdk/issues/1756

---

## 5.0.2

Patch release on the 5.0 line.

### Bug fixes

- **A CRAM `ReferenceSource` shared between threads no longer returns another contig's bases** ([#1643](https://github.com/samtools/htsjdk/issues/1643)).  It cached the most recent contig's bases and that contig's index in two fields, written without a lock from `getReferenceBasesByRegion`, so two threads asking for different contigs could leave one contig's bases labelled as the other's.  The pair is now published as one immutable object, so a caller can no longer silently get the wrong reference bases and encode or decode CRAM records against them.

## 5.0.1

Patch release on the 5.0 line.

### Performance

- **BCF records decode faster**, most of all their genotypes.  `BCF2Decoder` reads each value straight from the record's bytes rather than a byte at a time through a synchronized `ByteArrayInputStream`.  Reading a 3,000-sample BCF and decoding every genotype takes 40–60% less time on Apple silicon.  Decoded values are unchanged.
- **Writing VCF records with many samples is faster**, and the output is unchanged.  `VCFEncoder.formatVCFDouble` formats `%.2f` and `%.3f` values without a `java.util.Formatter` except near a rounding halfway point.  List and array attributes are formatted without reflection.  `VCFWriter` encodes each record into a `StringBuilder` and writes it in one call, rather than taking the `BufferedWriter`'s lock for every few characters.
- **The VCF encoder does less for each sample.**  It works out each FORMAT key's kind once per record, writes a missing attribute without formatting it, takes genotypes by position when a record's genotypes are in the header's sample order, and finds a GT allele's code by identity before hashing its bases.  Output is unchanged.

### Bug fixes

- **A truncated or corrupt BCF record now fails with a `TribbleException`** when decoding reaches the end of the record's bytes.  It used to read each missing byte as `0xFF` (so a value came out as `-1`) and carry on.
- **A VCF record that fails to encode no longer corrupts the next one.**  `VCFWriter` used to leave the columns it had written before the failure at the start of the next record's line.

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
