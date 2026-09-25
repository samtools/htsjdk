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

- **A CRAM writer checks that every codec it would use exists in the CRAM version it writes, and fails when it is created if one does not.**  `CRAMEncodingStrategy.setCramVersion(CramVersions.CRAM_v3)` on a strategy from the NORMAL, SMALL or ARCHIVE profile used to write a file labelled 3.0 full of CRAM 3.1 codecs; it now fails with a message naming the codec and pointing at the profiles that write CRAM 3.0, `FAST` and the new `NORMAL_3_0`.  `setCramVersion` accepts only 3.0 and 3.1: htsjdk still reads CRAM 2.1 but no longer pretends to write it (it wrote 3.x structures under a 2.1 label).

- **Asking `SAMFileWriterFactory` for `x.sam.gz` now writes bgzipped SAM.**  `makeSAMOrBAMWriter` and `makeWriter` used to treat every name not ending in `.sam` as BAM, so `x.sam.gz` received BAM bytes.  File-name detection also ignores case now, so `x.SAM` and `x.CRAM`, which used to be written as BAM, are written as SAM and CRAM.  See "Bgzipped SAM" below.

- **Text in SAM, BAM and CRAM files is encoded as the SAM specification describes, and a value that would corrupt the file is refused** (issues #1108, #1202).
  - Header text is UTF-8 in all three formats, as the spec allows in `@CO` and in `DS` and `CL` values.  htsjdk wrote SAM and BAM headers one byte per char, so a char above 0xFF lost its high byte (`č` became a carriage return), and read BAM headers as ISO-8859-1.  A header line that is not valid UTF-8, as htsjdk 5.x wrote non-ASCII ones, is read as ISO-8859-1.
  - Read names and Z and A tags stay one byte per char, read and written as ISO-8859-1 in all three formats, so bytes 0x80 to 0xFF that another tool wrote pass through unchanged, as they do in htslib.  CRAM read names were UTF-8, so a non-ASCII read name in a CRAM from htsjdk 5.x now reads as the bytes it was stored as (`é` as `Ã©`).
  - Writing throws an `IllegalArgumentException` that shows the value for a tab, line break or NUL in a header tag name or value (a line break or NUL in `@CO`, where tabs are allowed); for a tab, line break, NUL or char above 0xFF in a read name or Z or A tag written to SAM; and for a NUL or char above 0xFF in one written to BAM or CRAM.  SAM text has no escapes, so such a value split its field or its line (a read group whose description was `x<TAB>PI:123` was read back with a `PI` tag), and BAM and CRAM end these strings with a NUL.  A record read from BAM and written back unchanged is copied as it was read.

- The PQ standard header line type is now `Integer` (was `Float`). Code that compares a header's PQ line against `VCFStandardHeaderLines.getFormatLine("PQ")` will see the new type. Code that reads PQ values from a file whose header still says `Float` is unaffected: the repair preserves the file's declared type.
- `StructuralVariantType` gains a `MIXED` member, which may affect exhaustive switches or serialisation.
- **BCF output is BCF 2.2 with BGZF compression by default.**  A caller that needs raw BCF, or a Tribble `.idx` index over one, selects `setBCFVersion(BCFVersion.BCF_2_1)` on the `VariantContextWriterBuilder`.  With `INDEX_ON_THE_FLY` the writer produces a bare CSI (`x.bcf.csi`) as bcftools does, where earlier releases wrote a Tribble `.idx`; raw BCF 2.1 still gets `.idx`.

- **`BCF2Type`'s integer ranges leave each type's sentinel values unused**, as the BCF specification and htslib have them: INT8 encodes −120..127 (it was −127..127), INT16 −32760..32767 and INT32 −2147483640..2147483647.  The BCF writer therefore stores −127..−121 as INT16 where it stored them as INT8, whose byte for −127 is END_OF_VECTOR to htslib; and the seven values above `Integer.MIN_VALUE` can no longer be written and are refused with a `TribbleException`.  A BCF written by an earlier htsjdk that holds an INT8 −127 (or an INT16 −32767, or an INT32 −2147483647) now reads that value as END_OF_VECTOR, which ends its vector.

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

- **New: `htsjdk.index.BinningIndex`**, one sparse, format-neutral implementation of the binning index that TBI, BAI and CSI share, with a parameterised binning scheme (`min_shift`, depth), a `Builder`, `merge`, and readers and writers for the BAI/TBI layout and for CSI.  It implements `HtsQueryIndex`.  Tabix and BAM indexes are built, merged, read and written through it.  Against 5.x on a 2.4 M-record VCF: index-only queries 242 → 75 ms per 200,000, `VCFFileReader.query` −20%, index open −22%.  A loaded index over 50,000 one-record contigs takes 10 MB instead of 951 MB, because bins are no longer held in dense per-contig arrays.

- **New: CSI indexes for tabix-indexed files** (`TabixIndexType.CSI`).  TBI's fixed scheme stops at 2<sup>29</sup> bases; CSI stores its scheme and reaches further.  `TabixIndex` reads either format by its magic number and writes the type it was built as.  `TabixIndexCreator`, `AllRefsTabixIndexCreator`, `StreamBasedTabixIndexCreator`, `TabixIndexMerger`, `IndexFactory` (`IndexType.CSI`, `createTabixIndex(..., TabixIndexType)`) and `VariantContextWriterBuilder` (`setTabixIndexType`, `setCsiMinShift`) all take the type.  The binning scheme is the one htslib's `tabix -C` chooses, and the index carries the per-contig record counts that `bcftools index -n` reads.  `Tribble.csiIndexPath` and `tabixIndexPath(path, type)` name the index; `TabixUtils.findIndex` finds it.

  ```java
  VariantContextWriter w = new VariantContextWriterBuilder()
          .setOutputPath(Path.of("calls.vcf.gz"))
          .setReferenceDictionary(dictionary)
          .setOption(Options.INDEX_ON_THE_FLY)
          .setTabixIndexType(TabixIndexType.CSI)
          .build();
  ```

- **Indexes have the content that htslib gives them.**  Every BAI, TBI and CSI that htsjdk builds or merges now folds a bin whose chunks span less than 64 KiB of the compressed file into its parent, when the parent has chunks of its own, and gives a linear-index window that no record overlaps the offset of the next window that one does; 5.x kept every bin and gave such a window the offset of the one before, as samtools did before htslib.  A BAI or CSI built for an existing BAM, and a BAI built for an existing bgzipped SAM, is now equal, once read, to the one `samtools index` builds (a CSI for bgzipped SAM differs by design: see "Bgzipped SAM" below), and a TBI or CSI for a tabix-indexed file has the bins and linear index of `tabix`'s; the bytes still differ, because htslib writes a reference's bins in hash-table order.  Queries return what they did, from slightly fewer bins and, where a query starts in a stretch without records, slightly fewer bytes read.

- The test suite cross-checks htsjdk against htslib's `tabix` in both directions, for TBI and CSI; CI installs `tabix` alongside samtools.

- **New: `TabixIndex.getRecordCount(String)` and `getRecordCount()`** (issue #1586) give the number of records on a sequence, and in the whole file, from the counts a TBI or CSI keeps, as `bcftools index -s` and `-n` do, without reading the data.  They are empty for an index that keeps no counts.

- **A TBI index that htsjdk writes carries the record counts tabix writes** (issue #1586): each sequence's count in a metadata pseudo-bin, and a count of records without a position, which is 0.  A TBI written on the fly now equals the one tabix makes of the same file, and `bcftools index -n` / `-s` and `TabixIndex.getRecordCount` can answer from it.  A CSI already carried the per-sequence counts; in memory it now also reports the count of records without a position, as when read back from its file.

### BAM indexing

- **New: CSI indexes for BAM**, for references longer than 512 Mbp, which a BAI cannot address.  `SAMFileWriterFactory.setBamIndexType(BamIndexType)` chooses the index written when index creation is on: `BAI` (the default, unchanged, written as `x.bai`), `CSI` (written as `x.bam.csi`, as samtools names it), or `AUTO`, which writes a CSI only when a sequence in the header is too long for a BAI, so that a pipeline expecting `.bai` files keeps getting them.  `setCsiMinShift` sets the span of the smallest bins (default 14, as for samtools); the rest of the binning scheme is chosen as `samtools index -c` chooses it.  `BAMIndexer` takes the same type, as does `BAMIndexer.createIndex(reader, output, log, indexType)` for indexing an existing BAM.  samtools reads the result, record counts included.

  ```java
  SAMFileWriter w = new SAMFileWriterFactory()
          .setCreateIndex(true)
          .setBamIndexType(BamIndexType.AUTO)
          .makeBAMWriter(header, true, Path.of("out.bam"));
  ```

- `BAMIndexer` now builds through `htsjdk.index.BinningIndex` and writes the index when `finish()` is called, rather than reference by reference.  A placed but unmapped read whose position is the first base of a 16 kb window is now entered in the linear index under that window, as samtools enters it, rather than under the window before; see also "Indexes have the content that htslib gives them" above.  An attempt to index a read beyond 2<sup>29</sup> in a BAI fails with a message pointing at CSI.

- `CRAMBAIIndexer` builds through `BinningIndex` too.  Queries through a `.cram.bai` return what they did; beyond the folding of small bins and the filling of the linear index described above, the bytes differ in two places: the metadata pseudo-bin's offsets, which were a placeholder that nothing read (issue #401) and are now the offsets of the reference's first and last slices, and the linear index, which no longer marks the window after a slice that ends exactly on a 16 kb boundary.  A slice beyond 2<sup>29</sup> fails with a message pointing at CRAI rather than with an array-bounds error.

- **BAI and CSI indexes are read a reference at a time, and held only while there is memory for them.**  Opening an indexed BAM no longer costs anything until a query is made; a query reads the part of the index for its reference sequence, found by skipping over those before it and remembered thereafter.  What has been read is held through soft references and never evicted by htsjdk: with memory to spare a reader ends up with its whole index in memory, and when memory is short the collector reclaims the least recently used parts, across every open reader, which are read again if wanted.  This replaces three readers that each behaved differently: the default BAI reader, which kept nothing and walked the index file again for every query (1,000 queries on one chromosome of a whole-genome index: 64 ms, now 2 ms); the caching BAI reader behind `CACHE_FILE_BASED_INDEXES`; and the CSI reader, which re-read through BGZF for every query (932 ms, now 11 ms).  Index files are no longer memory-mapped, so an index's memory is ordinary heap that the JVM accounts for; the price is about a millisecond on the first query of a large BAI.

- **New: `SamReaderFactory.indexLoading(IndexLoading)`** chooses when the index is read: `LAZY` as above, `EAGER` to read it whole in one pass when first needed, or `AUTO` (the default), which is eager for an index given as a stream, one that is not on the default file system (a single sequential read suits remote storage better than seeks) and one smaller than a megabyte, and lazy otherwise.  Whatever was read stays reclaimable, so a job holding thousands of remote indexes sheds them under pressure rather than failing, and re-reads a reference by range.

- **`SamReaderFactory.Option.CACHE_FILE_BASED_INDEXES` and `DONT_MEMORY_MAP_INDEX` are deprecated and have no effect.**  What the first asked for is now always the case, for CSI and stream-supplied indexes too, and nothing is memory-mapped for the second to prevent.

- **Every BAI and CSI index is browseable.**  `SamReader.Indexing.hasBrowseableIndex()` was true for a BAI only with `CACHE_FILE_BASED_INDEXES`; it is now true for any BAM index and for a CRAM's BAI.  `getIndex()` still returns a `BAMIndex`, but no longer one of `DiskBasedBAMFileIndex`, `CachingBAMFileIndex` or `CSIIndex`, so code that cast the result to those needs `getHtsIndex(BrowseableBAMIndex.class)` or the `BAMIndex` methods instead.  `BamIndexValidator`, which silently checked nothing for a BAI unless index caching was on, now checks any BAI or CSI.

- New in `htsjdk.index`: `ReferenceBinsSource`, the view of a binning index that its readers need, implemented by `BinningIndex` (all in memory) and by the new `FileBackedBinningIndex` described above.

- **The old BAM index readers and index model are deprecated**, for removal in 7.0.0.  `DiskBasedBAMFileIndex`, `CSIIndex` and their base `AbstractBAMFileIndex` still work when constructed directly, with their public methods unchanged, but they now answer through the reader described above: the `useMemoryMapping` / `enableMemoryMapping` constructor arguments have no effect, a query that finds nothing returns an empty span where it used to return `null`, and the protected methods of `AbstractBAMFileIndex` that read the index file are gone, there being no file buffer beneath them any more: `readBytes`, `readInteger`, `readLong`, `skipBytes`, `seek`, `position`, `readChunks`, `skipToSequence`, `setSequenceIndexes`, `verifyIndexMagicNumber`, `initParameters`, `query` and `getQueryResults`, along with `CSIIndex.getQueryResults`.  Only a subclass of `DiskBasedBAMFileIndex` or `CSIIndex` outside htsjdk could have called them, and the last two returned a package-private type; such a subclass will no longer compile.  Also deprecated, since nothing in htsjdk uses them any longer: `BinningIndexBuilder`, `BinningIndexContent`, `LinearIndex`, `BinWithOffset`, and `BAMIndexMerger.mergeBins` / `mergeLinearIndexes`.  `Bin`, `BinList` and `BAMIndexMetaData` are not, because `BrowseableBAMIndex` and `BAMIndex` hand them out.

- `BAMIndexMerger` merges through `BinningIndex.merge`, which now understands part indexes whose empty linear-index windows were left unset, as a BAM part's are.  The merged BAI is the BAI that indexing the whole file gives.  `BAMIndexMerger.processIndex` reads the part's index there and then, so the caller may close it straight away, and rejects a CSI.

- An index written along with a BAM now ends the file's last chunk where `samtools index` ends it. The end of the last record was taken before the final BGZF flush, which names that place as the end of its block; samtools, and an index made by reading the finished file, name it as the start of the next. Only a file whose last record is a placed read was affected, and queries returned the same records either way.

- **`SamIndexes`' methods that open an index as a stream of BAI bytes are deprecated**, for removal in 7.0.0 (issue #1425): `openIndexFileAsBaiOrNull`, `openIndexUrlAsBaiOrNull`, `asBaiStreamOrNull` and `asBaiSeekableStreamOrNull`.  They convert a CRAI to a BAI but not a CSI, which the two that go by file name return as stored, BGZF-compressed, and the two that go by the first bytes take for a CRAI.  Their behaviour is unchanged and now documented.  Ask a `SamReader` for its index, or read one with `FileBackedBinningIndex` (BAI or CSI) or `CRAMCRAIIndexer.readIndex` (CRAI).  The `SamIndexes` constants and `getSAMIndexTypeFromStream` are not deprecated.

### Bgzipped SAM

- **New: `SAMFileWriterFactory` writes BGZF-compressed SAM** when the output name is `.sam` followed by `.gz`, `.gzip`, `.bgz` or `.bgzf`, in any case, through `makeWriter`, `makeSAMOrBAMWriter` or `makeSAMWriter`.  The factory's compression level and deflater factory apply, the file ends with the BGZF end-of-file block, and an MD5 file, if requested, is of the compressed bytes.  samtools and `SamReaderFactory` read the result.

- **New: on-the-fly indexing of bgzipped SAM.**  `SAMFileWriterFactory.setCreateIndex(true)` writing `x.sam.gz` from coordinate-sorted records to a regular-file `Path` writes an index beside it, as it does for a BAM.  The default is CSI (`x.sam.gz.csi`), which is what samtools writes on the fly; `setSamIndexType(BamIndexType)` chooses BAI (`x.sam.gz.bai`) or AUTO.  A CSI for SAM text always carries a tabix header naming every sequence of the header, so samtools and tabix both read it.  `setCsiMinShift` applies to both SAM and BAM CSI indexes.  The `.sam.gz` itself is byte-for-byte what it is without an index.

- **New: region queries on bgzipped SAM.**  A `.sam.gz` opened from a `Path` is queried through an index just as a BAM is: `query`, `queryOverlapping`, `queryContained`, `queryAlignmentStart`, `queryUnmapped`, `queryMate`, and iteration over a file span.  The index is found beside the file (`x.sam.gz.bai`, then `x.sam.gz.csi`, then `x.sam.gz.tbi`) or named through `SamInputResource.index(...)`, and may be any of the four kinds the two tools write: the BAI or CSI of `samtools index`, which number references as the header lists them, or the TBI or CSI of `tabix -p sam`, which number them in the order the file meets them and name them.  The second kind is asked by name.  That matters because samtools itself reads a tabix-made CSI by header ordinal, and silently returns some other reference's reads, or none, whenever a sequence early in the header has no reads.  An index that fits neither reading (a different number of references than the header has, a tabix index for a format other than SAM, or one naming a sequence the header lacks) is refused.  Such a reader can also be iterated more than once, which no SAM reader could be.  Not covered: bgzipped SAM opened from a `SeekableStream` or a URL, where the factory goes by the source's name and does not yet know `.sam.gz`.

- **New: indexing an existing bgzipped SAM file.**  `BAMIndexer.createIndex(reader, output)` (or with `BamIndexType.CSI`) works for a `.sam.gz` reader opened with `INCLUDE_SOURCE_IN_RECORDS`, because records of bgzipped SAM now say where in the file they lie, as records of a BAM do.  A BAI built this way is equal, once read, to the one `samtools index` builds for the same file.  A CSI written this way also carries a tabix header naming every `@SQ` of the header in order, which makes the two tools' numberings one: samtools and tabix both read it correctly, which is true of neither tool's own CSI.  Reading a `.sam.gz` costs what it did (+0.2% on a million records), and +0.8% with `INCLUDE_SOURCE_IN_RECORDS`, which used to attach an empty source to a SAM record and now builds its file pointer.

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

### VCF

- **BCF output is now BCF 2.2 with BGZF compression by default**, the form bcftools writes and reads natively.  `VariantContextWriterBuilder.setBCFVersion(BCFVersion)` selects the container version, and `BCFVersion.BCF_2_1` still writes the raw format earlier htsjdk versions read.  The embedded header text carries `IDX=` on every FILTER, INFO, FORMAT and contig line, with the indices htslib would assign (an `IDX` already on a line is honoured), the flag encoding is `BCF_BT_NULL` (matching the spec and htslib), INFO and FORMAT vectors are sized from the values present rather than from the header `Number`, a shorter genotype is padded with END_OF_VECTOR in 2.2 (MISSING in 2.1), the GT integer width follows the site's allele count (a site with more than 63 alleles uses INT16 or INT32), string lists in 2.2 are written without a leading comma, a missing FORMAT string is `.` in 2.2, and String values are percent-encoded element by element under a VCF 4.3 or later header.  Genotype bytes read from a BCF pass through unchanged only when the BCF version, the dictionary and the percent-encoding rule all match; otherwise they are decoded and re-encoded.  A genotype with a leading phase indicator (`|0/1`) is allowed from VCF 4.4.  A BGZF BCF written with `INDEX_ON_THE_FLY` produces a bare CSI index (`x.bcf.csi`), as bcftools does; `VCFFileReader` opens a BGZF BCF through the new `BCFFileReader`, which answers region queries from an htsjdk or bcftools CSI beside the file.  `BCF2Writer` is public for callers that want raw BCF at either version without BGZF framing.
- **BCF 2.1 and 2.2 files are read, both raw and BGZF-compressed** (issue #946).  `TribbleIndexedFeatureReader` now recognises gzip and BGZF input from its bytes rather than its file name, so a BGZF BCF named `.bcf` opens through `VCFFileReader`, `AbstractFeatureReader` and `VCFIteratorBuilder` whatever the extension (bcftools output, `-Ob` and `-Ou` alike, is BGZF), any gzipped Tribble file without a `.gz` extension reads correctly, and a file named `.gz` that is not compressed reads as plain text.  `BCF2Codec` reads decompressed bytes like every other Tribble codec and refuses a compressed stream with a message saying to decompress it first (with `IOUtil.openGzipOrBgzfStream`, for example); so a Tribble index cannot be built over a BGZF BCF.  An error in a BCF record cites its CHROM and POS once they are decoded, as htslib does, rather than a record number that counted every record the codec had decoded.  The BCF dictionaries are built as htslib builds them: a header line takes the index its `IDX` attribute names, a line without one the next index after the highest so far, `PASS` is always 0, an ID seen twice keeps its first index, and only two IDs naming one index is an error; so files with sparse indices (from `bcftools annotate -x`, for example) and headers that mix lines with and without `IDX` decode correctly.  The header exposed to callers carries no `IDX`.  END_OF_VECTOR ends every typed vector, so mixed-ploidy genotypes and ragged FORMAT arrays from htslib decode correctly.  Values come back as the VCF reader would hold them: an interior missing value in a generic INFO or FORMAT vector is null, in `AD` or `PL` it makes the field absent; a FORMAT String of `.` is missing; an INFO String with commas is a list; and from VCF 4.3, String values are percent-decoded.  The n_sample field uses its 24 bits.  BCF record strings are decoded as UTF-8.  The first allele's phase bit is ignored below VCF 4.4 and taken literally from 4.4 as the leading indicator of a diploid or polyploid GT; a haploid call is unphased at every version, as the text reader reads `1`.  BCF versions other than 2.1 and 2.2 are rejected with a clear message.  `BCF2Codec.ALLOWED_MINOR_VERSION`, `ALLOWED_BCF_VERSION`, `validateVersionCompatibility` and `error(String)` are deprecated: nothing is gated on them any more.  The codec keeps no per-record state, so records may be decoded on several threads at once.
- **A `VCFHeader` keeps the version it declares.**  A fileformat line among the lines a header is built from now sets its version instead of being discarded, `addMetaDataLine` of a fileformat line does the same, the copy constructor copies it, and `getMetaDataInInputOrder`/`getMetaDataInSortedOrder` lead with the declared version rather than with 4.2 (or 4.3 for a 4.3 header).  So `new VCFHeader(in.getMetaDataInInputOrder(), samples)` keeps the input's version, which it used to lose.  A header that declares no version reads back as null from `getVCFHeaderVersion()`, its lines carry no fileformat line (they used to lead with 4.2), and a header rebuilt from them declares none either.  When the lines a header is built from carry several fileformat lines, as lines gathered from several headers may, the highest version wins whatever their order.  `setVCFHeaderVersion` accepts any version after any other, and `VCFHeader.validateVersionTransition` is gone: the version is a label.  The VCF and BCF writers still write every header as 4.2, and no longer refuse one that declares 4.3 or later: headers now keep that version through copies and merges, so refusing it would refuse nearly everything derived from a 4.3 input, which used to be written as 4.2 precisely because the copy had lost its version.
- **Merging headers takes the highest version among them** (`VCFUtils.smartMergeHeaders`), where it used to refuse to merge a 4.3 header with any other version.  The merged lines lead with that version's fileformat line so a header built from them keeps it.  Where two inputs define the same INFO or FORMAT field alike but differ in `Source`, `Version` or another attribute, the merge used to keep the first silently.  It now keeps the line with the later `Version` when both give one for the same `Source` (compared without regard to case, and versions as a person reads them, so `1.10` follows `1.9` and `151` follows `99`), and otherwise the first; either way the line is taken whole and the choice is reported with the other merge warnings.
- **INFO and FORMAT header lines keep every attribute, in any order.**  `VCFCompoundHeaderLine` used to keep only `ID`, `Number`, `Type`, `Description`, `Source` and `Version` and drop anything else (an `IDX`, a tool's own key), and the parser rejected a line whose attributes were not in that order.  Any attribute now survives a round trip, in its original order after the standard ones, and is reachable through the new `getGenericFieldValue(tag)` and `getGenericFields()`, which mirror `VCFSimpleHeaderLine`.  Attribute order is not enforced for any structured line, as VCF 4.4 requires of parsers; a compound line without `ID`, `Number` or `Type` is still rejected.  The parser also now takes only the first `=` of an attribute as its separator and treats a `"` inside an unquoted value as a literal, so an ID such as the hts-specs corpus's ``complexcustomcontig!"#$%&'()*+-./;=?@[\]^_`{|}~`` reads and writes back correctly; IDs may contain `=`.  What a quoted value holds is kept exactly, whitespace at its ends included, and written back with every backslash and double quote escaped as the specification requires, so any value round-trips: one starting with a quote, holding `\\`, or ending in a backslash, which used to produce a header that could not be read back.  A value is taken as it is given, not as text already escaped, so a caller that escaped quotes itself must stop doing so.  A standard INFO or FORMAT line that gets repaired keeps its `Source`, `Version` and any other attribute, where repair used to replace the whole line.  An `ID` is never written quoted, since other tools take the quotes to be part of it.  The parser stays lenient where it can read a line without guessing: a double quote that a writer forgot to escape inside a quoted value is kept as part of the value (it used to be dropped), angle brackets outside quotes are ignored, as is whitespace around the line, and a line without its closing bracket still yields its last attribute.  `VCFCompoundHeaderLine.equals` now takes `Source` and `Version` into account, as `hashCode` always did.
- **`Number=P`, `LA`, `LR`, `LG` and `M` are understood** on INFO and FORMAT header lines: `VCFHeaderLineCount` gains the five values (VCF 4.4 defines `P`, VCF 4.5 the rest).  A header using one of them used to fail with a `NumberFormatException`, so a file declaring `PSL` or `LAD` could not be read at all.  They are read whatever version the header declares and written back as declared.  Their counts depend on each sample's GT or LAA, so `VCFCompoundHeaderLine.getCount(vc)` returns -1 for them as it does for `Number=.`, the new `VCFHeaderLineCount.variesBySample()` picks them out, and the BCF writer sizes such fields from their values.  A `Number` that is neither one of the codes nor an integer is now reported as an invalid header (`TribbleException.InvalidHeader`) where it used to escape as a `NumberFormatException`, and a header line older than VCF 4.0 may give `.` as well as `-1` for an unbounded count.
- **Genotypes keep a phase for each allele where one flag is not enough.**  VCF 4.4 gives every allele of a GT its own phase, the first through an optional leading indicator (`|0/1`).  A GT with mixed separators (`0/1|2`), valid in every version, used to be read as wholly phased and written back as `0|1|2`; it now round-trips through VCF and BCF.  `Genotype.isAllelePhased(int)` reports each allele's phase, `hasPerAllelePhasing()` says whether the genotype needed more than `isPhased()`, and `GenotypeBuilder.allelePhasing(boolean[])` builds one.  Nothing changes for any other genotype: `isPhased()` is true if any allele is phased, as it has been for VCF (a mixed GT read from BCF used to report its second allele's phase), a leading indicator that only repeats what the separators imply is dropped, and a haploid GT written without an indicator still reports unphased.  Writing a genotype whose first-allele phase the output cannot express (VCF before 4.4, BCF 2.1) throws rather than silently changing it, and leaves nothing of the record in the output; `VCFEncoder.writeGtField` has an overload that allows it.  Genotypes a VCF writer passes through undecoded are written as they were read, as before.  A copy of a genotype with per-allele phases that is given another ploidy (`new GenotypeBuilder(g).alleles(...)`) keeps only whether it was phased.

- `VCFStandardHeaderLines` carries every reserved key from VCF 4.4 and 4.5: the tandem-repeat INFO keys (SVCLAIM, RN, RUS, RUL, RUC, RB, CIRB, CIRUC, RUB), IMPRECISE, NOVEL, EVENTTYPE, INFO AD/ADF/ADR, FORMAT ADF/ADR (reserved since 4.3), H2, H3, the local-allele FORMAT keys (LAA, LAD, LADF, LADR, LEC, LGL, LGP, LPL, LPP), PSL, PSO, PSQ, PP, LEN, FORMAT MQ, FORMAT CICN, CNQ, CNL, CNP, NQ, HAP and AHAP. Keys whose Number or Type changed between versions (SVLEN, CIPOS, CIEND, CILEN, HOMLEN, HOMSEQ, BKPTID, MEINFO, METRANS, MATEID, PARID, EVENT, DGVID, DBVARID, DBRIPID, INFO/FORMAT CN, INFO CICN) have constants but no standard header line, so `repairStandardHeaderLines` does not rewrite a version-appropriate definition. Every INFO and FORMAT key constant is in one of the new nested classes `VCFConstants.INFO` and `VCFConstants.FORMAT`, named for what its key holds: `VCFConstants.INFO.CONFIDENCE_INTERVAL_AROUND_POS` is `CIPOS`, `VCFConstants.FORMAT.GENOTYPE` is `GT`, and a key defined for both INFO and FORMAT, such as `DP`, has a constant in each. The top-level names of the keys 5.0.0 had (`GENOTYPE_KEY`, `DEPTH_KEY` and the rest) remain as deprecated aliases, still compile-time constants, and each one's Javadoc names its replacement; `OLD_DEPTH_KEY` (`RD`, which the specification does not reserve and htsjdk does not use) is deprecated without one.
- `StructuralVariantType` gains `MIXED` and a non-throwing `parse` method that extracts the major type from `DEL:ME:ALU` or `<DEL:ME:ALU>`, with a `parse(byte[])` overload that allocates nothing. `StructuralVariantAllele` is a new immutable value type that carries the major type and the ordered subtype list, and says through `isSymbolic()` and `isBreakend()` which kind of allele it describes; `Allele.asStructuralVariant()` returns one for symbolic SV and breakend alleles. `VariantContext.getStructuralVariantType()` consults both `SVTYPE` and the alleles: no SV alleles returns null, one major type returns it, more than one or an SVTYPE-vs-allele disagreement returns `MIXED`.
- **A record's end is worked out as htslib works it out** (`VariantContext.getEnd()` for a record read from VCF): the furthest of the last base of the REF allele, INFO `END`, `POS + SVLEN` for a `<DEL>`, `<DUP>`, `<CNV>` or `<INV>` allele (subtypes such as `<DEL:ME>` included, each allele with its own `SVLEN`, taken as an absolute value) and, for a reference block (`<*>` or `<NON_REF>`) without `END`, `POS + LEN - 1` over the samples' FORMAT `LEN` (VCF 4.5).  The reader used to take `END` when present and the REF allele otherwise, so a `<DEL>` with an `SVLEN` but no `END` spanned one base, and was indexed and written to BCF (`rlen`) as such.  An `END` of `.` or before `POS` is now ignored rather than rejected, as htslib ignores it, and `VariantContext` only rejects an `END` past `getEnd()` where it used to demand equality, so a stale `END` that is short of the alleles no longer makes a file unreadable.  `LEN` is only consulted when the header declares it and `END` is absent, because reading it decodes the genotypes: a valid reference block with `LEN` carries an `END` of the same value, and the variant records of a gVCF carry `<NON_REF>` without `END`.  Where `LEN` is consulted, a malformed sample value is reported when the record is decoded rather than when a genotype is first asked for.  A record decoded for its location alone (`decodeLoc`, which index building uses) gets the same end, and now carries its genotypes to be decoded on demand like any other.  `isReferenceBlock()` is true of any record whose single alternate is `<*>` or `<NON_REF>`, whether its span comes from `END`, from `LEN` or is the REF base alone; it used to require `END`.  `VCFConstants` gains `INFO.STRUCTURAL_VARIANT_LENGTH` and `FORMAT.REFERENCE_BLOCK_LENGTH`.
- **VCF 3.2 through 4.5 are read by default.**  `VCFCodec` and `VCFFileReader` now accept every version from VCF 3.2 through VCF 4.5 without any system property; unknown versions (such as a future 4.6) are still rejected.  The `samjdk.optimistic_vcf_4_4` system property and the `Defaults.OPTIMISTIC_VCF_4_4` field are removed: code that references them will not compile.  The `testWithOptimisticVCF4_4` Gradle test task and the `optimistic_vcf_4_4` TestNG group are removed.  `VCF3Codec` is deprecated; it is now a trivial subclass of `VCFCodec` kept for source compatibility, and does not take part in codec discovery.
- `VCFHeaderVersion` gains `VCF4_5`.  Header lines of every version are parsed by the same parser, so a new version can no longer hit a missing parser.
- **The VCF codecs are thread-safe once their header is read** (issue #1026).  `VariantContext`s from a VCF reader decode their lazily parsed genotypes on whichever thread first asks for them, and that may now happen on several threads at once, and while the reader keeps reading; `AbstractVCFCodec.decode` may also be called concurrently on one codec.  Previously the codec kept per-record scratch state in fields, so genotypes decoded concurrently leaked between records.  Reading or setting the header remains single-threaded.  For subclasses, `parseFilters` now takes the record's line number, the scratch fields (`parts`, `genotypeParts`, `alleleMap`, `lineNo`, `filterHash`) are gone, and the `stringCache` field is private: intern strings through `getCachedString`, which is safe to call from any thread.
- Errors found while decoding genotypes lazily now cite the record's own line number rather than wherever the reader had got to.
- **The VCF and BCF writers write every VCF version from 4.0 through 4.5.**  The output version is the builder's (`VariantContextWriterBuilder.setVCFVersion`, which rejects a version before 4.0), else the header's with a floor of 4.2, else 4.2; it labels the `##fileformat` line of a VCF and of the header text embedded in a BCF, and the VCF encoder follows it.  The writer works on a copy of the header labelled with the output version; the caller's header is not changed.  At `writeHeader` time, a header holding a line that needs a newer version than the output (`VCFHeaderLine.minimumVersion`, which for INFO and FORMAT lines is their `Number=` code's `VCFHeaderLineCount.minimumVersion`) is rejected with a message quoting the output version, the offending lines and the fix.  Per record, a genotype whose first-allele phase the output version cannot express is refused.  For VCF 4.3 and later, INFO and FORMAT `String` and `Character` values in record bodies are percent-encoded (the eight characters the spec names), each element of a list on its own so that the commas between elements stay delimiters; the common path where nothing needs encoding is allocation-free.  `VariantContext.calcVCFGenotypeKeys` places the FORMAT key `LAA` immediately after `GT`, before all other keys, whatever the version, so the VCF and BCF writers both write it there.  `VCFHeaderVersion` gains `isOlderThan`, `percentEncodesText` and `leadingPhaseAllowed`; `VCFCompoundHeaderLine` gains `getLineType`.  `VCFRecordCodec` (used by `SortingCollection`) now encodes and decodes with the header's version, so a 4.4 header preserves leading phase indicators through spill/reload.  Genotypes still held as the text they were read from are written as they are only when the source and the output are both before 4.3 or both 4.3 or later and the output can express a leading phase indicator if the source could; otherwise they are decoded and re-encoded.  `VCFConstants` gains `FORMAT.LOCAL_ALTERNATE_ALLELES`.
- **VCF and GFF3 files are read and written as UTF-8, and so is every Tribble text format read through the shared line readers.**  `AsciiLineReader` is renamed to `Utf8LineReader` and `AsciiLineReaderIterator` to `Utf8LineReaderIterator`; the old names remain as deprecated subclasses.  `Utf8LineReader`, `BlockCompressedInputStream.readLine`, `SynchronousLineReader`, `TabixReader.readLine` and `VCFRecordCodec` decode UTF-8 where they widened raw bytes to chars (Latin-1) or used the platform default charset; the VCF writer, the VCF header text embedded in a BCF and `Gff3Writer` encode UTF-8 where they wrote ISO-8859-1 or the platform default (`VCFEncoder.VCF_CHARSET` is now `UTF_8`).  `TabixReader.readLine` now strips a trailing CR before the newline, so CRLF files queried by region yield clean lines.  Non-ASCII text in header descriptions, sample names, INFO and FORMAT string values now round-trips correctly.  Invalid UTF-8 sequences in older Latin-1 files decode to U+FFFD (the Unicode replacement character) and are therefore lossy on rewrite.

### Bug fixes

- The standard FORMAT line for PQ is `Number=1, Type=Integer`, matching VCF 4.3 through 4.5 (issue #751; it was `Type=Float`). `repairStandardHeaderLines` no longer changes the type of an existing header line: when the declared type differs from the standard's, the entire line is left as declared (Type, Number and Description), because correcting Number next to a foreign type can yield an invalid line. When types agree, Number, count type and Description are corrected as before.
- `VariantContext.getStructuralVariantType()` no longer throws `IllegalArgumentException` for an `SVTYPE` like `DEL:ME`; it returns `DEL`.
- `BinningIndex` now accepts a CSI index with `depth=0` (one bin level), which bcftools writes for files whose longest contig is shorter than 2^14 bases.
- The BCF writer sizes string vectors by their UTF-8 byte length rather than Java's char count, so a non-ASCII string (such as a description containing accented or CJK characters) is no longer silently truncated when written to BCF.
- **The BCF writer keeps an interior missing value in an INFO or FORMAT vector of integers, floats or strings.**  `X=10,.,5` was written as `X=10,5`, `S=a,.,c` as `S=a,c`, and an INFO integer list with a missing value threw a `NullPointerException`; fully decoding a `VariantContext` whose String list holds a missing element threw too.
- A `Type=Flag` header line with a `Number` other than 0 (`Number=A`, for example) no longer makes the BCF writer throw: the line's count is normalised to a fixed zero, as the VCF specification requires.
- The BCF writer masks n_sample to 24 bits, as the specification and the reader do; it used 20.
- **BGZF-compressed BCF files can now be read** (issue #946).  The BCF reader previously looked for the BCF magic bytes at the very start of the file, but a BGZF-compressed file starts with the gzip magic, so all bcftools-produced BCF files were silently rejected.
- **A missing value inside a BCF vector no longer shifts the values after it.**  A generic INFO or FORMAT vector such as `X=10,.,5` came back from BCF as `[10, 5]`; it now comes back as `[10, null, 5]`.  `AD` and `PL` are held as `int[]`, which cannot hold a missing value, so `AD=10,.,5` is absent, as it is when read from VCF, where it used to come back as `[10]`.
- **The BCF writer sets the first allele's phase bit as htslib does**: the phase the other alleles imply, and set for every haploid call.  It always cleared the bit, so a reader that honours it, as bcftools does and as htsjdk now does under a VCF 4.4 or later header, read a `0|1` written under a 4.4 header as `/0|1`.
- **Mixed-ploidy BCF genotypes from htslib no longer throw.**  htslib pads shorter genotypes with END_OF_VECTOR, which htsjdk decoded as data values, producing an `ArrayIndexOutOfBoundsException` or garbled alleles.
- `BCF2Encoder` encodes strings as explicit UTF-8 rather than using the platform default charset.
- `VCFFileReader` can now open VCF 3.2 and 3.3 files, which previously required constructing a `VCF3Codec` by hand and using `AbstractFeatureReader` directly.  A 3.x record with `FILTER=PASS` now decodes as passing, where the old `VCF3Codec` treated it as a named filter.
- **Spaces in INFO values are no longer rejected** (issue #1667).  The VCF reader used to throw on any whitespace in the INFO column for every version, but VCF 4.3+ explicitly allows spaces in values, and htslib has never enforced the restriction.  Tabs still delimit VCF columns as before: a record with more columns than its header declares is reported as such, where the extra column used to be reported as whitespace in INFO.
- **A tabix-indexed file whose last line has no trailing newline no longer loses that line when read through `TabixReader`.**  `TabixReader.readLine` returned null at the end of the stream whatever it had read, so a query that reached such a last record dropped it; the line is now returned, as htslib returns it.
- `VCFUtils.smartMergeHeaders` promotes a field defined as `Integer` in one header and `Float` in another to `Float` whichever comes first.  With the `Integer` definition first it reported the promotion and kept `Integer`, so the merged header depended on the order of its inputs.
- A malformed VCF genotype no longer escapes the codec as an `ArrayIndexOutOfBoundsException` (a sample with more values than FORMAT keys, in a file with more than eight samples) or a `NumberFormatException` (a non-numeric `DP` or `GQ`); both are reported as `TribbleException`s naming the sample, the key and the position.
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
- **A rANS encoder or decoder builds its order-1 tables the first time it codes an order-1 stream**, not when it is constructed.  The tables are about 2.6 MB in an encoder and 2.9 MB in a decoder, and were paid for by every codec, including the decoders of a CRAM writer and the encoders of a CRAM reader, which are never used.  A codec that only ever sees order-0 data now holds about 12 KB.

- **A BAM or bgzipped SAM writer that cannot build or write its index fails the whole write.**  An indexing failure while writing a record is remembered, further writes are refused, and `close()` throws the original failure after closing the data stream and removing the partial index.  The factory no longer leaves the data output stream open when the index cannot be opened.

- **Reading a BAI or TBI whose metadata pseudo-bin has the wrong number of chunks now says the reference is probably longer than 2^29 and suggests a CSI index** (issues #823, #643).

- **A CRAM opened by `Path` now reads through a buffered stream**, like one opened from a `SeekableStream`; container and slice headers are parsed a few bytes at a time, so an unbuffered read made a syscall per field (322 per small query, now 3), which was very slow on network filesystems such as GPFS or NFS (issue #1756).

- **A Tribble index (`.idx`) with a byte of 0x80 or above in a sequence name, path or property is read instead of failing with `EOFException`** (issue #1614).  `LittleEndianInputStream.readString` cast each byte to a signed `byte` before testing for the end of the stream, so every such byte looked like it.  It now decodes the string as UTF-8, and `LittleEndianOutputStream.writeString` writes UTF-8 to match; it wrote the low byte of each character, which lost everything outside Latin-1.

- `SeekablePathStream.read()` no longer returns 0 when the underlying channel reads no bytes, as a channel may, which a caller took for a byte of zero; it retries, as `read(byte[], int, int)` already did (issue #1399).

- **A mapped read without bases (SEQ `*`, such as a minimap2 secondary alignment) keeps its CIGAR, NM and MD through a CRAM that htsjdk writes.**  htsjdk 5.0.0 read such a read back from its own CRAM with the CIGAR `0M` and without NM or MD, whatever it had been; samtools read the same files correctly.  The writer now stores these reads as htslib does, and CRAMs written by htsjdk 4.x and 5.0.0 read correctly again.
- Reading CRAM matches samtools in three smaller details: MD for a read that runs off the end of the reference was empty; htslib's internal `cF` tag appeared on unmapped records; and the PNEXT of an unpaired read was dropped when writing and cleared when reading.  Such a read has no RNEXT, as in htslib, so `ValidationStringency.STRICT` rejects it as it rejects the same record in SAM or BAM; that error now reports the PNEXT, where it reported the read's own position.
- **htsjdk 4.x can read the CRAMs htsjdk writes again.**  With libdeflate loaded, the default, htsjdk 5.0.0 wrote an empty GZIP block (a GZIP-compressed data series or tag with no values in a slice) as a gzip member with no DEFLATE data inside, which `java.util.zip.GZIPInputStream`, and so htsjdk 4.x, rejects with `invalid stored block lengths`; htslib reads such a block as empty.  Empty blocks are now written RAW, as htslib writes them, whatever their data series' codec (issue #1633), and the libdeflate deflater writes a valid stream for empty input, as `java.util.zip.Deflater` does.
- **A CRAM labelled 3.0 no longer contains CRAM 3.1 codecs.**  The `FAST` profile, which writes CRAM 3.0, compressed tags with rANS Nx16, a CRAM 3.1 codec.  Each profile now sets the codecs tried on tags (`CRAMEncodingStrategy.setTagCompressorCandidates`), rANS 4x8 for the CRAM 3.0 profiles.  The new `NORMAL_3_0` profile writes CRAM 3.0 with the codecs htsjdk 4.x used, rANS 4x8 and GZIP, for readers without CRAM 3.1 support: on a 1.6M-read exome BAM it writes 7% smaller files than htsjdk 4.3.0 did, in under half the time, and htsjdk 4.1.3 reads them.
- **A CIGAR too long for a BAM record is stored in a `CG:B:I` tag, as the SAM specification requires** (issue #1560).  htsjdk wrote `CG:B:i`, which older samtools releases reject.
- **Bytes 0x80 to 0xFF in CRAM read names and tags are kept.**  A CRAM 3.1 read name went through the name tokeniser as UTF-8 and ASCII, so such a byte came back as `?`; Z tags were written as ASCII, with the same result; and an A tag above 0x7F, in BAM as in CRAM, was read as a char near 0xFFFF.

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
