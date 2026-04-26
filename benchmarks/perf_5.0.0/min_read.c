/* min_read.c -- minimal read-only BAM/CRAM iterator built on htslib.
 *
 * Apples-to-apples partner for Picard CollectQualityYieldMetrics: iterate
 * every primary record, walk the qualities array, count total bases and
 * bases at Q20+/Q30+. No required_fields shortcut (uses htslib's INT_MAX
 * default), so every data series is decoded for every record -- matches
 * htsjdk's behaviour, which has no required_fields equivalent.
 *
 * Why this exists in addition to min_read.py: the pysam path adds a
 * substantial Python interpreter + per-record wrapper + numpy.asarray cost
 * on top of htslib's actual decode work. This C tool measures the htslib
 * decode + a 4-cycle inner loop, nothing else, and is the defensible
 * "raw htslib decode" reference.
 *
 * Build:  HTSLIB_DIR=/path/to/htslib ./build_min_read_c.sh
 * Usage:  ./min_read_c INPUT.{bam,cram} [REFERENCE.fa]
 */

#include <stdio.h>
#include <stdlib.h>
#include <htslib/sam.h>
#include <htslib/hts.h>

int main(int argc, char **argv) {
    if (argc < 2) {
        fprintf(stderr, "usage: %s INPUT.{bam,cram} [REFERENCE.fa]\n", argv[0]);
        return 2;
    }

    samFile *fp = sam_open(argv[1], "r");
    if (!fp) { perror("sam_open"); return 1; }

    if (argc >= 3) {
        if (hts_set_fai_filename(fp, argv[2]) != 0) {
            fprintf(stderr, "hts_set_fai_filename(%s) failed\n", argv[2]);
            return 1;
        }
    }

    sam_hdr_t *hdr = sam_hdr_read(fp);
    if (!hdr) { fprintf(stderr, "sam_hdr_read failed\n"); return 1; }

    bam1_t *b = bam_init1();
    long reads = 0, bases = 0, q20 = 0, q30 = 0;
    int r;
    while ((r = sam_read1(fp, hdr, b)) >= 0) {
        if (b->core.flag & (BAM_FSECONDARY | BAM_FSUPPLEMENTARY)) continue;
        int n = b->core.l_qseq;
        if (n <= 0) continue;
        const uint8_t *q = bam_get_qual(b);
        /* htslib stores 0xff in qual[0] when QUAL is absent ("*"). */
        if (q[0] == 0xff) continue;
        reads++;
        bases += n;
        for (int i = 0; i < n; i++) {
            if (q[i] >= 20) q20++;
            if (q[i] >= 30) q30++;
        }
    }

    int rc = (r < -1) ? 1 : 0;
    if (rc) fprintf(stderr, "sam_read1 error: %d\n", r);

    bam_destroy1(b);
    sam_hdr_destroy(hdr);
    sam_close(fp);

    printf("reads=%ld bases=%ld q20=%ld q30=%ld\n", reads, bases, q20, q30);
    return rc;
}
