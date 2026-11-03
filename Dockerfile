# Etapa 1: compilar Angular
FROM node:24-bookworm-slim AS frontend-build

WORKDIR /app/frontend

COPY frontend/package*.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build -- --configuration production


# Etapa 2: compilar Spring Boot e incorporar Angular
FROM maven:3.9-eclipse-temurin-21 AS backend-build

WORKDIR /app/backend

COPY backend/pom.xml ./
COPY backend/src ./src

# Angular compilado dentro de los recursos estáticos de Spring Boot
COPY --from=frontend-build \
    /app/frontend/dist/frontend/browser/ \
    /app/backend/src/main/resources/static/

# Las pruebas se ejecutan en CI; aquí generamos el JAR
RUN mvn -B -DskipTests package


# Etapa 3: imagen final de ejecución
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=backend-build \
    /app/backend/target/backend-0.0.1-SNAPSHOT.jar \
    /app/esibuy.jar

# Ejecutar sin privilegios de root
RUN groupadd --system esibuy \
    && useradd --system --gid esibuy esibuy \
    && mkdir -p /app/uploads/productos \
    && chown -R esibuy:esibuy /app

USER esibuy

EXPOSE 8080

ENV PORT=8080

ENTRYPOINT ["java", "-jar", "/app/esibuy.jar"]
