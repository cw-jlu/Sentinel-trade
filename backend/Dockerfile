FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN echo '<settings><mirrors><mirror><id>huaweicloud</id><mirrorOf>central</mirrorOf><name>huaweicloud maven</name><url>https://repo.huaweicloud.com/repository/maven/</url></mirror></mirrors></settings>' > settings.xml
RUN mvn -s settings.xml dependency:go-offline -q
COPY src ./src
RUN mvn -s settings.xml package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
