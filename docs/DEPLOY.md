# 部署说明

## 1. 环境要求

| 组件 | 版本 | 说明 |
| --- | --- | --- |
| JDK | 17+ | 编译与运行 |
| Maven | 3.8+ | 服务端构建（也可用 `mvnw`） |
| Node.js | 18+ / 20+ | 前端构建 |
| MySQL | 8.0+ | 业务库，字符集 `utf8mb4` |
| Redis | 5.0+ | 缓存 / 限流 / 黑名单（可选，缺失时业务自动降级） |
| Nginx | 1.20+ | 生产环境静态资源与反向代理 |

---

## 2. 环境变量

服务端全部配置都可以用环境变量覆盖（`application.yml` 中已声明默认值）：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SERVER_PORT` | `8080` | 服务端口 |
| `SPRING_PROFILES_ACTIVE` | `dev` | `dev`（规则 AI + 演示数据）/ `llm`（接入大模型） |
| `MYSQL_HOST` / `MYSQL_PORT` | `localhost` / `3306` | 数据库地址 |
| `MYSQL_DB` | `jizhangbao` | 数据库名 |
| `MYSQL_USER` / `MYSQL_PASSWORD` | `root` / `603605` | 数据库账号（**生产环境务必修改**） |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | 缓存地址 |
| `REDIS_PASSWORD` / `REDIS_DB` | 空 / `0` | 缓存认证与库号 |
| `JWT_SECRET` | 内置示例值 | **生产环境必须替换为 ≥ 32 字节随机串** |
| `AI_MODE` | `rule` | `rule` / `llm` |
| `OPENAI_API_KEY` | 空 | 大模型 Key（`llm` 模式必填） |
| `OPENAI_BASE_URL` | `https://api.deepseek.com` | OpenAI 兼容地址 |
| `OPENAI_MODEL` | `deepseek-chat` | 模型名 |
| `AI_PROVIDER` | `deepseek` | 仅用于前端展示 |
| `CORS_ALLOWED_ORIGINS` | `*` | 允许的跨域来源，生产建议写具体域名 |
| `DEMO_DATA_ENABLED` | `true` | 是否写入演示数据，**生产请设为 false** |

---

## 3. 后端部署

### 3.1 构建

```bash
cd server
mvn clean package -DskipTests
# 产物：target/jizhangbao-server.jar（约 60 MB，含内嵌 Tomcat）
```

### 3.2 启动

```bash
export MYSQL_HOST=127.0.0.1 MYSQL_USER=jzb MYSQL_PASSWORD='strong-pass'
export JWT_SECRET='请替换为至少32字节的随机字符串xxxxxxxxxxxx'
export DEMO_DATA_ENABLED=false

java -Xms512m -Xmx1024m -Dfile.encoding=UTF-8 \
     -jar jizhangbao-server.jar --spring.profiles.active=dev
```

首次启动会自动执行 `db/schema.sql` 建表（`CREATE TABLE IF NOT EXISTS`，幂等）。

### 3.3 systemd 示例

```ini
# /etc/systemd/system/jizhangbao.service
[Unit]
Description=JiZhangBao Server
After=network.target mysql.service redis.service

[Service]
Type=simple
User=www
WorkingDirectory=/opt/jizhangbao
EnvironmentFile=/opt/jizhangbao/.env
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -Dfile.encoding=UTF-8 \
          -jar /opt/jizhangbao/jizhangbao-server.jar --spring.profiles.active=dev
SuccessExitStatus=143
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now jizhangbao
journalctl -u jizhangbao -f
```

### 3.4 建库与最小权限账号

```sql
CREATE DATABASE jizhangbao DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER 'jzb'@'%' IDENTIFIED BY 'strong-pass';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, INDEX, ALTER ON jizhangbao.* TO 'jzb'@'%';
FLUSH PRIVILEGES;
```

---

## 4. 前端部署

### 4.1 构建

