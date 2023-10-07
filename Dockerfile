FROM clojure:tools-deps as build
COPY . /usr/src/app
WORKDIR /usr/src/app
RUN clj -T:build uber

FROM openjdk:22-slim-bookworm
WORKDIR /app
COPY --from=BUILD /usr/src/app/target/app-standalone.jar ./app.jar
CMD ["java", "-jar", "app.jar"]