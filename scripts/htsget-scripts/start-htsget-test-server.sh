#!/bin/sh

set -e
set -v

DOCKER_NAME='ga4gh/htsget-refserver'
SERVER_VERSION='1.1.0'
DOCKER_COORDINATE=${DOCKER_NAME}:${SERVER_VERSION}

CONTAINER_NAME="htsget-test-server"

docker pull ${DOCKER_COORDINATE}

#Remove the container if it already exists
docker rm -f ${CONTAINER_NAME} || true

#Run the container set port mapping and mount in the data
docker container run -d --name ${CONTAINER_NAME} -p 3000:3000 --env HTSGET_PORT=3000 --env HTSGET_HOST=http://127.0.0.1:3000 \
      -v ${PWD}/src/test/resources/htsjdk/samtools/BAMFileIndexTest/:/data \
      -v ${PWD}/scripts/htsget-scripts:/data/scripts \
      ${DOCKER_COORDINATE} \
      ./htsref -config /data/scripts/htsget_config.json

#give it a second(s) to start
sleep 2

#List containers
docker container ls -a

#Check that we can connect
curl http://localhost:3000