```bash
cd web-mobile && npm ci && npm run build   # 产物 dist/
cd ../web-pc  && npm ci && npm run build   # 产物 dist/
```

### 4.2 Nginx 配置（两站点 + 同一后端）

```nginx
# ---------- 移动端 ----------
server {
    listen 80;
    server_name m.jizhangbao.example.com;
    root /opt/jizhangbao/web-mobile/dist;
    index index.html;

    # history 路由回退
    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

        # SSE 流式问答必须关闭缓冲
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 300s;
        chunked_transfer_encoding on;
    }
}

# ---------- PC / 后台 ----------
server {
    listen 80;
    server_name admin.jizhangbao.example.com;
    root /opt/jizhangbao/web-pc/dist;
    index index.html;

    location / {
        try_files $uri $uri/ /index.html;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;

        proxy_buffering off;
        proxy_read_timeout 300s;
    }
}
```

> **关键点**：AI 问答是 SSE，Nginx 必须设置 `proxy_buffering off`，否则前端会等到全部生成完才一次性收到。

### 4.3 本地开发代理

两个前端已内置 Vite proxy（`/api` → `http://127.0.0.1:8080`），本地无需额外配置跨域。

---

## 5. Docker 方式

仓库自带仅含依赖的 `docker-compose.yml`：

```bash
docker compose up -d          # MySQL 8.0 + Redis 7
docker compose ps
```

应用本体建议本地 `java -jar` 或自行打包镜像。若要容器化后端，可用如下 Dockerfile：

```dockerfile
# server/Dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
ENV TZ=Asia/Shanghai LANG=C.UTF-8
COPY --from=build /build/target/jizhangbao-server.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-Dfile.encoding=UTF-8", "-jar", "/app/app.jar"]
```

```bash
docker build -t jizhangbao-server ./server
docker run -d --name jizhangbao-server -p 8080:8080 \
  -e MYSQL_HOST=host.docker.internal -e MYSQL_PASSWORD=603605 \
  -e REDIS_HOST=host.docker.internal -e DEMO_DATA_ENABLED=false \
  jizhangbao-server
```

---

## 6. 上线检查清单

- [ ] `JWT_SECRET` 已替换为随机长串（≥ 32 字节）
- [ ] `MYSQL_PASSWORD` 已改为强密码，且账号为最小权限
- [ ] `DEMO_DATA_ENABLED=false`，并清理演示账号 `demo` / `admin`
- [ ] `CORS_ALLOWED_ORIGINS` 收敛为具体域名
- [ ] Redis 设置了 `requirepass` 并只监听内网
- [ ] Nginx 对 SSE 路径关闭代理缓冲
- [ ] MySQL 已配置备份（`mysqldump` 每日）
- [ ] 监控 `/api/meta/health` 与 `/actuator/health`
- [ ] `llm` 模式下已核验 Key 额度与超时（`jizhangbao.ai.timeout-seconds`）

---

## 7. 常见问题

**Q：启动报 `Unknown database 'jizhangbao'`**
先创建库：`CREATE DATABASE jizhangbao DEFAULT CHARACTER SET utf8mb4;`，表结构应用会自动建。

**Q：启动报 `Column 'year_month' not found`**
旧版本脚本建的库与当前实体不匹配，请删库重建（`db/schema.sql` 已把 `YEAR_MONTH` 这类 MySQL 保留字改成
`bill_month` / `is_system`）。

**Q：Redis 没装，能用吗**
可以。`CacheHelper` 捕获所有 Redis 异常，缓存与限流自动失效，业务功能不受影响。

**Q：AI 问答没有流式效果**
检查 Nginx 是否对 `/api/` 设置了 `proxy_buffering off`。

**Q：想换成通义千问 / Moonshot**
把 `OPENAI_BASE_URL` 改成对应服务的 OpenAI 兼容地址、`OPENAI_MODEL` 改成对应模型名即可，代码无需改动。
