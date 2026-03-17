FROM maven:3.8.5-openjdk-11-slim AS builder

WORKDIR /app

COPY . .

ARG MVN_PROFILE=default

RUN mvn clean package -P ${MVN_PROFILE}

FROM tomcat:10-jdk11

WORKDIR /app

COPY --from=builder /app/target/fablix.war /usr/local/tomcat/webapps/fablix.war

EXPOSE 8080

CMD ["catalina.sh", "run"]