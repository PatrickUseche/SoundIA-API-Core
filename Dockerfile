# syntax=docker/dockerfile:1
#
# API Gateway de SoundIA (Spring Boot, Java 25). Imagen en dos etapas: compila con el JDK y ejecuta con el JRE.
#
# NOTA: este Dockerfile no se pudo construir al escribirlo (la maquina de desarrollo no tiene Docker).
# Si falla al construirlo, revisar primero los nombres de las imagenes base.

FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Primero solo lo necesario para resolver dependencias: asi esa capa se reutiliza mientras no cambie el pom.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
# Las pruebas ya corren en la integracion continua; aqui solo se empaqueta.
RUN ./mvnw -B -q -DskipTests package


FROM eclipse-temurin:25-jre
WORKDIR /app

# No se ejecuta como root.
RUN useradd --system --uid 1001 --no-create-home soundia
COPY --from=build /workspace/target/soundia-*-SNAPSHOT.jar app.jar
USER soundia

EXPOSE 8080
# DB_PASSWORD es obligatoria; el resto de la configuracion se pasa con variables de entorno (ver README).
# Para poca memoria (512 MB) se puede limitar la JVM con JAVA_TOOL_OPTIONS (ver render.yaml).
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
