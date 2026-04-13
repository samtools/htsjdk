# CRAM Cross-Implementation Validation Pipeline

Validates CRAM encoding/decoding compatibility between htsjdk and samtools by converting
input files with both tools and comparing the results.

## Prerequisites

Build the htsjdk fat JAR from the repo root:

```bash
./gradlew shadowJar
```

## Setup

Install the pipeline dependencies (samtools, snakemake, Java) via pixi:

```bash
cd validation
pixi install
```

## Running

Run with the default sample sheet (`samples.tsv`):

```bash
pixi run snakemake --cores 4
```

Or dry-run first to see what will execute:

```bash
pixi run snakemake --cores 4 -n
```

## Configuration

### `samples.tsv`

Tab-separated file listing inputs:

| Column | Description |
|--------|-------------|
| sample | Unique sample name (used in output paths) |
| input | Path to input file (.bam, .cram, or .sam) |
| reference | Path to reference FASTA |

### `config.yaml`

| Key | Default | Description |
|-----|---------|-------------|
| profiles | fast, normal, small, archive | CRAM compression profiles to test |
| htsjdk_jar | `../build/libs/htsjdk-all.jar` | Path to htsjdk fat JAR |
| comparison_mode | strict | `strict` (all fields + tags) or `lenient` (core fields only) |
| max_diffs | 10 | Maximum mismatches to report per comparison |

## What it does

For each sample × profile, the pipeline:

1. Converts the input to BAM as a common baseline
2. Encodes to CRAM with htsjdk (using the specified profile)
3. Encodes to CRAM with samtools (using the equivalent profile)
4. Decodes each CRAM back to BAM with both htsjdk and samtools
5. Compares each decoded BAM against the original

This produces 4 comparisons per sample × profile:

| Comparison | What it validates |
|------------|-------------------|
| htsjdk_via_htsjdk vs original | htsjdk round-trip fidelity |
| htsjdk_via_samtools vs original | samtools can read htsjdk output |
| samtools_via_htsjdk vs original | htsjdk can read samtools output |
| samtools_via_samtools vs original | samtools round-trip baseline |

## Output

Results are written to `output/` with a final `output/summary.txt` showing pass/fail
for each comparison.

## Adding test data

Edit `samples.tsv` to add more input files. The pipeline will automatically process
all combinations of samples × profiles.
