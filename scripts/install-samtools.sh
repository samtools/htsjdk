#!/bin/sh
set -ex
wget https://github.com/samtools/samtools/releases/download/1.14/samtools-1.14.tar.bz2
# CRAM Interop Tests are dependent on the test files in samtools-1.14/htslib-1.14/htscodes/tests/dat
tar -xjvf samtools-1.14.tar.bz2
echo "print current dir"
pwd
cd samtools-1.14 && ./configure --prefix=/usr && make && sudo make install