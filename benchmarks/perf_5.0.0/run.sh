#!/usr/bin/env bash
# Driver for the htsjdk 5.0.0 release benchmark.
#
# Runs each (variant x test) cell N times and writes a results.tsv with one row
# per measured run. Aggregation/medians are computed by summarize.sh.
#
# Variants are Picard fat jars dropped into $PICARD_DIR (default
# /tmp/picard-bench). Each jar's basename is the variant name. Naming
# convention encodes the deflater configuration:
#
#   *-default   : Picard defaults (IntelDeflater on x86, falls through on aarch64)
#   *-jdk       : pass --USE_JDK_DEFLATER true --USE_JDK_INFLATER true
#                 (with htsjdk 5.0 -> jlibdeflate; with 4.3 -> JDK zlib)
#   *-zlib      : *-jdk PLUS -Dsamjdk.use_libdeflate=false (true JDK zlib in 5.0)
#
# samtools is taken from the pixi environment; the special variant name
# "samtools" is added automatically.
#
# Tests:
#   bam-read            Picard CollectInsertSizeMetrics on $BAM
#                       (samtools: `samtools view -c` as a read-only proxy)
#   bam-write           Picard SamFormatConverter BAM -> BAM
#   cram-read           Picard CollectInsertSizeMetrics on $CRAM
#   bam-to-cram-fast    BAM -> CRAM, FAST profile / samtools 3.0
#   bam-to-cram-normal  BAM -> CRAM, NORMAL profile / samtools 3.1 (skipped for picard-4.3*)
#
# Usage:
#   pixi run ./run.sh \
#     --bam /path/to/input.bam \
#     --cram /path/to/input.cram \
#     --reference /path/to/reference.fa \
#     [--picard-dir /tmp/picard-bench] \
#     [--out-dir ./out] \
#     [--iterations 3] \
#     [--variants picard-4.3-default,picard-5.0-default,...] \
#     [--tests bam-read,bam-write,...]
#
# No warmup runs. Inputs are cat'd to /dev/null once at script start to warm
# the OS page cache; per-iteration cold-cache flushing is intentionally not
# done (laptop is for smoke-testing only; AWS doesn't need it for our purposes).

set -euo pipefail

# --- defaults ------------------------------------------------------------------

PICARD_DIR="/tmp/picard-bench"
OUT_DIR="./out"
ITERATIONS=3
VARIANTS=""
TESTS="bam-read,bam-write,cram-read,bam-to-cram-fast,bam-to-cram-normal"
BAM=""
CRAM=""
REFERENCE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --bam)        BAM="$2"; shift 2 ;;
    --cram)       CRAM="$2"; shift 2 ;;
    --reference)  REFERENCE="$2"; shift 2 ;;
    --picard-dir) PICARD_DIR="$2"; shift 2 ;;
    --out-dir)    OUT_DIR="$2"; shift 2 ;;
    --iterations) ITERATIONS="$2"; shift 2 ;;
    --variants)   VARIANTS="$2"; shift 2 ;;
    --tests)      TESTS="$2"; shift 2 ;;
    -h|--help)    awk '/^# /{sub(/^# ?/,""); print; next} /^[^#]/{exit}' "$0"; exit 0 ;;
    *)            echo "Unknown arg: $1" >&2; exit 1 ;;
  esac
done

[[ -n "$BAM"       ]] || { echo "--bam is required"       >&2; exit 1; }
[[ -n "$CRAM"      ]] || { echo "--cram is required"      >&2; exit 1; }
[[ -n "$REFERENCE" ]] || { echo "--reference is required" >&2; exit 1; }
for f in "$BAM" "$CRAM" "$REFERENCE"; do
  [[ -f "$f" ]] || { echo "Not a file: $f" >&2; exit 1; }
done

mkdir -p "$OUT_DIR"
RESULTS="$OUT_DIR/results.tsv"
LOG_DIR="$OUT_DIR/logs"
mkdir -p "$LOG_DIR"

# --- enumerate variants --------------------------------------------------------

declare -a ALL_VARIANTS=()
if [[ -d "$PICARD_DIR" ]]; then
  while IFS= read -r jar; do
    ALL_VARIANTS+=("$(basename "$jar" .jar)")
  done < <(find "$PICARD_DIR" -maxdepth 1 -name '*.jar' -type f | sort)
fi
ALL_VARIANTS+=("samtools")

