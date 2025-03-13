# This docker build is here to enable Carrot tests
# It's not intended for any other purpose
FROM eclipse-temurin:17

RUN apt-get update && \
    apt-get upgrade -y && \
    apt-get -y --no-install-recommends install samtools

ENV HTSJDK_SAMTOOLS_BIN=/usr/bin/samtools

COPY . .
RUN ./gradlew shadowJar
ENTRYPOINT bash
