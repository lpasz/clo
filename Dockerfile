FROM clojure:tools-deps as builder
COPY . /usr/src/app
WORKDIR /usr/src/app
RUN clj -T:build uber

FROM eclipse-temurin:latest
COPY . /usr/src/app
COPY --from=builder /usr/src/app/target/app-standalone.jar /usr/src/app/target/app-standalone.jar
CMD ["java", "-jar", "/usr/src/app/target/app-standalone.jar"]