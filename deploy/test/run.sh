#!/usr/bin/env bash
set -euo pipefail

# 进入脚本所在目录，保证相对路径稳定
cd "$(dirname "$0")"

# === 你只需要确认这两个路径 ===
# 当前脚本在：blog-gateway-service/deploy/test
# 回到 blog-gateway-service 根目录：../..
SERVICE_ROOT="../.."

# jar 产物路径（相对于 SERVICE_ROOT）
JAR_REL_PATH="build/blog-gateway-service.jar"
JAR_PATH="${SERVICE_ROOT}/${JAR_REL_PATH}"

# 1) 每次都先构建：clean + package（跳过测试）
# 这样你每次跑脚本，都一定用的是最新 jar
MVN_SETTINGS="${HOME}/.m2/settings-github.xml"

echo "[INFO] build jar: mvn -s ${MVN_SETTINGS} clean package (skip tests)"
(cd "$SERVICE_ROOT" && mvn -q -s "$MVN_SETTINGS" -DskipTests clean package)

# 2) 构建后检查 jar 是否存在，避免构建失败你还继续往下跑
if [ ! -f "$JAR_PATH" ]; then
  echo "[ERROR] build finished but jar still not found: $JAR_PATH"
  exit 1
fi

# 3) 确保公共网络存在
if ! docker network inspect blog-net >/dev/null 2>&1; then
  echo "[INFO] create docker network: blog-net"
  docker network create blog-net >/dev/null
fi

# 4) 启动容器（compose 会通过 env_file 去读项目根目录的 .env）
echo "[INFO] docker compose up -d"
docker compose up -d

echo "[INFO] follow logs (Ctrl+C exit view)"
docker logs -f blog-gateway-service