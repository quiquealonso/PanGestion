# Imagen de produccion de la API: compila con Maven y ejecuta solo el .jar con un JRE
FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre
# Hora de Espana: las horas que guarda la API (pedido enviado, preparando desde...) son hora local, y el
# navegador las lee como tal. En UTC, los minutos de cocina y de las mesas saldrian con 1-2 horas de mas.
ENV TZ=Europe/Madrid
WORKDIR /app
RUN useradd --system --uid 1001 app && mkdir -p /app/uploads && chown app /app/uploads
COPY --from=build /src/target/*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]
