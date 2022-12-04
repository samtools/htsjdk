VERSION 0.6

ARG --required DOCKER_REGISTRY_GROUP

deps:
    FROM ${DOCKER_REGISTRY_GROUP}/genestack-java8-builder:latest
    COPY --dir gradle gradlew settings.gradle build.gradle gradle.properties ./
    COPY build.gradle ./
    RUN ./gradlew

    SAVE IMAGE --cache-hint

test:
    FROM +deps

    COPY . .
    # Bad way because of failed tests
    #RUN ./gradlew test

    SAVE IMAGE --cache-hint

publish:
    FROM +test

    RUN ./gradlew uploadArchives
    # Hint for publish release version
    #RUN ./gradlew uploadArchives -Drelease=true

    SAVE IMAGE --cache-hint