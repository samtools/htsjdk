# CHANGELOG

This changelog covers htsjdk releases from 3.0.0 onwards.  For earlier releases, see
the [GitHub Releases](https://github.com/samtools/htsjdk/releases) page.

Prior to 3.0.0, htsjdk followed a 2.x versioning scheme for several years with incremental
improvements to SAM/BAM/CRAM/VCF support, all targeting Java 8.  The 3.0.0 release introduced
the first major API breaking changes (notably converting `Allele` to an interface) and added
early infrastructure for a plugin-based codec framework and resource bundles.

---

## 6.0.0

Major release.

### Headlines

- **An indexed CRAM is now written with a `.crai`, not a `.cram.bai`, and CRAM region queries are answered from the CRAI directly.**  Opening a CRAI-indexed CRAM is several times faster, queries read fewer containers, and a CRAM on a reference longer than 512 Mbp is finally queryable.  See below for the migration path.
- **BAM and tabix-indexed files (VCF, BED, GFF, ...) can be indexed with CSI**, so references longer than 512 Mbp are indexable and queryable.  BAI and TBI remain the defaults.
- **htsjdk now uses `java.nio.file.Path` (not `java.io.File`) throughout its public API.**  This makes the whole library work with any NIO `FileSystemProvider` (SPI) — Amazon S3, Google Cloud Storage, HDFS, in-memory filesystems such as [jimfs](https://github.com/google/jimfs), and so on — through the same reader, writer, factory and index APIs you already use, with no File-specific code paths.
- **String- and URI-based entry points are scheme-aware.**  Where an API takes a path as a `String`, it is resolved with `IOUtil.getPath`, which honours the URI scheme (e.g. `file:`, `gs:`, `s3:`, custom providers) and falls back to the local filesystem for plain paths (including paths containing spaces).  Existing HTTP/HTTPS and FTP behaviour is unchanged — those continue to flow through htsjdk's `SeekableStream` machinery rather than NIO.

### ⚠️ Breaking changes

Consumers should review these before upgrading.

- **An indexed CRAM is written with a `.crai`, not a `.cram.bai`.**  Reading is unaffected, since `SamFiles.findIndex` resolves either, but anything that globs for `*.cram.bai` or names the index file explicitly needs updating.  samtools never wrote a BAI for a CRAM and cannot read one.

  ```java
  // htsjdk 5.x wrote out.cram + out.cram.bai
  // htsjdk 6.0.0 writes  out.cram + out.cram.crai
  SAMFileWriter w = new SAMFileWriterFactory().setCreateIndex(true).makeCRAMWriter(header, true, cram, reference);

  // To keep writing a BAI for one more release (deprecated on arrival, removed in 7.0.0):
  SAMFileWriter w = new SAMFileWriterFactory().setCreateIndex(true).setCreateBaiIndexForCram(true)
                        .makeCRAMWriter(header, true, cram, reference);
  ```

- **`java.io.File`-based public APIs have been removed in favour of `java.nio.file.Path`** across factories, readers, writers, indexes, reference-sequence access and the utility classes (224 methods/constructors).  Update call sites to pass a `Path` (or a `String`/`URI`, which are resolved via `IOUtil.getPath`).  For example:

  ```java
  // Before (htsjdk 5.x)
  SamReader r = SamReaderFactory.makeDefault().open(new File("/data/x.bam"));
  SAMFileWriter w = new SAMFileWriterFactory().makeBAMWriter(header, true, new File("/data/out.bam"));
  ReferenceSequenceFile ref = ReferenceSequenceFileFactory.getReferenceSequenceFile(new File("/data/ref.fasta"));

  // After (htsjdk 6.0.0)
  SamReader r = SamReaderFactory.makeDefault().open(Path.of("/data/x.bam"));
  SAMFileWriter w = new SAMFileWriterFactory().makeBAMWriter(header, true, Path.of("/data/out.bam"));
  ReferenceSequenceFile ref = ReferenceSequenceFileFactory.getReferenceSequenceFile(Path.of("/data/ref.fasta"));

  // Or open something on a custom filesystem (requires that provider on the classpath)
  SamReader s3 = SamReaderFactory.makeDefault().open(URI.create("s3://bucket/x.bam"));
  ```

  To bridge a `File` you already hold, call `file.toPath()` (or `IOUtil.toPath(file)`, which is null-safe).

- **`Defaults.REFERENCE_FASTA` is now a `Path`** (previously a `File`).  It is resolved from the `samjdk.reference_fasta` system property; an unparseable value is logged and treated as unset rather than failing class initialisation.

- **`TabixIndex` is built on the new `htsjdk.index.BinningIndex`.**  `TabixIndex(TabixFormat, List<String>, BinningIndex)` replaces the constructor taking `BinningIndexContent[]`, and `getBinningIndex()` replaces `getIndices()`.  `TabixReader`'s protected `TPair64`, `TIndex` and `mIndex`, and `TabixUtils.TPair64`, `TIndex`, `TIntv` and `less64`, are removed; `TabixReader` now loads a `TabixIndex`.  A truncated `.tbi` is reported as a `TribbleException` rather than an `EOFException`.

- **A `.csi` beside a tabix-indexed file is preferred to a `.tbi`**, which is htslib's order.  This applies to `TabixReader`, `AbstractFeatureReader.isTabix` and `VariantsBundle`.

- **Asking `SAMFileWriterFactory` for `x.sam.gz` now writes bgzipped SAM.**  `makeSAMOrBAMWriter` and `makeWriter` used to treat every name not ending in `.sam` as BAM, so `x.sam.gz` received BAM bytes.  File-name detection also ignores case now, so `x.SAM` and `x.CRAM`, which used to be written as BAM, are written as SAM and CRAM.  See "Bgzipped SAM" below.

### CRAM indexing

- **CRAM region queries are answered from the CRAI directly** by the new `CRAIQueryIndex`, instead of rebuilding the whole CRAI as an in-memory BAI on every reader open (issue #851).  The index is not read until a query needs it, so opening a reader for sequential reading no longer touches it.  Measured on a 102 MB GRCh38 CRAM (2,395 CRAI entries, 3,366 sequences):

  | | 5.x | 6.0.0 | |
  |---|---:|---:|---|
  | Open a CRAI-indexed CRAM | 30–38 ms | 9 ms | −70% |
  | Bytes read for 200 × 1 kbp region queries | 86.0 MB | 78.6 MB | −8.6% |
  | Heap retained by an open reader | 4.97 MB | 3.22 MB | −35% |
  | 200 × 1 kbp region queries | 8.3–8.8 s | 8.6 s | unchanged |
  | Full sequential read | 16.3–16.4 s | 16.4–16.6 s | unchanged |

  Query wall time is dominated by decoding the matching container, so it does not move.  Fewer bytes are read because BAI bins are coarse: the old path also read containers whose slices did not overlap the query and then skipped them, whereas the CRAI path checks overlap directly.  Over 2,000 random 1 kbp queries on that file the BAI path resolved 8,465 containers to the CRAI path's 1,953.

- **A CRAM on a reference sequence longer than 512 Mbp is now queryable** (issue #1747).  BAI bins top out at 2<sup>29</sup>−1, which is why large genomes failed; a CRAI has no binning and no such ceiling.  CSI for CRAM is not supported, matching samtools.

- **New: `SamReader.Indexing.getHtsIndex()`**, returning the index in whatever form the file has: a `CRAIQueryIndex` for a CRAI, a `BAMIndex` for a BAI.  Format-specific capabilities are reached by asking for the type: `getHtsIndex(BAMIndex.class)` for per-reference record counts, `getHtsIndex(BrowseableBAMIndex.class)` for bin traversal, `getHtsIndex(CRAIQueryIndex.class)` for container offsets; each returns empty when the file's index is not of that kind.  `iterator(HtsFileSpan)` accepts a span from `getHtsIndex()` directly.  `getIndex()` is deprecated and will be removed in 7.0.0; it still returns a `BAMIndex`, synthesised on demand for a CRAI.

- **New: format-neutral `htsjdk.index.HtsQueryIndex` and `htsjdk.index.HtsFileSpan`**, covering the one question every coordinate index answers: given a region, which byte ranges could hold its records.  `SAMFileSpan` extends `HtsFileSpan` and `BAMIndex` extends `HtsQueryIndex`; both changes are source- and binary-compatible.  References are addressed by ordinal rather than by name, because a BAI file contains no sequence names.

- **A CRAI-backed index has no record counts, and no longer pretends to** (issue #531).  `CRAIQueryIndex` has no metadata method.  Counts reached through the deprecated `getIndex()` on a CRAI-indexed CRAM are still zeroes; real counts come from indexing the CRAM itself with `CRAMBAIIndexer`.

- **The index caching and memory-mapping flags are documented as BAI-only** (issue #535).  A CRAI is read fully into memory, so there is nothing to cache or memory-map.

### Tabix and CSI indexing

- **New: `htsjdk.index.BinningIndex`**, one sparse, format-neutral implementation of the binning index that TBI, BAI and CSI share, with a parameterised binning scheme (`min_shift`, depth), a `Builder`, `merge`, and readers and writers for the BAI/TBI layout and for CSI.  It implements `HtsQueryIndex`.  Tabix indexes moved onto it with their bytes unchanged; BAM indexes have not moved yet.  Against 5.x on a 2.4 M-record VCF: index-only queries 242 → 75 ms per 200,000, `VCFFileReader.query` −20%, index open −22%.  A loaded index over 50,000 one-record contigs takes 10 MB instead of 951 MB, because bins are no longer held in dense per-contig arrays.

- **New: CSI indexes for tabix-indexed files** (`TabixIndexType.CSI`).  TBI's fixed scheme stops at 2<sup>29</sup> bases; CSI stores its scheme and reaches further.  `TabixIndex` reads either format by its magic number and writes the type it was built as.  `TabixIndexCreator`, `AllRefsTabixIndexCreator`, `StreamBasedTabixIndexCreator`, `TabixIndexMerger`, `IndexFactory` (`IndexType.CSI`, `createTabixIndex(..., TabixIndexType)`) and `VariantContextWriterBuilder` (`setTabixIndexType`, `setCsiMinShift`) all take the type.  The binning scheme is the one htslib's `tabix -C` chooses, and the index carries the per-contig record counts that `bcftools index -n` reads.  `Tribble.csiIndexPath` and `tabixIndexPath(path, type)` name the index; `TabixUtils.findIndex` finds it.

  ```java
  VariantContextWriter w = new VariantContextWriterBuilder()
          .setOutputPath(Path.of("calls.vcf.gz"))
          .setReferenceDictionary(dictionary)
          .setOption(Options.INDEX_ON_THE_FLY)
          .setTabixIndexType(TabixIndexType.CSI)
          .build();
  ```

- The test suite cross-checks htsjdk against htslib's `tabix` in both directions, for TBI and CSI; CI installs `tabix` alongside samtools.

### BAM indexing

- **New: CSI indexes for BAM**, for references longer than 512 Mbp, which a BAI cannot address.  `SAMFileWriterFactory.setBamIndexType(BamIndexType)` chooses the index written when index creation is on: `BAI` (the default, unchanged, written as `x.bai`), `CSI` (written as `x.bam.csi`, as samtools names it), or `AUTO`, which writes a CSI only when a sequence in the header is too long for a BAI, so that a pipeline expecting `.bai` files keeps getting them.  `setCsiMinShift` sets the span of the smallest bins (default 14, as for samtools); the rest of the binning scheme is chosen as `samtools index -c` chooses it.  `BAMIndexer` takes the same type, as does `BAMIndexer.createIndex(reader, output, log, indexType)` for indexing an existing BAM.  samtools reads the result, record counts included.

  ```java
  SAMFileWriter w = new SAMFileWriterFactory()
          .setCreateIndex(true)
          .setBamIndexType(BamIndexType.AUTO)
          .makeBAMWriter(header, true, Path.of("out.bam"));
  ```

- `BAMIndexer` now builds through `htsjdk.index.BinningIndex` and writes the index when `finish()` is called, rather than reference by reference.  A BAI built for a real BAM is byte-for-byte what 5.x wrote, with one exception: a placed but unmapped read whose position is the first base of a 16 kb window is now entered in the linear index under that window, as samtools enters it, rather than under the window before.  An attempt to index a read beyond 2<sup>29</sup> in a BAI fails with a message pointing at CSI.

- `CRAMBAIIndexer` builds through `BinningIndex` too.  Queries through a `.cram.bai` return what they did; the bytes differ in two places: the metadata pseudo-bin's offsets, which were a placeholder that nothing read (issue #401) and are now the offsets of the reference's first and last slices, and the linear index, which no longer marks the window after a slice that ends exactly on a 16 kb boundary.  A slice beyond 2<sup>29</sup> fails with a message pointing at CRAI rather than with an array-bounds error.

- **BAI and CSI indexes are read a reference at a time, and held only while there is memory for them.**  Opening an indexed BAM no longer costs anything until a query is made; a query reads the part of the index for its reference sequence, found by skipping over those before it and remembered thereafter.  What has been read is held through soft references and never evicted by htsjdk: with memory to spare a reader ends up with its whole index in memory, and when memory is short the collector reclaims the least recently used parts, across every open reader, which are read again if wanted.  This replaces three readers that each behaved differently: the default BAI reader, which kept nothing and walked the index file again for every query (1,000 queries on one chromosome of a whole-genome index: 64 ms, now 2 ms); the caching BAI reader behind `CACHE_FILE_BASED_INDEXES`; and the CSI reader, which re-read through BGZF for every query (932 ms, now 11 ms).  Index files are no longer memory-mapped, so an index's memory is ordinary heap that the JVM accounts for; the price is about a millisecond on the first query of a large BAI.

- **New: `SamReaderFactory.indexLoading(IndexLoading)`** chooses when the index is read: `LAZY` as above, `EAGER` to read it whole in one pass when first needed, or `AUTO` (the default), which is eager for an index given as a stream, one that is not on the default file system (a single sequential read suits remote storage better than seeks) and one smaller than a megabyte, and lazy otherwise.  Whatever was read stays reclaimable, so a job holding thousands of remote indexes sheds them under pressure rather than failing, and re-reads a reference by range.

- **`SamReaderFactory.Option.CACHE_FILE_BASED_INDEXES` and `DONT_MEMORY_MAP_INDEX` are deprecated and have no effect.**  What the first asked for is now always the case, for CSI and stream-supplied indexes too, and nothing is memory-mapped for the second to prevent.

- **Every BAI and CSI index is browseable.**  `SamReader.Indexing.hasBrowseableIndex()` was true for a BAI only with `CACHE_FILE_BASED_INDEXES`; it is now true for any BAM index and for a CRAM's BAI.  `getIndex()` still returns a `BAMIndex`, but no longer one of `DiskBasedBAMFileIndex`, `CachingBAMFileIndex` or `CSIIndex`, so code that cast the result to those needs `getHtsIndex(BrowseableBAMIndex.class)` or the `BAMIndex` methods instead.  `BamIndexValidator`, which silently checked nothing for a BAI unless index caching was on, now checks any BAI or CSI.

- New in `htsjdk.index`: `ReferenceBinsSource`, the view of a binning index that its readers need, implemented by `BinningIndex` (all in memory) and by the new `FileBackedBinningIndex` described above.

- **The old BAM index readers and index model are deprecated**, for removal in 7.0.0.  `DiskBasedBAMFileIndex`, `CSIIndex` and their base `AbstractBAMFileIndex` still work when constructed directly, with their public methods unchanged, but they now answer through the reader described above: the `useMemoryMapping` / `enableMemoryMapping` constructor arguments have no effect, a query that finds nothing returns an empty span where it used to return `null`, and the protected methods of `AbstractBAMFileIndex` that read the index file are gone, there being no file buffer beneath them any more: `readBytes`, `readInteger`, `readLong`, `skipBytes`, `seek`, `position`, `readChunks`, `skipToSequence`, `setSequenceIndexes`, `verifyIndexMagicNumber`, `initParameters`, `query` and `getQueryResults`, along with `CSIIndex.getQueryResults`.  Only a subclass of `DiskBasedBAMFileIndex` or `CSIIndex` outside htsjdk could have called them, and the last two returned a package-private type; such a subclass will no longer compile.  Also deprecated, since nothing in htsjdk uses them any longer: `BinningIndexBuilder`, `BinningIndexContent`, `LinearIndex`, `BinWithOffset`, and `BAMIndexMerger.mergeBins` / `mergeLinearIndexes`.  `Bin`, `BinList` and `BAMIndexMetaData` are not, because `BrowseableBAMIndex` and `BAMIndex` hand them out.

- `BAMIndexMerger` merges through `BinningIndex.merge`, which now understands part indexes whose empty linear-index windows were left unset, as a BAM part's are.  The merged BAI is byte-for-byte what it was.  `BAMIndexMerger.processIndex` reads the part's index there and then, so the caller may close it straight away, and rejects a CSI.

### Bgzipped SAM

- **New: `SAMFileWriterFactory` writes BGZF-compressed SAM** when the output name is `.sam` followed by `.gz`, `.gzip`, `.bgz` or `.bgzf`, in any case, through `makeWriter`, `makeSAMOrBAMWriter` or `makeSAMWriter`.  The factory's compression level and deflater factory apply, the file ends with the BGZF end-of-file block, and an MD5 file, if requested, is of the compressed bytes.  samtools and `SamReaderFactory` read the result.  No index is written along with it yet; index the finished file as described below.

- **New: region queries on bgzipped SAM.**  A `.sam.gz` opened from a `Path` is queried through an index just as a BAM is: `query`, `queryOverlapping`, `queryContained`, `queryAlignmentStart`, `queryUnmapped`, `queryMate`, and iteration over a file span.  The index is found beside the file (`x.sam.gz.bai`, then `x.sam.gz.csi`, then `x.sam.gz.tbi`) or named through `SamInputResource.index(...)`, and may be any of the four kinds the two tools write: the BAI or CSI of `samtools index`, which number references as the header lists them, or the TBI or CSI of `tabix -p sam`, which number them in the order the file meets them and name them.  The second kind is asked by name.  That matters because samtools itself reads a tabix-made CSI by header ordinal, and silently returns some other reference's reads, or none, whenever a sequence early in the header has no reads.  An index that fits neither reading (a different number of references than the header has, a tabix index for a format other than SAM, or one naming a sequence the header lacks) is refused.  Such a reader can also be iterated more than once, which no SAM reader could be.  Not covered: bgzipped SAM opened from a `SeekableStream` or a URL, where the factory goes by the source's name and does not yet know `.sam.gz`.

- **New: indexing an existing bgzipped SAM file.**  `BAMIndexer.createIndex(reader, output)` (or with `BamIndexType.CSI`) works for a `.sam.gz` reader opened with `INCLUDE_SOURCE_IN_RECORDS`, because records of bgzipped SAM now say where in the file they lie, as records of a BAM do.  File offsets, linear index and record counts are identical to those of `samtools index` on the same file; only the binning differs, since samtools folds sparsely used bins into their parents and htsjdk does not.  A CSI written this way also carries a tabix header naming every `@SQ` of the header in order, which makes the two tools' numberings one: samtools and tabix both read it correctly, which is true of neither tool's own CSI.  Reading a `.sam.gz` costs what it did (+0.2% on a million records), and +0.8% with `INCLUDE_SOURCE_IN_RECORDS`, which used to attach an empty source to a SAM record and now builds its file pointer.

- New in `htsjdk.index`: `RenumberedReferenceBinsSource`, an index presented under another numbering of its references.  New on `TabixIndex`: `readCsiAux` and the `Header` record, for reading the tabix header of a CSI without reading the index.  New on `SamLineReader`: `getBytesConsumed()` and `reset()`.

- **SAM, BAM and CRAM file names are recognised in any case.**  `SamReader.Type.hasValidFileExtension` ignores case, and with it `SAMFileWriterFactory.makeWriter` and `makeSAMOrBAMWriter` (`x.SAM` and `x.CRAM` used to be written as BAM), `SamFiles.isSAMFile`/`isBAMFile`/`isCRAMFile`, `BamFileIoUtils.isBamFile`, index discovery in `SamFiles.findIndex` (the `.crai` beside `x.CRAM` is now found), `CramConverter`, and htsget request validation.  The index written beside `x.BAM` is now `x.bai`, as for `x.bam`, rather than `x.BAM.bai`.  URL sniffing in `SamStreams` and the `htsjdk.beta` codecs already ignored case.

### Retained `File` APIs

A small, deliberate set of `java.io.File` APIs remains because they are inherently tied to the local filesystem or ease migration; they do not affect NIO-SPI support:

- `IOUtil.newTempFile`, `IOUtil.getDefaultTmpDir`, `IOUtil.createTempDir` — temporary files/directories are always local.
- `IOUtil.toPath(File)` and `IOUtil.filesToPaths(Collection<File>)` — `File`→`Path` bridge helpers.
- Sixteen `File` overloads that are deprecated in this release and will be removed in a later one.  Each has an identical `Path` overload, which is the replacement:
  - `CRAMFileReader`: the six constructors taking a `File` for the CRAM, the index, or both.
  - `MetricsFile`: `write(File)`, `readBeans(File)`, `readHeaders(File)`, `areMetricsEqual(File, File)` and `areMetricsAndHistogramsEqual(File, File)`.
  - `BlockCompressedInputStream`: the `(File)` and `(File, InflaterFactory)` constructors, `checkTermination(File)`, and `assertNonDefectiveFile(File)` (use `assertNonDefectivePath(Path)`).
  - `CRAIIndex.openCraiFileAsBaiStream(File, SAMSequenceDictionary)`.

The build enforces this list.  Main sources are checked at the bytecode level by the [forbiddenapis](https://github.com/policeman-tools/forbidden-apis) Gradle plugin, which bans `java.io.File` and `Path.toFile()`; each permitted use carries an `@SuppressForbidden` annotation stating why it is needed.

### Bug fixes

- **Gzipped and bgzipped input read from pipes, sockets and URLs is no longer silently truncated** (issue #1691).  htsjdk read such input with `java.util.zip.GZIPInputStream`, which on JDKs affected by [JDK-7036144](https://bugs.openjdk.org/browse/JDK-7036144) stops at a gzip member boundary and reports a clean end of stream whenever `InputStream.available()` returns 0.  Every BGZF file is multi-member, so a bgzipped VCF streamed over HTTP could yield a fraction of its records with no error.  All such reads now go through the new `IOUtil.openGzipOrBgzfStream`, which reads BGZF with `BlockCompressedInputStream` and any other gzip with a decoder that handles concatenated members.  This covers `VCFIteratorBuilder`, `VCFHeaderReader`, plain-gzipped SAM, unindexed `.vcf.gz`/`.bed.gz` via `TribbleIndexedFeatureReader`, gzipped Tribble indexes, CRAI, and everything opened with `IOUtil.openFileForReading` (FASTQ, interval lists, metrics, chain files, FASTA).
- `IOUtil.isGZIPInputStream` now inspects only the gzip header rather than inflating the first byte.  A stream with a valid gzip header but corrupt compressed data is therefore reported as gzip and fails when read, instead of being treated as uncompressed.
- **A tabix index with a sequence that has no records is now written correctly.**  Such a sequence was written without its linear-index count, so an index from `AllRefsTabixIndexCreator` or `TabixIndexMerger` that contained one was rejected by htslib and returned no records from `TabixReader`.  `AllRefsTabixIndexCreator` also accepts a sequence without records between two that have them.
- `VariantContextWriterBuilder` no longer replaces an `IndexCreator` supplied by the caller when the output is block-compressed VCF.
- `TabixReader` rejects a plain-gzip (non-BGZF) file when it is opened, with a message saying so, and names the index files it looked for when none exists.
- `ProcessExecutor.executeAndReturnInterleavedOutput` no longer deadlocks when the child process writes more than a pipe buffer of output.
- `queryUnmapped()` on a CSI-indexed BAM could seek to the wrong place, including into the BAM header: `CSIIndex.getStartOfLastLinearBin` took the `loffset` of whichever bin came last in the index file, where bins are in no particular order and the last may be the metadata pseudo-bin.  It now takes the largest `loffset` among the real bins.
- `SeekableMemoryStream.read(buffer, offset, 0)` returned -1 rather than 0, which made `InputStream.readAllBytes()` on one stop after its first 8 KB.
- `SAMFileWriterFactory.clone()` and its copy constructor now carry over the deflater factory and the SAM flag field format; both were reset to their defaults in the copy.

- **A block-compressed file made by joining others is read to its end.**  Joining BGZF files end to end, as `cat` does and as the spec allows, leaves the empty end-of-file block of each part where the parts meet.  `BlockCompressedInputStream` took such a block for the end of the stream whenever a read began exactly at it (`read` returned -1 and `available()` 0, though a further read would carry on), which truncated the data for any caller that reads a block's worth at a time or a record at a time.  Empty blocks in mid-stream are now passed over, as htslib passes over them.
- **A CRAM 3.1 writer no longer holds about 6 MB of rANS tables for every distinct tag in its records.**  `CompressionHeaderFactory` built a separate rANS encoder and decoder for each tag it met and kept them for the life of the writer, so a file with 20 distinct tags cost over 100 MB of heap, and one with a thousand several gigabytes.  The tags now share one.

### Testing

- The test suite was migrated to `Path`, and a focused `NioSpiCompatibilityTest` exercises the major read/write/index APIs (BAM/CRAM/VCF/FASTQ, reference access and index discovery/creation) against an in-memory jimfs filesystem to validate NIO-SPI compatibility end to end.

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
