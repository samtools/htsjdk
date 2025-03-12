# This docker build is here to enable Carrot tests
# It's not intended for any other purpose
FROM eclipse-temurin:17

COPY . .
RUN ./gradlew shadowJar
ENTRYPOINT bash
