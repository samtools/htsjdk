#!/bin/sh

set -e
set -v

DOCKER_NAME='ga4gh/htsget-refserver'
SERVER_VERSION='1.1.0'
DOCKER_COORDINATE=${DOCKER_NAME}:${SERVER_VERSION}

docker pull ${DOCKER_COORDINATE}
docker rm htsget-server
docker container run -d --name htsget-server -p 3000:3000 --env HTSGET_PORT=3000 --env HTSGET_HOST=http://127.0.0.1:3000 \
      -v ${PWD}/src/test/resources/htsjdk/samtools/BAMFileIndexTest/:/data \
      -v ${PWD}/scripts/htsget-scripts:/data/scripts \
      ${DOCKER_COORDINATE} \
      ./htsref -config /data/scripts/htsget_config.json
sleep 2
docker container ls -a


curl http://localhost:3000
