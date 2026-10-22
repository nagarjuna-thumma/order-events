# Build the jars first with `mvn -DskipTests package`, then:
#   docker build --build-arg SERVICE=order-service -t order-service .
#   docker build --build-arg SERVICE=fulfilment-service -t fulfilment-service .
FROM eclipse-temurin:21-jre AS layers
ARG SERVICE
WORKDIR /app
COPY ${SERVICE}/target/${SERVICE}-*.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 app
# Dependencies change least often, so they go in the lowest layer and stay cached between builds.
COPY --from=layers /app/extracted/dependencies/ ./
COPY --from=layers /app/extracted/spring-boot-loader/ ./
COPY --from=layers /app/extracted/snapshot-dependencies/ ./
COPY --from=layers /app/extracted/application/ ./
USER 10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
