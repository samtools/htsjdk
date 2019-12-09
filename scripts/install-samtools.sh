#!/bin/sh
set -ex
wget https://github.com/samtools/samtools/releases/download/1.10/samtools-1.10.tar.bz2
tar -xjvf samtools-1.10.tar.bz2
cd samtools-1.10 && ./configure --prefix=/usr && make && sudo make install
