# ==========================================
# Etapa 1: Construcción (Build) con Maven y JDK 17
# ==========================================
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copiar archivos del wrapper y dependencias para aprovechar la caché de Docker
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Descargar dependencias sin compilar código aún (layer caching)
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B || true

# Copiar el código fuente completo
COPY src ./src

# Compilar y empaquetar la aplicación omitiendo tests en el build de contenedor
RUN ./mvnw clean package -DskipTests -B

# ==========================================
# Etapa 2: Imagen final liviana de ejecución (Runtime JRE 17)
# ==========================================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Crear directorio para posibles subidas y usuario sin privilegios root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup && mkdir -p /app/uploads && chown -R appuser:appgroup /app

USER appuser

# Copiar el JAR generado desde la etapa de construcción
COPY --from=builder /app/target/*.jar app.jar

# Variables de entorno por defecto
ENV PORT=8082
ENV JAVA_OPTS="-XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Xms128m -Xmx384m"

EXPOSE 8082

# Ejecutar Spring Boot conectando el puerto dinámico de Render ($PORT)
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT} -jar app.jar"]

