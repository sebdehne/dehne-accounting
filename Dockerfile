# syntax=docker/dockerfile:1

FROM eclipse-temurin:21 AS package
WORKDIR /build
COPY --chmod=0755 mvnw mvnw
COPY .mvn/ .mvn/
COPY ./src src/
COPY ./webapp webapp/
RUN --mount=type=bind,source=pom.xml,target=pom.xml \
    --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=webapp/node_modules \
    ./mvnw clean package -DskipTests && \
    mv target/$(./mvnw help:evaluate -Dexpression=project.artifactId -q -DforceStdout)-$(./mvnw help:evaluate -Dexpression=project.version -q -DforceStdout)-jar-with-dependencies.jar target/app.jar


FROM eclipse-temurin:21 AS final
VOLUME /data
COPY --from=package build/target/app.jar ./
ENTRYPOINT [ "java", "-DSQLITE_FILE=/data/sql.db", "-DCONTEXTPATH=/accounting", "-DBACKUP_DIR=/data/backups", "-jar", "app.jar" ]