if [[ -z "$VARIANTS" ]]; then
  VARIANTS_ARRAY=("${ALL_VARIANTS[@]}")
else
  IFS=',' read -ra VARIANTS_ARRAY <<<"$VARIANTS"
fi
IFS=',' read -ra TESTS_ARRAY <<<"$TESTS"

# --- helpers -------------------------------------------------------------------

# Detect /usr/bin/time flavor once.
if /usr/bin/time -l true >/dev/null 2>&1; then
  TIME_FLAG="-l"   # macOS BSD time, RSS in bytes
else
  TIME_FLAG="-v"   # GNU coreutils time, RSS in kilobytes
fi

# Cross-platform stat -- file size in bytes.
file_size() {
  if stat -f '%z' "$1" >/dev/null 2>&1; then stat -f '%z' "$1"
  else stat -c '%s' "$1"
  fi
}

# Variant -> jar path, or "" for samtools.
jar_for() {
  [[ "$1" == "samtools" ]] && return
  echo "$PICARD_DIR/$1.jar"
}

# Picard CLI flags driven by the suffix in the variant name.
picard_flags_for() {
  case "$1" in
    *-jdk|*-zlib) echo "USE_JDK_DEFLATER=true USE_JDK_INFLATER=true" ;;
    *)            echo "" ;;
  esac
}

# Extra JVM system properties driven by the suffix.
jvm_props_for() {
  case "$1" in
    *-zlib) echo "-Dsamjdk.use_libdeflate=false" ;;
    *)      echo "" ;;
  esac
}

# Run one timed command. Sets globals BEFORE calling: cmd_args (array), cmd_outfile (path or "").
# Args: variant, test, iteration.
run_timed() {
  local variant="$1" test="$2" iter="$3"
  local logfile="$LOG_DIR/${variant}.${test}.${iter}.log"
  local timefile="$LOG_DIR/${variant}.${test}.${iter}.time"

  /usr/bin/time "$TIME_FLAG" -o "$timefile" "${cmd_args[@]}" >"$logfile" 2>&1 \
    || { echo "FAILED: $variant $test iter=$iter (see $logfile)"; return 1; }

  local wall_s rss_b
  if [[ "$TIME_FLAG" == "-l" ]]; then
    wall_s=$(awk '/real /{print $1; exit}' "$timefile")
    rss_b=$(awk '/maximum resident set size/{print $1; exit}' "$timefile")
  else
    wall_s=$(awk -F': ' '/Elapsed \(wall clock\) time/{print $2; exit}' "$timefile" \
      | awk -F: '{ if (NF==3) print $1*3600+$2*60+$3; else if (NF==2) print $1*60+$2; else print $1 }')
    local rss_kb
    rss_kb=$(awk -F': ' '/Maximum resident set size/{print $2; exit}' "$timefile")
    rss_b=$((rss_kb * 1024))
  fi

  local out_b=0
  [[ -n "$cmd_outfile" && -f "$cmd_outfile" ]] && out_b=$(file_size "$cmd_outfile")

  printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$variant" "$test" "$iter" "$wall_s" "$rss_b" "$out_b" >>"$RESULTS"
}

