#!/bin/sh

set -e
WORKING_DIR=/home/travis/build/samtools/htsjdk
docker pull ga4gh/htsget-ref:1.1.0
docker container run -d --name htsget-server -p 3000:3000 --env HTSGET_PORT=3000 --env HTSGET_HOST=http://127.0.0.1:3000 \
      -v $WORKING_DIR/src/test/resources/htsjdk/samtools/BAMFileIndexTest/:/data \
      -v $WORKING_DIR/scripts/htsget-scripts:/data/scripts \
      ga4gh/htsget-ref:1.1.0 \
      ./htsref -config /data/scripts/htsget_config.json
docker container ls -a

curl http://localhost:3000
