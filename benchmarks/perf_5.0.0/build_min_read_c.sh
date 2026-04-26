#!/usr/bin/env bash
# Build min_read.c against an existing htslib source tree.
#
# Expects $HTSLIB_DIR to point at a built htslib (i.e. one where `make` has
# produced libhts.a). The link line mirrors htslib's own htslib_static.mk:
#   HTSLIB_static_LIBS = -lz -lm -lbz2 -llzma -lcurl
# We add -lpthread defensively (recent glibc/musl have moved pthread out of
# libc, and macOS treats it as part of -lSystem so the flag is harmless).
#
# Usage:
#   HTSLIB_DIR=/path/to/htslib ./build_min_read_c.sh
#
# Resulting binary: ./min_read_c (in the same directory as this script).

set -euo pipefail

HTSLIB_DIR="${HTSLIB_DIR:-}"
[[ -n "$HTSLIB_DIR" ]] || { echo "HTSLIB_DIR is required" >&2; exit 1; }
[[ -f "$HTSLIB_DIR/libhts.a" ]] || { echo "no $HTSLIB_DIR/libhts.a -- build htslib first" >&2; exit 1; }
[[ -d "$HTSLIB_DIR/htslib" ]] || { echo "no $HTSLIB_DIR/htslib (header dir) -- wrong HTSLIB_DIR?" >&2; exit 1; }

HERE=$(cd "$(dirname "$0")" && pwd)
SRC="$HERE/min_read.c"
OUT="$HERE/min_read_c"

CC="${CC:-cc}"
CFLAGS="${CFLAGS:--O3 -Wall -Wextra}"

# Read htslib's documented static-link list rather than hard-coding, so this
# stays right even if htslib's deps change.
HTSLIB_STATIC_LIBS=$(awk -F= '/^HTSLIB_static_LIBS/{print $2}' "$HTSLIB_DIR/htslib_static.mk" | xargs)
[[ -n "$HTSLIB_STATIC_LIBS" ]] || HTSLIB_STATIC_LIBS="-lz -lm -lbz2 -llzma -lcurl"

set -x
$CC $CFLAGS -I "$HTSLIB_DIR" -o "$OUT" "$SRC" "$HTSLIB_DIR/libhts.a" $HTSLIB_STATIC_LIBS -lpthread