# Build cmd_args + cmd_outfile for a (variant, test) cell.
# Returns 1 if the cell should be skipped.
build_cmd() {
  local variant="$1" test="$2"
  local jar; jar=$(jar_for "$variant")
  local props; props=$(jvm_props_for "$variant")
  local flags; flags=$(picard_flags_for "$variant")

  cmd_args=()
  cmd_outfile=""

  case "$test" in
    bam-read)
      if [[ -z "$jar" ]]; then
        # `samtools view -c` would skip via the index. Use a full-decode read
        # piped to /dev/null instead so we're actually exercising decompression
        # + decoding, which is what we're benchmarking against Picard.
        cmd_args=(sh -c "samtools view '$BAM' > /dev/null")
      else
        # CollectQualityYieldMetrics is a single-pass-over-records tool that
        # does not plot (no R/PDF render), so the cost is mostly Picard JVM
        # startup + htsjdk decode + per-record stats. CollectInsertSizeMetrics
        # adds ~12s of R-driven PDF rendering, which inflates the comparison
        # against samtools dramatically.
        local m="$OUT_DIR/$variant.cqm.txt"
        cmd_args=(java)
        [[ -n "$props" ]] && cmd_args+=("$props")
        cmd_args+=(-jar "$jar" CollectQualityYieldMetrics "I=$BAM" "O=$m")
        [[ -n "$flags" ]] && for w in $flags; do cmd_args+=("$w"); done
      fi
      ;;
    bam-write)
      cmd_outfile="$OUT_DIR/$variant.recompressed.bam"
      if [[ -z "$jar" ]]; then
        cmd_args=(samtools view -b -o "$cmd_outfile" "$BAM")
      else
        cmd_args=(java)
        [[ -n "$props" ]] && cmd_args+=("$props")
        cmd_args+=(-jar "$jar" SamFormatConverter "I=$BAM" "O=$cmd_outfile")
        [[ -n "$flags" ]] && for w in $flags; do cmd_args+=("$w"); done
      fi
      ;;
    cram-read)
      if [[ -z "$jar" ]]; then
        cmd_args=(sh -c "samtools view --reference '$REFERENCE' '$CRAM' > /dev/null")
      else
        local m="$OUT_DIR/$variant.cram.cqm.txt"
        cmd_args=(java)
        [[ -n "$props" ]] && cmd_args+=("$props")
        cmd_args+=(-jar "$jar" CollectQualityYieldMetrics "I=$CRAM" "O=$m" "R=$REFERENCE")
        [[ -n "$flags" ]] && for w in $flags; do cmd_args+=("$w"); done
      fi
      ;;
    bam-to-cram-fast)
      cmd_outfile="$OUT_DIR/$variant.fast.cram"
      if [[ -z "$jar" ]]; then
        cmd_args=(samtools view -C --reference "$REFERENCE" --output-fmt-option version=3.0 -o "$cmd_outfile" "$BAM")
      else
        cmd_args=(java)
        [[ -n "$props" ]] && cmd_args+=("$props")
        cmd_args+=(-jar "$jar" SamFormatConverter "I=$BAM" "O=$cmd_outfile" "R=$REFERENCE")
        # CRAM_PROFILE flag only meaningful for picard-5.0 (must be exposed on
        # SamFormatConverter in the picard-5.0 fat-jar build). Use legacy K=V
        # syntax so it composes with the I=/O=/R= args without tripping Picard's
        # mixed-arg-style detection.
        [[ "$variant" == picard-5.0* ]] && cmd_args+=("CRAM_PROFILE=FAST")
        [[ -n "$flags" ]] && for w in $flags; do cmd_args+=("$w"); done
      fi
      ;;
    bam-to-cram-normal)
      [[ "$variant" == picard-4.3* ]] && return 1   # 4.3 only writes 3.0
      cmd_outfile="$OUT_DIR/$variant.normal.cram"
      if [[ -z "$jar" ]]; then
        cmd_args=(samtools view -C --reference "$REFERENCE" --output-fmt-option version=3.1 -o "$cmd_outfile" "$BAM")
      else
        cmd_args=(java)
        [[ -n "$props" ]] && cmd_args+=("$props")
        cmd_args+=(-jar "$jar" SamFormatConverter "I=$BAM" "O=$cmd_outfile" "R=$REFERENCE" CRAM_PROFILE=NORMAL)
        [[ -n "$flags" ]] && for w in $flags; do cmd_args+=("$w"); done
      fi
      ;;
    *)
      echo "Unknown test: $test" >&2; return 1 ;;
  esac
  return 0
}

# --- main ----------------------------------------------------------------------

# Header row.
[[ -s "$RESULTS" ]] || printf 'variant\ttest\titeration\twall_s\tpeak_rss_bytes\tout_bytes\n' >"$RESULTS"

echo "Pre-warming page cache for inputs..."
cat "$BAM" "$CRAM" "$REFERENCE" >/dev/null

declare -a cmd_args=()
declare cmd_outfile=""

for variant in "${VARIANTS_ARRAY[@]}"; do
  for test in "${TESTS_ARRAY[@]}"; do
    if ! build_cmd "$variant" "$test"; then
      echo "[skip] $variant $test"
      continue
    fi
    for ((i=1; i<=ITERATIONS; i++)); do
      echo "[run]  $variant $test iter=$i  -> ${cmd_args[*]}"
      run_timed "$variant" "$test" "$i"
    done
  done
done

echo
echo "Done. Raw results: $RESULTS"
echo "Per-run logs:    $LOG_DIR"
