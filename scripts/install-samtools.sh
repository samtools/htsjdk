#!/bin/sh
set -ex
wget https://github.com/samtools/samtools/releases/download/1.9/samtools-1.9.tar.bz2
tar -xjvf samtools-1.9.tar.bz2
cd samtools-1.9 && ./configure --prefix=/usr && make && sudo make install
