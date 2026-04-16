# CRAM Cross-Implementation Validation Pipeline

Validates CRAM encoding/decoding compatibility between htsjdk and samtools by converting
input files with both tools and comparing the results.

## Prerequisites

Build the htsjdk fat JAR from the repo root:

```bash
./gradlew shadowJar
```

## Setup

Install the pipeline dependencies (samtools, snakemake, Java, curl, aws-cli) via pixi:

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

### `config.yaml`

| Key | Default | Description |
|-----|---------|-------------|
| dataset_file | `samples.tsv` | Sample sheet to use (see below) |
| samples | (all) | Optional list of sample names to process (subset) |
| profiles | fast, normal, small, archive | CRAM compression profiles to test |
| htsjdk_jar | `../build/libs/*-all.jar` | Path to htsjdk fat JAR |
| comparison_mode | strict | `strict` (all fields + tags) or `lenient` (core fields only) |
| max_diffs | 10 | Maximum mismatches to report per comparison |

### Sample sheets

The pipeline supports two sample sheet formats, auto-detected by column names.

**Local mode (`samples.tsv`)** — for local test data bundled in the repo:

| Column | Description |
|--------|-------------|
| sample | Unique sample name |
| input | Local path to input BAM/CRAM/SAM |
| reference | Local path to reference FASTA |

**Remote mode (`test_datasets.tsv`)** — for downloading public datasets:

| Column | Description |
|--------|-------------|
| sample | Unique sample name |
| platform | Sequencing platform (informational) |
| assay | Assay type (informational) |
| organism | Organism (informational) |
| input_url | URL to input BAM/CRAM (HTTPS, S3) |
| reference_url | URL to reference FASTA |
| approx_size | Approximate file size (informational) |
| reason | Why this dataset is included (informational) |
| notes | Verification status, reference details (informational) |

In remote mode, the pipeline automatically downloads inputs and references before
processing. References shared across samples are downloaded once (deduplicated by URL).
Downloaded files are cached in `downloads/` — re-running skips completed downloads.

## Remote mode

To validate against public datasets from ENA, GIAB, ENCODE, 1000 Genomes, etc.:

1. Edit `config.yaml`:
   ```yaml
   dataset_file: "test_datasets.tsv"
   ```

2. (Optional) Start with a small subset to test:
   ```yaml
   samples:
     - sarscov2_amplicon    # 185 MB, fastest
     - geuvadis_rnaseq      # 3.5 GB
   ```

3. Run:
   ```bash
   pixi run snakemake --cores 4
   ```

### Disk requirements

The full `test_datasets.tsv` contains 12 datasets totaling ~400 GB of input data plus
~21 GB of reference genomes. The pipeline also generates BAM intermediates and CRAM
files for each profile. Plan for approximately **1 TB** of total disk space.

### Download protocols

The pipeline handles two URL protocols:

- **HTTPS** — used for EBI FTP, NCBI, ENCODE, 10x Genomics, GCS public buckets.
  Downloaded with `curl`.
- **S3** — used for Ultima Genomics and ONT public buckets.
  Downloaded with `aws s3 cp --no-sign-request` (no AWS credentials needed).

## What it does

For each sample x profile, the pipeline:

1. Downloads the input and reference (remote mode only)
2. Converts the input to BAM as a common baseline
3. Encodes to CRAM with htsjdk (using the specified profile)
4. Encodes to CRAM with samtools (using the equivalent profile)
5. Decodes each CRAM back to BAM with both htsjdk and samtools
6. Compares each decoded BAM against the original

This produces 4 comparisons per sample x profile:

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

Edit `samples.tsv` (local) or `test_datasets.tsv` (remote) to add more input files.
The pipeline will automatically process all combinations of samples x profiles.
