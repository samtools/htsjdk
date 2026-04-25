# htsjdk 5.0.0 release benchmark

Just-enough performance benchmarks to back up the perf claims in the v5.0.0 CHANGELOG / release notes. The goal is **a small number of honest, reproducible numbers**, not exhaustive performance engineering.

## What we measure

For each combination of *variant* × *test* we record wall-clock time and (for write tests) the size of the output file.

### Tests

| Test | Driver | Why it's here |
|---|---|---|
| `bam-read` | Picard `CollectQualityYieldMetrics I=$BAM`. **samtools is intentionally skipped** -- there's no like-for-like samtools equivalent of CQM, so any cross-tool comparison would be apples-to-oranges. | end-to-end realistic, read-heavy. Substantiates the "BAM read is faster in 5.0" claim. CQM (not CIM) is used so we don't pay R-driven PDF-render overhead. |
| `bam-write` | Picard `SamFormatConverter I=$BAM O=...bam`. **samtools is intentionally skipped** -- same reasoning as bam-read. | exercises the BAM compression path. Substantiates the jlibdeflate-vs-zlib-vs-IntelDeflater story. |
| `cram-read` | Picard `CollectQualityYieldMetrics I=$CRAM R=$REF` vs `samtools view --reference $REF $CRAM > /dev/null` | substantiates the CRAM read speed-ups. |
| `bam-to-cram-fast` | Picard `SamFormatConverter I=$BAM O=...cram R=$REF [--CRAM_PROFILE FAST]`. samtools: `samtools view -C --output-fmt-option version=3.0`. | apples-to-apples 4.3 (CRAM 3.0) vs 5.0 (FAST profile, ≈ 3.0 codec choices). |
| `bam-to-cram-normal` | same with `--CRAM_PROFILE NORMAL`. samtools: `--output-fmt-option version=3.1`. Skipped for `picard-4.3*` (4.3 cannot write 3.1). | the user-default 3.1 path. |

### Variants

Variants are Picard fat jars dropped into `$PICARD_DIR` (default `/tmp/picard-bench`). The basename of each jar is the variant name. The suffix encodes which DEFLATE implementation is exercised — pick the suffix to match the deflater you want to measure, **not** Picard's "default" behavior:

