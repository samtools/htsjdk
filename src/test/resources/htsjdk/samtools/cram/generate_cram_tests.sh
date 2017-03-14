#!/bin/bash -eux

# A bash script to convert a SAM file to CRAM.
# The output should be 2 files: <sam name>.2.1.cram and <sam name>.3.0.cram.
# A reference fasta file is expected to be next to the SAM file and follow predefined naming convention, for example: 
# - ce.fa
# - ce#unmap.sam

sam=$1
ref=$(echo "${sam}" | cut -f 1 -d '#').fa
out=${sam/%.sam/}

# convert SAM to CRAM v2.1:
samtools view  --output-fmt-option version=2.1 -T "${ref}" -C "${sam}" > "${out}".2.1.cram
# assert the output file is CRAM v2.1:
od -t x1 "${out}".2.1.cram | head -1 | grep -q '0000000 43 52 41 4d 02 01'

# convert SAM to CRAM v3.0:
samtools view  --output-fmt-option version=3.0 -T "${ref}" -C "${sam}" > "${out}".3.0.cram
# assert the output file is CRAM v3.0:
od -t x1 "${out}".3.0.cram | head -1 | grep -q '0000000 43 52 41 4d 03 00'
