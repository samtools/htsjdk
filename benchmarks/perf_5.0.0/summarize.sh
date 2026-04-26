#!/usr/bin/env bash
# Aggregate the raw results.tsv produced by run.sh into a markdown table.
# Reports the median wall-clock per (variant, test) and the output-file size
# (taken from the first iteration -- output is deterministic).
#
# Usage:
#   ./summarize.sh out/results.tsv > results.md

set -euo pipefail

RESULTS="${1:?usage: summarize.sh path/to/results.tsv}"
[[ -f "$RESULTS" ]] || { echo "not a file: $RESULTS" >&2; exit 1; }

gawk -F'\t' '
  function median(s,    arr, n, i, sorted) {
    n = split(s, arr, " ")
    asort(arr, sorted)
    if (n % 2 == 1) return sorted[(n + 1) / 2]
    return (sorted[n/2] + sorted[n/2 + 1]) / 2
  }

  function human_size(b,    units, i, v) {
    split("B KB MB GB TB", units, " ")
    v = b + 0; i = 1
    while (v >= 1024 && i < 5) { v /= 1024; i++ }
    return sprintf("%.2f %s", v, units[i])
  }

  NR == 1 { next }   # skip header

  {
    key = $1 "\t" $2
    walls[key] = (walls[key] ? walls[key] " " : "") $4
    if (!(key in out_bytes)) out_bytes[key] = $6
    variants[$1] = 1
    tests[$2] = 1
  }

  END {
    # Stable variant ordering: alphabetical for picard-* (htsjdk impls), then
    # the non-htsjdk reference impls (htslib, pysam, samtools) at the end.
    n_v = 0
    for (v in variants) if (v != "samtools" && v != "pysam" && v != "htslib") vlist[++n_v] = v
    if (n_v > 0) asort(vlist)
    if ("htslib"   in variants) vlist[++n_v] = "htslib"
    if ("pysam"    in variants) vlist[++n_v] = "pysam"
    if ("samtools" in variants) vlist[++n_v] = "samtools"

    # Stable test ordering: preferred first, then any others as encountered.
    split("bam-read cram-read bam-write bam-to-cram-fast bam-to-cram-normal", preferred, " ")
    n_t = 0
    for (i = 1; i <= length(preferred); i++) if (preferred[i] in tests) tlist[++n_t] = preferred[i]
    for (t in tests) {
      seen = 0
      for (j = 1; j <= n_t; j++) if (tlist[j] == t) { seen = 1; break }
      if (!seen) tlist[++n_t] = t
    }

    printf "| Test |"
    for (i = 1; i <= n_v; i++) printf " %s |", vlist[i]
    printf "\n|---|"
    for (i = 1; i <= n_v; i++) printf " ---: |"
    printf "\n"

    for (j = 1; j <= n_t; j++) {
      t = tlist[j]
      printf "| %s (s) |", t
      for (i = 1; i <= n_v; i++) {
        v = vlist[i]
        key = v "\t" t
        if (key in walls) printf " %.2f |", median(walls[key])
        else printf " — |"
      }
      printf "\n"
    }

    print ""
    print "Output sizes (from first iteration):"
    print ""
    printf "| Test |"
    for (i = 1; i <= n_v; i++) printf " %s |", vlist[i]
    printf "\n|---|"
    for (i = 1; i <= n_v; i++) printf " ---: |"
    printf "\n"
    for (j = 1; j <= n_t; j++) {
      t = tlist[j]
      has_size = 0
      for (i = 1; i <= n_v; i++) {
        v = vlist[i]
        key = v "\t" t
        if ((key in out_bytes) && (out_bytes[key] + 0) > 0) { has_size = 1; break }
      }
      if (!has_size) continue
      printf "| %s |", t
      for (i = 1; i <= n_v; i++) {
        v = vlist[i]
        key = v "\t" t
        if ((key in out_bytes) && (out_bytes[key] + 0) > 0) printf " %s |", human_size(out_bytes[key])
        else printf " — |"
      }
      printf "\n"
    }
  }
' "$RESULTS"
