# htsjdk 5.0.0 release benchmark

Just-enough performance benchmarks to back up the perf claims in the v5.0.0 CHANGELOG / release notes. The goal is **a small number of honest, reproducible numbers**, not exhaustive performance engineering.

## What we measure

For each combination of *variant* × *test* we record wall-clock time and (for write tests) the size of the output file.

### Tests

| Test | Driver | Why it's here |
|---|---|---|
| `bam-read` | Picard `CollectInsertSizeMetrics I=$BAM` (samtools: `samtools view -c $BAM` as a read-only proxy) | end-to-end realistic, read-heavy. Substantiates the "BAM read is faster in 5.0" claim. |
| `bam-write` | Picard `SamFormatConverter I=$BAM O=...bam` (samtools: `samtools view -b -o ... $BAM`) | exercises the BAM compression path. Substantiates the jlibdeflate-vs-zlib-vs-IntelDeflater story. |
| `cram-read` | Picard `CollectInsertSizeMetrics I=$CRAM R=$REF` (samtools: `samtools view -c --reference ...`) | substantiates the CRAM read speed-ups. |
| `bam-to-cram-fast` | Picard `SamFormatConverter I=$BAM O=...cram R=$REF [--CRAM_PROFILE FAST]`. samtools: `samtools view -C --output-fmt-option version=3.0`. | apples-to-apples 4.3 (CRAM 3.0) vs 5.0 (FAST profile, ≈ 3.0 codec choices). |
| `bam-to-cram-normal` | same with `--CRAM_PROFILE NORMAL`. samtools: `--output-fmt-option version=3.1`. Skipped for `picard-4.3*` (4.3 cannot write 3.1). | the user-default 3.1 path. |

### Variants

Variants are Picard fat jars dropped into `$PICARD_DIR` (default `/tmp/picard-bench`). The basename of each jar is the variant name. Naming convention encodes the deflater configuration:

| Suffix | Picard CLI flags | JVM props | Effective Deflater (htsjdk 4.3) | Effective Deflater (htsjdk 5.0) |
|---|---|---|---|---|
| `-default` | (none) | (none) | IntelDeflater on x86, JDK zlib on aarch64 | IntelDeflater on x86, **JDK zlib** on aarch64 |
| `-jdk` | `USE_JDK_DEFLATER=true USE_JDK_INFLATER=true` | (none) | JDK zlib | **jlibdeflate** |
| `-zlib` | `USE_JDK_DEFLATER=true USE_JDK_INFLATER=true` | `-Dsamjdk.use_libdeflate=false` | JDK zlib | JDK zlib (forced) |

> ⚠️ **Picard's `IntelDeflaterFactory` is what registers a default Deflater with htsjdk** — and when its native fails to load (e.g. on aarch64) it explicitly falls back to **JDK zlib**, NOT to htsjdk's default. So the `-default` variant on aarch64 measures JDK zlib through Picard's fallback, **not** jlibdeflate. To engage jlibdeflate via Picard you must pass `USE_JDK_DEFLATER=true USE_JDK_INFLATER=true` (the `-jdk` variant) — those flags bypass `IntelDeflaterFactory` entirely and let htsjdk choose its default (libdeflate in 5.0). Confirm via the htsjdk log line: `INFO  DeflaterFactory  libdeflate is available; using libdeflate for DEFLATE compression.`

> ⚠️ **IntelDeflater is x86-only.** On aarch64 (Apple Silicon, Graviton) the Intel native `libgkl_compression` fails to load and Picard's `IntelDeflaterFactory` falls back to JDK zlib (see above). On aarch64 the `-default` and `-zlib` variants therefore measure the same thing (JDK zlib), and only `-jdk` engages jlibdeflate.

samtools is used as a fifth variant for the cross-implementation comparison rows.

### Suggested variants for AWS

**x86** (5 picard variants + samtools): `picard-4.3-default`, `picard-4.3-jdk`, `picard-5.0-default`, `picard-5.0-jdk`, `picard-5.0-zlib`, `samtools`. Five distinct deflater paths: IntelDeflater (4.3 / 5.0), JDK zlib (4.3 -jdk / 5.0 -zlib), jlibdeflate (5.0 -jdk).

**Graviton (aarch64)** (3 picard variants + samtools): `picard-4.3-default` (≡ -zlib here, JDK zlib via Picard fallback), `picard-5.0-default` (also JDK zlib via fallback), `picard-5.0-jdk` (jlibdeflate), `samtools`. Three distinct deflater paths: JDK zlib, jlibdeflate, htslib.

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
