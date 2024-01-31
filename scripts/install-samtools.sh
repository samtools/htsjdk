#!/bin/sh
set -ex
wget https://github.com/samtools/samtools/releases/download/1.14/samtools-1.14.tar.bz2
# Note that the CRAM Interop Tests are dependent on the test files in samtools-1.14/htslib-1.14/htscodecs/tests/dat
tar -xjvf samtools-1.14.tar.bz2
cd samtools-1.14 && ./configure --prefix=/usr && make && sudo make install