| Suffix | Meaning | Picard CLI flags | JVM props | Notes |
|---|---|---|---|---|
| `-zlib` | JDK built-in `java.util.zip.Deflater` | `USE_JDK_DEFLATER=true USE_JDK_INFLATER=true` | `-Dsamjdk.use_libdeflate=false` (only matters in 5.0) | The plain-Java reference path. Available on every htsjdk version and every architecture. |
| `-libdef` | jlibdeflate (libdeflate via JNI) | `USE_JDK_DEFLATER=true USE_JDK_INFLATER=true` | (none) | htsjdk's default in 5.0; only meaningful for `picard-5.0-*`. The flags bypass Picard's `IntelDeflaterFactory` so htsjdk picks its own default (libdeflate). |
| `-intel` | Intel GKL `IntelDeflater` / `IntelInflater` | (none -- Picard's `IntelDeflaterFactory` engages by default) | (none) | x86-only. On aarch64 the native fails to load and Picard's factory falls back to JDK zlib **via a Picard wrapper** that's noticeably slower than the direct `-zlib` path -- so `-intel` on aarch64 is not equivalent to `-zlib`. Skip on aarch64 unless you specifically want to measure that wrapper overhead. |

> ⚠️ **Picard's `IntelDeflaterFactory` is what registers a default Deflater with htsjdk.** When its native fails to load it explicitly falls back to **JDK zlib**, NOT to htsjdk's default. So Picard "out of the box" on aarch64 with htsjdk 5.0 will run JDK zlib via Picard's wrapper, **not** jlibdeflate. To engage jlibdeflate (or to measure JDK zlib without the wrapper overhead), use the `-libdef` or `-zlib` variants.

> ⚠️ **Confirm libdeflate is engaged** via the htsjdk log line `INFO  DeflaterFactory  libdeflate is available; using libdeflate for DEFLATE compression.` It only prints when `-libdef` is the variant in use.

samtools is used as a fifth variant for the cross-implementation comparison rows.

### Suggested variants for AWS

**x86** (5 picard variants + samtools on CRAM tests): `picard-4.3-zlib`, `picard-4.3-intel`, `picard-5.0-zlib`, `picard-5.0-libdef`, `picard-5.0-intel`, `samtools`. Covers all three real deflater paths (zlib / libdef / intel) on both htsjdk versions.

**Graviton (aarch64)** (4 picard variants + samtools on CRAM tests): `picard-4.3-zlib`, `picard-5.0-zlib`, `picard-5.0-libdef`, `samtools`. Skip the `-intel` variants on aarch64 -- the Intel native isn't available, and the only thing they measure is the cost of Picard's wrapper around JDK zlib, which is a Picard-internal artifact rather than an htsjdk perf characteristic.

## Methodology

- **No warmup runs.** Inputs are cat'd to `/dev/null` once at script start to warm the OS page cache.
- **No per-iteration cold-cache flushing.** AWS will run on instance NVMe with no other load; the laptop is for smoke-testing only.
- **3 iterations per cell, median reported.**
- **Wall-clock time** via `/usr/bin/time -l` (BSD/macOS) or `-v` (GNU/Linux). Peak RSS is also captured but not reported in the table by default.
- **Sequential runs only**, never parallel cells.
- **JDK 17** from `pixi` (Temurin/Zulu, depending on platform). samtools also from the pixi env (conda-forge/bioconda).

## Files

| File | Purpose |
|---|---|
| `pixi.toml` | Pinned tool environment (samtools + JDK 17). |
| `run.sh` | The harness. Parameterized over inputs, variant set, and test set. |
| `summarize.sh` | Aggregates `out/results.tsv` into a markdown table. |
| `out/results.tsv` | Raw per-iteration measurements (one row per run). |
| `out/logs/` | Per-iteration stdout/stderr and `/usr/bin/time` output. |
| `out/*.metrics.txt`, `*.recompressed.bam`, `*.cram` | Output artifacts; sizes are recorded in `results.tsv`. |
| `results-laptop.md`, `results-graviton.md`, `results-x86.md` | Final, committed summary tables (one per platform). |

## Smoke test (laptop)

Inputs:
- BAM:       `/Users/tfenne/work/public-data/1kg/HG03953.0.1x.bam` (~5M reads)
- CRAM:      `/Users/tfenne/work/public-data/1kg/HG03953.0.1x.cram`
- Reference: `/Users/tfenne/work/public-data/1kg/GRCh38_full_analysis_set_plus_decoy_hla.fa.gz`

Sequence:

```bash
cd benchmarks/perf_5.0.0
pixi install                       # one-time; brings in JDK 17 + samtools

# user drops fat jars into /tmp/picard-bench, e.g.
#   picard-4.3-default.jar   (latest released Picard, depends on htsjdk 4.3.x)
#   picard-4.3-jdk.jar       (same jar -- the "-jdk" suffix only changes runtime flags)
#   picard-5.0-default.jar   (Picard built against htsjdk 5.0.0-9fae270-SNAPSHOT)
#   picard-5.0-jdk.jar       (same jar, runtime flag difference)
#   picard-5.0-zlib.jar      (same jar, runtime flag + JVM prop difference)
# (the same jar can be symlinked under multiple names, or duplicated -- the harness only uses the basename.)

pixi run ./run.sh \
  --bam       ~/work/public-data/1kg/HG03953.0.1x.bam \
  --cram      ~/work/public-data/1kg/HG03953.0.1x.cram \
  --reference ~/work/public-data/1kg/GRCh38_full_analysis_set_plus_decoy_hla.fa.gz \
  --iterations 3

./summarize.sh out/results.tsv > results-laptop.md
```

## AWS pivot

1. Provision two otherwise-identical instances: one Graviton (`c8g.4xlarge` or similar), one x86 (`c7i.4xlarge` or similar). Same vCPU count, same memory, same EBS class, same generation.
2. Install pixi, clone htsjdk, `cd benchmarks/perf_5.0.0`, `pixi install`.
3. Copy the WGS BAM + CRAM + reference from S3 onto the instance's local NVMe (`/local/...`) — don't run benchmarks against EBS or S3-FUSE.
4. Drop fat jars into `/tmp/picard-bench` (cross-built x86 → x86, aarch64 → aarch64; same jar will work on both since Picard is pure Java but native deflater libs differ).
5. Same `run.sh` invocation as the smoke test, with the AWS-side input paths and the per-platform variant set (see "Suggested variants for AWS").
6. Commit `results-graviton.md` and `results-x86.md` (and `out/results.tsv` if we want auditability) back to this directory.
7. Distill the headline numbers into the v5.0.0 CHANGELOG performance section.
