#!/bin/sh
set -ex
# ubuntu specific
sudo apt-get update
sudo apt-get install -y libncurses-dev libbz2-dev liblzma-dev

# install from the github tar
export SAMTOOLS_VERSION=1.23.1
wget https://github.com/samtools/samtools/releases/download/${SAMTOOLS_VERSION}/samtools-${SAMTOOLS_VERSION}.tar.bz2
tar -xjvf samtools-${SAMTOOLS_VERSION}.tar.bz2
cd samtools-${SAMTOOLS_VERSION} && ./configure --prefix=/usr/local && make && sudo make install

# tabix comes from the htslib bundled in the samtools tarball; samtools' configure has already configured it
cd htslib-${SAMTOOLS_VERSION} && make tabix && sudo install tabix /usr/local/bin/tabix
