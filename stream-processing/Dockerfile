FROM maven:3.8-eclipse-temurin-11 AS builder
WORKDIR /app
COPY pom.xml .
RUN echo '<settings><mirrors><mirror><id>huaweicloud</id><mirrorOf>central</mirrorOf><name>huaweicloud maven</name><url>https://repo.huaweicloud.com/repository/maven/</url></mirror></mirrors></settings>' > settings.xml
RUN mvn -s settings.xml dependency:go-offline -q
COPY src ./src
RUN mvn -s settings.xml package -DskipTests -q

FROM apache/flink:1.17.0
COPY --from=builder /app/target/stream-processing-1.0.0.jar /opt/flink/usrlib/sentinel-job.jar
