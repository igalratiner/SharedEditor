FROM gradle:4.7.0-jdk8-alpine AS build
COPY --chown=gradle:gradle . /home/gradle/src
WORKDIR /home/gradle/src/accounts
RUN gradle build

FROM openjdk:8-jre-slim

COPY --from=build /home/gradle/src/accounts/build/libs/*.jar /app/accounts.jar
ENTRYPOINT java -jar /app/accounts.jar shared-editor-db:5113