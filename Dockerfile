# ---- 构建阶段 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# 先拷贝 pom 与 mvnw，利用 Docker 层缓存，依赖不变时无需重新下载。
COPY pom.xml ./
RUN mvn -B dependency:go-offline

# 拷贝源码并打包（跳过测试，避免构建阶段依赖 Testcontainers 拉起的数据库）。
COPY src ./src
RUN mvn -B package -DskipTests

# ---- 运行阶段 ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# 非 root 用户运行，降低容器被攻破后的影响面。
RUN addgroup --system app && adduser --system --ingroup app app

COPY --from=build /app/target/*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
