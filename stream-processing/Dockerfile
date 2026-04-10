FROM maven:3.8-eclipse-temurin-11 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

FROM apache/flink:1.17.0
COPY --from=builder /app/target/stream-processing-1.0.0.jar /opt/flink/usrlib/sentinel-job.jar
