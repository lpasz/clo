FROM clojure:tools-deps as build
COPY . /usr/src/app
WORKDIR /usr/src/app
RUN clj -T:build uber

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /usr/src/app/target/app-standalone.jar ./app.jar
CMD ["java", "-jar", "./app.jar"]