#!/usr/bin/env python3
"""Minimal read-only BAM/CRAM iteration via pysam (htslib).

Mimics the per-record work scope of Picard CollectQualityYieldMetrics so the
runtime is a fair apples-to-apples comparison against the Java/htsjdk path:
iterate every primary record, walk the qualities array once, accumulate
counts of total bases and bases at Q20+/Q30+. Uses default required_fields
(INT_MAX) so htslib does a full per-record decode, matching htsjdk.

Usage: min_read.py INPUT.{bam,cram} [REFERENCE.fa]
"""

import sys
import numpy as np
import pysam

if len(sys.argv) < 2:
    sys.exit("usage: min_read.py INPUT.{bam,cram} [REFERENCE.fa]")

inp = sys.argv[1]
ref = sys.argv[2] if len(sys.argv) > 2 else None

f = pysam.AlignmentFile(inp, reference_filename=ref)

reads = bases = q20 = q30 = 0
for r in f:
    if r.is_secondary or r.is_supplementary:
        continue
    q = r.query_qualities  # array.array('B') or None
    if q is None:
        continue
    # Wrap as numpy uint8 view for vectorised threshold counts. asarray on an
    # array.array of typecode 'B' is a no-copy view; on numpy input it's a no-op.
    qa = np.asarray(q, dtype=np.uint8)
    reads += 1
    bases += qa.size
    q20 += int((qa >= 20).sum())
    q30 += int((qa >= 30).sum())

print(f"reads={reads} bases={bases} q20={q20} q30={q30}")
