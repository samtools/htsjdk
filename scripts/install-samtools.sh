#!/bin/sh
set -ex
wget https://github.com/samtools/samtools/releases/download/1.19.1/samtools-1.19.1.tar.bz2
tar -xjvf samtools-1.19.1.tar.bz2
cd samtools-1.19.1 && ./configure --prefix=/usr && make && sudo make install
