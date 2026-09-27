FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /src
COPY src/ComplianceChecker.java .
RUN javac ComplianceChecker.java

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /src/ComplianceChecker.class /app/ComplianceChecker.class
RUN mkdir -p /inputs /outputs
ENTRYPOINT ["java", "-cp", "/app", "ComplianceChecker"]
