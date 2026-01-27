# =========================
# 运行时镜像（只负责跑 jar）
# =========================
FROM eclipse-temurin:8-jre

# 约定容器里的工作目录
WORKDIR /app

# 这个参数用来告诉 Docker：你的 jar 文件在构建上下文里叫什么/在哪
# 你在 GitHub Actions 里可以通过 --build-arg JAR_FILE=xxx 来传入
ARG JAR_FILE=build/app.jar

# 把 jar 拷贝进镜像，并统一命名为 app.jar
# 注意：这里的 JAR_FILE 是“仓库里的相对路径”，比如 build/blog-gateway-service.jar
COPY ${JAR_FILE} /app/app.jar

# 可选：设置容器时区，避免你看到日志时间不对
# 这行不加也能跑，但你之前已经遇到“时区不对”的问题，建议加上
ENV TZ=Asia/Shanghai

# 这里不写死 profile，让你在 docker run / compose 里用环境变量控制
# 例如：SPRING_PROFILES_ACTIVE=preview
ENV SPRING_PROFILES_ACTIVE=preview

# 暴露端口只是“说明”，真正对外开放靠 docker run -p 或 compose ports
EXPOSE 8080

# 用 exec 形式启动，避免 shell 转发信号出问题（方便优雅停机）
ENTRYPOINT ["java","-jar","/app/app.jar"]