# GitHub Actions 实战教程与部署排障手册

## Spring Boot 自动构建并部署到 Ubuntu

版本：2.0  
整理日期：2026 年 8 月 3 日  
适用技术栈：Spring Boot · Maven · GitHub Actions · Ubuntu · SSH/SCP · systemd · Docker Compose

> 本手册将原《GitHub Actions 实战教程：Spring Boot 自动构建并部署到 Ubuntu》与后续真实排障过程合并整理。第一部分讲解从 CI 到生产部署的完整方法；第二部分针对实际项目 `personal-website-backend`，集中处理私钥、Base64、jar 文件名、工作流不一致和 Shell 续行等问题，并提供最终修正版配置。

## 阅读说明

- **第一部分：完整基础教程**——适合从零建立 CI/CD，示例名称使用通用占位符 `my-app`，Java 示例以教程中的 JDK 17 为主。
- **第二部分：实际问题排查与最终修正版**——适合当前项目落地，使用 JDK 21、`personal-website-backend` 和 Base64 SSH 私钥方案。
- 当两部分中的 SSH 私钥处理或 jar 命名示例不同时，**以第二部分的最终修正版为准**。
- 建议先按第一部分理解整体流程，再依据第二部分替换正式部署配置。

## 快速导航

1. 第一部分：完整基础教程
2. 第二部分：实际问题排查与最终修正版
3. 最终推荐工作流
4. 排查顺序与安全检查表

---

## 第一部分：完整基础教程

本部分保留原 Word 教程的完整章节、代码示例、表格和学习路径。涉及项目名、服务器目录和 Java 版本时，请根据实际项目替换。

### 目录

1. 目标与整体流程
2. 核心概念
3. 项目准备
4. 创建最简单的构建流程
5. Maven 生命周期与测试策略
6. 保存构建产物
7. 准备 Ubuntu 服务器
8. 配置专用 SSH 部署密钥
9. 配置 GitHub Secrets
10. 单独验证 SSH 连接
11. 使用脚本启动 Spring Boot
12. 完整的自动部署工作流
13. 增加应用健康检查
14. 使用 systemd 管理服务
15. 备份与自动回滚
16. Docker Compose 部署方案
17. 区分测试与生产环境
18. 拆分 CI 与 CD
19. 常见触发方式
20. Maven 私服配置
21. 安全基线
22. 常见问题排查与最终方案

---

### 1. 目标与整体流程

完成本教程后，每次向 `main` 分支推送代码，GitHub Actions 将自动完成：

```text
本地提交代码
    ↓
push 到 GitHub main 分支
    ↓
GitHub Actions 拉取代码
    ↓
安装 JDK、缓存 Maven 依赖
    ↓
执行测试并打包
    ↓
通过 SSH 上传 jar 到 Ubuntu
    ↓
执行部署或重启命令
    ↓
健康检查确认应用可用
```

推荐循序渐进地实现：

1. 只运行 Maven 构建与测试。
2. 保存并下载构建产物。
3. 验证 GitHub Runner 能通过 SSH 登录服务器。
4. 上传 jar 并重启服务。
5. 增加健康检查。
6. 增加备份和自动回滚。
7. 需要时再升级为 Docker 镜像部署和多环境发布。

### 2. 核心概念

GitHub Actions 的工作流文件存放在项目的 `.github/workflows/` 目录中，以 YAML 描述触发条件、任务和执行步骤。

```yaml
name: Spring Boot CI

on:
  push:
    branches:
      - main

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout
        uses: actions/checkout@v6

      - name: Build
        run: mvn clean package
```

| 关键词 | 含义 |
|---|---|
| `workflow` | 一整套自动化流程，由一个 YAML 文件定义 |
| `on` | 工作流的触发条件 |
| `jobs` | 工作流中要执行的任务 |
| `runner` | 执行任务的机器或运行环境 |
| `steps` | 一个任务内按顺序执行的步骤 |
| `uses` | 调用已封装的 Action |
| `run` | 直接执行 Shell 命令 |
| `secrets` | 密码、私钥、令牌等敏感变量 |

它们的关系可以理解为：

```text
Workflow
├── Job：build
│   ├── Step：拉取代码
│   ├── Step：安装 JDK
│   └── Step：Maven 构建与测试
│
└── Job：deploy
    ├── Step：上传 jar
    ├── Step：重启服务
    └── Step：健康检查
```

### 3. 项目准备

假设 Spring Boot 项目结构如下：

```text
my-springboot-app/
├── .github/
│   └── workflows/
├── src/
├── pom.xml
├── mvnw
├── mvnw.cmd
└── README.md
```

在添加自动化之前，先确保项目在本地可以成功构建：

```bash
mvn clean package
```

如果项目提交了 Maven Wrapper，优先使用：

```bash
./mvnw clean package
```

构建成功后，`target` 目录中应出现可运行的 jar，例如：

```text
target/my-app-1.0.0.jar
```

> 实践原则：先跑通 CI 构建，再增加 CD 部署。这样失败时更容易定位是代码构建问题、网络问题，还是服务器配置问题。

### 4. 创建最简单的构建流程

在项目根目录创建：

```text
.github/workflows/ci.yml
```

写入以下内容：

```yaml
name: Spring Boot CI

on:
  push:
    branches:
      - main
  pull_request:
    branches:
      - main

permissions:
  contents: read

jobs:
  build:
    name: Build and Test
    runs-on: ubuntu-latest

    steps:
      - name: Checkout source code
        uses: actions/checkout@v6

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Build with Maven
        run: mvn --batch-mode clean verify
```

这份配置会在以下场景运行：

- 向 `main` 分支推送代码；
- 创建或更新目标为 `main` 的 Pull Request。

执行过程是：

```text
创建临时 Ubuntu Runner
→ 拉取代码
→ 安装 Temurin JDK 17
→ 缓存 Maven 依赖
→ 执行 clean verify
```

提交并推送工作流：

```bash
git add .github/workflows/ci.yml
git commit -m "ci: add GitHub Actions workflow"
git push origin main
```

打开 GitHub 仓库的 `Actions` 页面，进入 `Spring Boot CI`，看到绿色对勾即表示第一阶段完成。

### 5. Maven 生命周期与测试策略

开发时常见命令是：

```bash
mvn clean package -DskipTests
```

CI 环境更建议使用：

```bash
mvn clean verify
```

| 命令 | 主要行为 | 适用场景 |
|---|---|---|
| `mvn clean package` | 清理、编译、测试并生成 jar | 本地或基础构建 |
| `mvn clean verify` | 在 `package` 基础上继续执行验证阶段 | CI 默认推荐 |
| `mvn clean package -DskipTests` | 编译测试代码但跳过测试执行 | 临时排障或项目早期 |

测试尚未整理好时，可以临时使用：

```yaml
- name: Build with Maven
  run: mvn --batch-mode clean package -DskipTests
```

> 不建议长期跳过测试。否则流水线只是“自动打包”，无法及时拦截回归问题。

### 6. 保存构建产物

将 jar 保存为 Artifact，便于确认构建结果、手工下载，或在不同 Job 之间传递。

```yaml
name: Spring Boot CI

on:
  push:
    branches:
      - main
  pull_request:
    branches:
      - main

permissions:
  contents: read

jobs:
  build:
    name: Build and Test
    runs-on: ubuntu-latest

    steps:
      - name: Checkout source code
        uses: actions/checkout@v6

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Build with Maven
        run: mvn --batch-mode clean package

      - name: Upload jar
        uses: actions/upload-artifact@v4
        with:
          name: springboot-package
          path: target/*.jar
          if-no-files-found: error
          retention-days: 7
```

构建完成后，在本次运行页面底部可看到：

```text
Artifacts
└── springboot-package
```

如果项目会同时生成普通 jar、sources jar 或 original jar，最好固定最终文件名。在 `pom.xml` 中加入：

```xml
<build>
    <finalName>my-app</finalName>
</build>
```

工作流随后固定使用：

```yaml
path: target/my-app.jar
```

### 7. 准备 Ubuntu 服务器

本教程使用以下示例参数：

| 项目 | 示例值 |
|---|---|
| 操作系统 | Ubuntu |
| 部署目录 | `/opt/my-app` |
| 部署用户 | `deploy` |
| 应用端口 | `8080` |
| jar 名称 | `my-app.jar` |

创建专用部署用户：

```bash
sudo adduser deploy
```

创建应用目录并授权：

```bash
sudo mkdir -p /opt/my-app
sudo chown -R deploy:deploy /opt/my-app
```

切换到部署用户：

```bash
sudo su - deploy
```

确认 Java 环境：

```bash
java -version
```

如果尚未安装 Java 17 运行环境：

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless
```

生产服务器运行 Spring Boot jar 只需要 JRE，无需安装 Maven。

### 8. 配置专用 SSH 部署密钥

建议为 GitHub Actions 单独生成一套部署密钥，不要复用日常登录私钥。

在本地电脑执行：

```bash
ssh-keygen -t ed25519 \
  -C "github-actions-deploy" \
  -f ~/.ssh/github_actions_deploy
```

生成两个文件：

```text
~/.ssh/github_actions_deploy       # 私钥
~/.ssh/github_actions_deploy.pub   # 公钥
```

将公钥安装到服务器：

```bash
ssh-copy-id \
  -i ~/.ssh/github_actions_deploy.pub \
  deploy@服务器地址
```

如果没有 `ssh-copy-id`，可以将公钥内容手工追加到服务器的：

```text
/home/deploy/.ssh/authorized_keys
```

并检查权限：

```bash
mkdir -p ~/.ssh
chmod 700 ~/.ssh
chmod 600 ~/.ssh/authorized_keys
```

在本地验证免密登录：

```bash
ssh \
  -i ~/.ssh/github_actions_deploy \
  deploy@服务器地址
```

只有此命令成功后，才继续配置 GitHub Secrets。

### 9. 配置 GitHub Secrets

进入仓库：

```text
Settings
→ Secrets and variables
→ Actions
→ New repository secret
```

添加以下 Secret：

| Secret 名称 | 内容 |
|---|---|
| `SERVER_HOST` | 服务器 IP 或域名 |
| `SERVER_PORT` | SSH 端口，通常为 `22` |
| `SERVER_USER` | `deploy` |
| `SERVER_SSH_KEY` | SSH 私钥的完整内容 |
| `SERVER_PATH` | `/opt/my-app` |

私钥内容必须包括头尾：

```text
-----BEGIN OPENSSH PRIVATE KEY-----
……
-----END OPENSSH PRIVATE KEY-----
```

> 严禁把服务器密码、Token 或私钥直接写进 YAML 并提交到仓库。

### 10. 单独验证 SSH 连接

先用手动触发的独立工作流验证网络、密钥和用户权限。创建：

```text
.github/workflows/ssh-test.yml
```

```yaml
name: SSH Connection Test

on:
  workflow_dispatch:

permissions:
  contents: read

jobs:
  ssh-test:
    runs-on: ubuntu-latest

    steps:
      - name: Configure SSH
        env:
          SSH_PRIVATE_KEY: ${{ secrets.SERVER_SSH_KEY }}
          SERVER_HOST: ${{ secrets.SERVER_HOST }}
          SERVER_PORT: ${{ secrets.SERVER_PORT }}
        run: |
          mkdir -p ~/.ssh
          chmod 700 ~/.ssh

          printf '%s\n' "$SSH_PRIVATE_KEY" > ~/.ssh/deploy_key
          chmod 600 ~/.ssh/deploy_key

          ssh-keyscan -p "$SERVER_PORT" "$SERVER_HOST" \
            >> ~/.ssh/known_hosts
          chmod 600 ~/.ssh/known_hosts

      - name: Test SSH connection
        env:
          SERVER_HOST: ${{ secrets.SERVER_HOST }}
          SERVER_PORT: ${{ secrets.SERVER_PORT }}
          SERVER_USER: ${{ secrets.SERVER_USER }}
        run: |
          ssh \
            -o ConnectTimeout=20 \
            -i ~/.ssh/deploy_key \
            -p "$SERVER_PORT" \
            "$SERVER_USER@$SERVER_HOST" \
            "echo 'SSH connection successful' && hostname && whoami"
```

在 GitHub 仓库中进入：

```text
Actions
→ SSH Connection Test
→ Run workflow
```

日志中出现以下内容即表示成功：

```text
SSH connection successful
服务器主机名
deploy
```

> 安全提示：`ssh-keyscan` 适合快速验证，但生产环境应预先核对并保存服务器真实 Host Key，避免首次连接时受到中间人攻击。

### 11. 使用脚本启动 Spring Boot

先用容易理解的 `nohup + PID` 方案。登录服务器后，在 `/opt/my-app` 创建 `deploy.sh`：

```bash
#!/usr/bin/env bash

set -euo pipefail

APP_DIR="/opt/my-app"
APP_NAME="my-app"
JAR_FILE="$APP_DIR/my-app.jar"
LOG_FILE="$APP_DIR/app.log"
PID_FILE="$APP_DIR/app.pid"

cd "$APP_DIR"

if [ ! -f "$JAR_FILE" ]; then
    echo "错误：找不到 $JAR_FILE"
    exit 1
fi

if [ -f "$PID_FILE" ]; then
    OLD_PID="$(cat "$PID_FILE")"

    if kill -0 "$OLD_PID" 2>/dev/null; then
        echo "停止旧服务，PID：$OLD_PID"
        kill "$OLD_PID"

        for i in {1..30}; do
            if ! kill -0 "$OLD_PID" 2>/dev/null; then
                break
            fi
            sleep 1
        done

        if kill -0 "$OLD_PID" 2>/dev/null; then
            echo "旧服务未正常停止，强制结束"
            kill -9 "$OLD_PID"
        fi
    fi
fi

echo "启动 $APP_NAME"

nohup java \
    -Xms256m \
    -Xmx512m \
    -jar "$JAR_FILE" \
    --spring.profiles.active=prod \
    >> "$LOG_FILE" 2>&1 &

NEW_PID=$!
echo "$NEW_PID" > "$PID_FILE"

sleep 5

if kill -0 "$NEW_PID" 2>/dev/null; then
    echo "部署成功，PID：$NEW_PID"
else
    echo "部署失败"
    tail -n 100 "$LOG_FILE"
    exit 1
fi
```

添加执行权限：

```bash
chmod +x /opt/my-app/deploy.sh
```

这个版本适合学习和验证。长期生产运行建议使用第 14 章的 `systemd` 方案。

### 12. 完整的自动部署工作流

创建：

```text
.github/workflows/deploy.yml
```

```yaml
name: Build and Deploy Spring Boot

on:
  push:
    branches:
      - main
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: production-deploy
  cancel-in-progress: false

jobs:
  build-and-deploy:
    name: Build and Deploy
    runs-on: ubuntu-latest

    steps:
      - name: Checkout source code
        uses: actions/checkout@v6

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Build with Maven
        run: mvn --batch-mode clean package

      - name: Check jar
        run: |
          ls -lh target
          test -f target/my-app.jar

      - name: Configure SSH
        env:
          SSH_PRIVATE_KEY: ${{ secrets.SERVER_SSH_KEY }}
          SERVER_HOST: ${{ secrets.SERVER_HOST }}
          SERVER_PORT: ${{ secrets.SERVER_PORT }}
        run: |
          mkdir -p ~/.ssh
          chmod 700 ~/.ssh

          printf '%s\n' "$SSH_PRIVATE_KEY" > ~/.ssh/deploy_key
          chmod 600 ~/.ssh/deploy_key

          ssh-keyscan -p "$SERVER_PORT" "$SERVER_HOST" \
            >> ~/.ssh/known_hosts
          chmod 600 ~/.ssh/known_hosts

      - name: Upload jar
        env:
          SERVER_HOST: ${{ secrets.SERVER_HOST }}
          SERVER_PORT: ${{ secrets.SERVER_PORT }}
          SERVER_USER: ${{ secrets.SERVER_USER }}
          SERVER_PATH: ${{ secrets.SERVER_PATH }}
        run: |
          scp \
            -o ConnectTimeout=20 \
            -i ~/.ssh/deploy_key \
            -P "$SERVER_PORT" \
            target/my-app.jar \
            "$SERVER_USER@$SERVER_HOST:$SERVER_PATH/my-app.jar.new"

      - name: Deploy application
        env:
          SERVER_HOST: ${{ secrets.SERVER_HOST }}
          SERVER_PORT: ${{ secrets.SERVER_PORT }}
          SERVER_USER: ${{ secrets.SERVER_USER }}
          SERVER_PATH: ${{ secrets.SERVER_PATH }}
        run: |
          ssh \
            -o ConnectTimeout=20 \
            -o ServerAliveInterval=15 \
            -o ServerAliveCountMax=3 \
            -i ~/.ssh/deploy_key \
            -p "$SERVER_PORT" \
            "$SERVER_USER@$SERVER_HOST" \
            "mv '$SERVER_PATH/my-app.jar.new' \
              '$SERVER_PATH/my-app.jar' && \
              '$SERVER_PATH/deploy.sh'"
```

先上传为 `my-app.jar.new`，上传完成后再执行原子重命名，可避免网络中断时直接损坏正式 jar。

### 13. 增加应用健康检查

检查 Java 进程存在，并不能证明 Spring Boot 已经成功启动。推荐加入 Actuator。

在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

在 `application-prod.yml` 中配置：

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
```

健康检查地址：

```text
http://127.0.0.1:8080/actuator/health
```

在启动脚本中加入轮询：

```bash
echo "等待服务启动"

for i in {1..30}; do
    if curl -fsS \
        "http://127.0.0.1:8080/actuator/health" \
        | grep -q '"status":"UP"'; then
        echo "服务健康检查成功"
        exit 0
    fi

    sleep 2
done

echo "服务健康检查失败"
tail -n 100 "$LOG_FILE"
exit 1
```

流水线只有在接口真正返回 `UP` 后才显示成功。

### 14. 使用 systemd 管理服务

生产环境推荐使用 `systemd`，它能提供开机自启、异常自动重启、统一日志和可靠的进程管理。

创建服务文件：

```text
/etc/systemd/system/my-app.service
```

内容如下：

```ini
[Unit]
Description=My Spring Boot Application
After=network.target

[Service]
Type=simple
User=deploy
Group=deploy
WorkingDirectory=/opt/my-app

ExecStart=/usr/bin/java \
  -Xms256m \
  -Xmx512m \
  -jar /opt/my-app/my-app.jar \
  --spring.profiles.active=prod

SuccessExitStatus=143
Restart=on-failure
RestartSec=5

StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

加载并启动：

```bash
sudo systemctl daemon-reload
sudo systemctl enable my-app
sudo systemctl start my-app
```

查看状态和日志：

```bash
sudo systemctl status my-app
journalctl -u my-app -f
```

部署用户默认无权重启系统服务。使用 `visudo` 创建最小权限规则：

```bash
sudo visudo -f /etc/sudoers.d/my-app-deploy
```

写入：

```text
deploy ALL=(root) NOPASSWD: /usr/bin/systemctl restart my-app
deploy ALL=(root) NOPASSWD: /usr/bin/systemctl status my-app
deploy ALL=(root) NOPASSWD: /usr/bin/journalctl -u my-app *
```

设置权限：

```bash
sudo chmod 440 /etc/sudoers.d/my-app-deploy
```

工作流中的部署命令可以改为：

```yaml
- name: Deploy application
  env:
    SERVER_HOST: ${{ secrets.SERVER_HOST }}
    SERVER_PORT: ${{ secrets.SERVER_PORT }}
    SERVER_USER: ${{ secrets.SERVER_USER }}
    SERVER_PATH: ${{ secrets.SERVER_PATH }}
  run: |
    ssh \
      -i ~/.ssh/deploy_key \
      -p "$SERVER_PORT" \
      "$SERVER_USER@$SERVER_HOST" \
      "mv '$SERVER_PATH/my-app.jar.new' \
        '$SERVER_PATH/my-app.jar' && \
        sudo systemctl restart my-app"
```

远程健康检查：

```yaml
- name: Health check
  env:
    SERVER_HOST: ${{ secrets.SERVER_HOST }}
    SERVER_PORT: ${{ secrets.SERVER_PORT }}
    SERVER_USER: ${{ secrets.SERVER_USER }}
  run: |
    ssh \
      -i ~/.ssh/deploy_key \
      -p "$SERVER_PORT" \
      "$SERVER_USER@$SERVER_HOST" \
      '
        for i in $(seq 1 30); do
          if curl -fsS \
            http://127.0.0.1:8080/actuator/health \
            | grep -q "\"status\":\"UP\""; then
            echo "Application is healthy"
            exit 0
          fi
          sleep 2
        done

        echo "Health check failed"
        sudo systemctl status my-app --no-pager
        exit 1
      '
```

### 15. 备份与自动回滚

在服务器创建 `/opt/my-app/release.sh`：

```bash
#!/usr/bin/env bash

set -euo pipefail

APP_DIR="/opt/my-app"
CURRENT_JAR="$APP_DIR/my-app.jar"
NEW_JAR="$APP_DIR/my-app.jar.new"
BACKUP_DIR="$APP_DIR/backup"
TIMESTAMP="$(date +%Y%m%d_%H%M%S)"

mkdir -p "$BACKUP_DIR"

if [ ! -f "$NEW_JAR" ]; then
    echo "找不到新版本：$NEW_JAR"
    exit 1
fi

if [ -f "$CURRENT_JAR" ]; then
    cp "$CURRENT_JAR" \
      "$BACKUP_DIR/my-app-$TIMESTAMP.jar"
fi

mv "$NEW_JAR" "$CURRENT_JAR"
sudo systemctl restart my-app

for i in $(seq 1 30); do
    if curl -fsS \
        http://127.0.0.1:8080/actuator/health \
        | grep -q '"status":"UP"'; then
        echo "部署成功"
        exit 0
    fi
    sleep 2
done

echo "新版本启动失败，准备回滚"

LATEST_BACKUP="$(
    find "$BACKUP_DIR" \
      -maxdepth 1 \
      -name 'my-app-*.jar' \
      -type f \
      | sort \
      | tail -n 1
)"

if [ -z "$LATEST_BACKUP" ]; then
    echo "没有可用备份"
    exit 1
fi

cp "$LATEST_BACKUP" "$CURRENT_JAR"
sudo systemctl restart my-app

echo "已回滚到：$LATEST_BACKUP"
exit 1
```

添加执行权限：

```bash
chmod +x /opt/my-app/release.sh
```

工作流只需调用：

```yaml
- name: Deploy application
  env:
    SERVER_HOST: ${{ secrets.SERVER_HOST }}
    SERVER_PORT: ${{ secrets.SERVER_PORT }}
    SERVER_USER: ${{ secrets.SERVER_USER }}
    SERVER_PATH: ${{ secrets.SERVER_PATH }}
  run: |
    ssh \
      -i ~/.ssh/deploy_key \
      -p "$SERVER_PORT" \
      "$SERVER_USER@$SERVER_HOST" \
      "'$SERVER_PATH/release.sh'"
```

> 建议再增加备份保留策略，例如只保留最近 5～10 个版本，避免磁盘被历史 jar 占满。

### 16. Docker Compose 部署方案

应用已经 Docker 化时，可以改为：

```text
GitHub Actions
├── Maven 打包
├── 构建 Docker 镜像
├── 推送镜像仓库
└── SSH 到服务器执行 docker compose pull/up
```

项目根目录的 `Dockerfile`：

```dockerfile
FROM eclipse-temurin:17-jre

WORKDIR /app
COPY target/my-app.jar app.jar

EXPOSE 8080

ENTRYPOINT [
  "java",
  "-Xms256m",
  "-Xmx512m",
  "-jar",
  "/app/app.jar"
]
```

服务器上的 `compose.yml`：

```yaml
services:
  my-app:
    image: ghcr.io/你的GitHub用户名/my-app:latest
    container_name: my-app
    restart: unless-stopped

    ports:
      - "8080:8080"

    environment:
      SPRING_PROFILES_ACTIVE: prod

    volumes:
      - ./logs:/app/logs
```

构建镜像、推送到 GitHub Container Registry，并远程部署：

```yaml
name: Docker Build and Deploy

on:
  push:
    branches:
      - main

permissions:
  contents: read
  packages: write

env:
  IMAGE_NAME: ghcr.io/${{ github.repository_owner }}/my-app

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout source code
        uses: actions/checkout@v6

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Build jar
        run: mvn --batch-mode clean package

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build and push Docker image
        uses: docker/build-push-action@v6
        with:
          context: .
          push: true
          tags: |
            ${{ env.IMAGE_NAME }}:latest
            ${{ env.IMAGE_NAME }}:${{ github.sha }}

      - name: Configure SSH
        env:
          SSH_PRIVATE_KEY: ${{ secrets.SERVER_SSH_KEY }}
          SERVER_HOST: ${{ secrets.SERVER_HOST }}
          SERVER_PORT: ${{ secrets.SERVER_PORT }}
        run: |
          mkdir -p ~/.ssh
          printf '%s\n' "$SSH_PRIVATE_KEY" \
            > ~/.ssh/deploy_key
          chmod 600 ~/.ssh/deploy_key
          ssh-keyscan -p "$SERVER_PORT" "$SERVER_HOST" \
            >> ~/.ssh/known_hosts

      - name: Deploy
        env:
          SERVER_HOST: ${{ secrets.SERVER_HOST }}
          SERVER_PORT: ${{ secrets.SERVER_PORT }}
          SERVER_USER: ${{ secrets.SERVER_USER }}
        run: |
          ssh \
            -i ~/.ssh/deploy_key \
            -p "$SERVER_PORT" \
            "$SERVER_USER@$SERVER_HOST" \
            "cd /opt/my-app && \
              docker compose pull && \
              docker compose up -d"
```

GitHub 会在每次工作流运行时提供 `GITHUB_TOKEN`。仍应通过 `permissions` 只授予所需权限。

> 国内服务器直接拉取 GHCR 或 Docker Hub 镜像可能受到跨境网络影响。若不稳定，可使用国内容器镜像服务、自建 Harbor，或继续采用“Actions 构建 jar + SCP 上传”的方案。

### 17. 区分测试与生产环境

建议使用 GitHub Environments：

```text
Settings
→ Environments
→ New environment
```

创建：

```text
test
production
```

把生产服务器 Secret 配置到 `production` Environment，而不是全部放在仓库级 Secrets。

```yaml
jobs:
  deploy:
    environment: production
    runs-on: ubuntu-latest
```

Environment 可以用于：

- 隔离测试和生产凭据；
- 限制允许部署的分支；
- 配置人工审批；
- 保存环境级部署历史；
- 为不同环境设置不同变量。

### 18. 拆分 CI 与 CD

不建议让所有分支都直接部署生产。将构建验证和生产发布拆成两个工作流。

`.github/workflows/ci.yml`：

```yaml
name: CI

on:
  push:
    branches:
      - '**'
  pull_request:

permissions:
  contents: read

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v6

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - run: mvn --batch-mode clean verify
```

`.github/workflows/deploy.yml`：

```yaml
name: Production Deploy

on:
  push:
    branches:
      - main
  workflow_dispatch:

permissions:
  contents: read

jobs:
  deploy:
    environment: production
    runs-on: ubuntu-latest

    steps:
      # 构建和部署步骤
```

最终行为：

```text
普通分支
└── 构建与测试

Pull Request
└── 构建与测试

main
├── 构建与测试
└── 部署生产
```

### 19. 常见触发方式

#### 19.1 推送到 main

```yaml
on:
  push:
    branches:
      - main
```

#### 19.2 推送指定标签

```yaml
on:
  push:
    tags:
      - 'v*'
```

适合使用 `v1.0.0`、`v1.0.1`、`v2.0.0` 等版本标签发布。

#### 19.3 手动运行

```yaml
on:
  workflow_dispatch:
```

#### 19.4 定时运行

```yaml
on:
  schedule:
    - cron: '0 18 * * *'
```

GitHub Actions 的 cron 使用 UTC。该示例对应中国时间次日 02:00。

#### 19.5 只在后端文件变化时触发

```yaml
on:
  push:
    branches:
      - main
    paths:
      - 'src/**'
      - 'pom.xml'
      - '.github/workflows/deploy.yml'
```

### 20. Maven 私服配置

项目依赖公司 Nexus 时，可以在工作流中动态生成 `settings.xml`。先创建以下 Secrets：

- `MAVEN_USERNAME`
- `MAVEN_PASSWORD`

工作流步骤：

```yaml
- name: Create Maven settings
  env:
    MAVEN_USERNAME: ${{ secrets.MAVEN_USERNAME }}
    MAVEN_PASSWORD: ${{ secrets.MAVEN_PASSWORD }}
  run: |
    mkdir -p ~/.m2

    cat > ~/.m2/settings.xml <<EOF
    <settings>
      <servers>
        <server>
          <id>company-nexus</id>
          <username>${MAVEN_USERNAME}</username>
          <password>${MAVEN_PASSWORD}</password>
        </server>
      </servers>

      <mirrors>
        <mirror>
          <id>company-nexus</id>
          <mirrorOf>*</mirrorOf>
          <url>
            https://nexus.example.com/repository/maven-public/
          </url>
        </mirror>
      </mirrors>
    </settings>
    EOF
```

执行构建：

```yaml
- name: Build
  run: >
    mvn --batch-mode
    --settings ~/.m2/settings.xml
    clean package
```

如果 GitHub 托管 Runner 无法访问内网 Nexus，需要 VPN、专线、对外可访问的 Nexus，或 Self-hosted Runner。

### 21. 安全基线

#### 21.1 不使用 root 部署

创建专用 `deploy` 用户，并只授予：

- 写入应用目录的权限；
- 重启指定 systemd 服务的权限；
- 查看指定服务状态和日志的权限。

#### 21.2 使用专用 SSH 密钥

不要使用服务器登录密码。建议关闭密码登录与 root 远程登录：

```text
PasswordAuthentication no
PermitRootLogin no
```

修改 SSH 配置前，先确认密钥登录正常，避免把自己锁在服务器外。

#### 21.3 限制 Actions 权限

默认只读：

```yaml
permissions:
  contents: read
```

只有推送镜像时才增加：

```yaml
permissions:
  contents: read
  packages: write
```

#### 21.4 谨慎使用第三方 Action

第三方 Action 会在 Runner 中执行代码。生产工作流应：

- 优先选择官方或可信维护者；
- 审核来源和权限；
- 固定大版本，严格环境可固定到具体 commit SHA；
- 避免为整个工作流授予过宽权限；
- 尽量减少不必要的第三方依赖。

#### 21.5 不输出 Secret

不要执行：

```bash
echo "${{ secrets.SERVER_SSH_KEY }}"
```

日志脱敏不能替代正确的安全设计。

#### 21.6 固定服务器 Host Key

生产环境应保存已核验的服务器 Host Key，而不是每次无条件执行 `ssh-keyscan` 并信任结果。

### 22. 常见问题排查与最终方案

#### 22.1 `Permission denied (publickey)`

检查服务器权限：

```bash
chmod 700 ~/.ssh
chmod 600 ~/.ssh/authorized_keys
```

确认：

- GitHub Secret 中保存的是私钥；
- 服务器 `authorized_keys` 中保存的是公钥；
- `deploy` 用户拥有对应的 home 目录和文件；
- 本地能使用同一私钥登录。

#### 22.2 `Host key verification failed`

说明 `known_hosts` 没有配置，或服务器 Host Key 已变化。快速验证可使用：

```bash
ssh-keyscan -p "$SERVER_PORT" "$SERVER_HOST" \
  >> ~/.ssh/known_hosts
```

生产环境应核对真实指纹后固定保存。

#### 22.3 找不到 `target/my-app.jar`

临时列出构建目录：

```yaml
- name: List target
  run: find target -maxdepth 2 -type f -print
```

再检查 `pom.xml` 是否设置：

```xml
<finalName>my-app</finalName>
```

#### 22.4 `mvn: command not found`

优先提交 Maven Wrapper：

```bash
mvn wrapper:wrapper
```

工作流改为：

```yaml
- name: Build
  run: |
    chmod +x mvnw
    ./mvnw --batch-mode clean package
```

#### 22.5 Maven 依赖下载慢

确认 `setup-java` 启用了：

```yaml
cache: maven
```

如需镜像仓库，应使用可信来源并明确版本与可用性。

#### 22.6 SSH 偶发超时

增加连接参数：

```bash
ssh \
  -o ConnectTimeout=20 \
  -o ServerAliveInterval=15 \
  -o ServerAliveCountMax=3 \
  ...
```

`scp` 同样可以增加 `ConnectTimeout`。

#### 22.7 重启命令成功，但应用不可用

`systemctl restart` 成功只代表命令已执行，不代表应用已就绪。必须增加 Actuator 健康检查，并在失败时输出服务状态和最近日志。

#### 22.8 多次提交导致重复部署

使用并发控制：

```yaml
concurrency:
  group: production-deploy
  cancel-in-progress: false
```

生产部署通常不建议执行到一半时被强制取消，因此默认使用 `false`。

#### 22.9 推荐的最终架构

```text
GitHub Actions 托管 Runner
├── Checkout
├── JDK 17
├── Maven 缓存
├── 单元测试
├── 打包 jar
├── SSH/SCP 上传
└── 调用服务器 release.sh

Ubuntu
├── deploy 普通用户
├── /opt/my-app
├── systemd 管理服务
├── Actuator 健康检查
└── 保留上一版 jar
```

最简最终工作流：

```yaml
name: Production Deploy

on:
  push:
    branches:
      - main
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: production-deploy
  cancel-in-progress: false

jobs:
  deploy:
    name: Build and Deploy
    runs-on: ubuntu-latest
    environment: production

    steps:
      - name: Checkout source code
        uses: actions/checkout@v6

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
          cache: maven

      - name: Build and test
        run: mvn --batch-mode clean verify

      - name: Check package
        run: test -f target/my-app.jar

      - name: Configure SSH
        env:
          SSH_PRIVATE_KEY: ${{ secrets.SERVER_SSH_KEY }}
          SERVER_HOST: ${{ secrets.SERVER_HOST }}
          SERVER_PORT: ${{ secrets.SERVER_PORT }}
        run: |
          mkdir -p ~/.ssh
          chmod 700 ~/.ssh

          printf '%s\n' "$SSH_PRIVATE_KEY" \
            > ~/.ssh/deploy_key
          chmod 600 ~/.ssh/deploy_key

          ssh-keyscan \
            -p "$SERVER_PORT" \
            "$SERVER_HOST" \
            >> ~/.ssh/known_hosts
          chmod 600 ~/.ssh/known_hosts

      - name: Upload package
        env:
          SERVER_HOST: ${{ secrets.SERVER_HOST }}
          SERVER_PORT: ${{ secrets.SERVER_PORT }}
          SERVER_USER: ${{ secrets.SERVER_USER }}
          SERVER_PATH: ${{ secrets.SERVER_PATH }}
        run: |
          scp \
            -o ConnectTimeout=20 \
            -i ~/.ssh/deploy_key \
            -P "$SERVER_PORT" \
            target/my-app.jar \
            "$SERVER_USER@$SERVER_HOST:$SERVER_PATH/my-app.jar.new"

      - name: Release
        env:
          SERVER_HOST: ${{ secrets.SERVER_HOST }}
          SERVER_PORT: ${{ secrets.SERVER_PORT }}
          SERVER_USER: ${{ secrets.SERVER_USER }}
          SERVER_PATH: ${{ secrets.SERVER_PATH }}
        run: |
          ssh \
            -o ConnectTimeout=20 \
            -o ServerAliveInterval=15 \
            -o ServerAliveCountMax=3 \
            -i ~/.ssh/deploy_key \
            -p "$SERVER_PORT" \
            "$SERVER_USER@$SERVER_HOST" \
            "'$SERVER_PATH/release.sh'"
```

#### 学习顺序检查表

- [ ] Maven 构建在本地成功。
- [ ] GitHub Actions 能运行 `clean verify`。
- [ ] 构建产物可作为 Artifact 下载。
- [ ] 独立 SSH 测试工作流通过。
- [ ] jar 可上传为 `.new` 文件。
- [ ] systemd 能以 `deploy` 用户运行应用。
- [ ] Actuator 健康检查返回 `UP`。
- [ ] 新版本失败时能自动回滚。
- [ ] 生产 Secret 已迁移到 `production` Environment。
- [ ] 工作流权限、SSH 权限和 sudo 权限均遵循最小权限原则。

> 最后建议：不要第一天就把 Docker、镜像仓库、Nginx、数据库迁移、回滚和审批全部塞进一份 YAML。先让绿色对勾出现，再逐步把流水线养成一条可靠的发布链路。

---

## 第二部分：实际问题排查与最终修正版

> 适用项目：`personal-website-backend`  
> Java 版本：21  
> 部署方式：GitHub Actions 托管 Runner → SSH/SCP → Ubuntu

本部分来自实际运行日志，给出的最终配置会覆盖第一部分中的通用 SSH 私钥和 jar 命名示例。

### 1. 文档目标

本文整理实际配置 GitHub Actions 自动部署过程中遇到的几类问题，并给出一份可以直接修改使用的最终工作流。

完整链路如下：

```text
push main
    ↓
GitHub Actions 拉取代码
    ↓
使用 JDK 21 和 Maven 构建
    ↓
自动定位可执行 jar
    ↓
从 Base64 Secret 还原 SSH 私钥
    ↓
验证 SSH 连接
    ↓
SCP 上传 jar.new
    ↓
调用服务器 release.sh 发布
```

### 2. 本次遇到的问题总览

| 报错 | 直接原因 | 修复重点 |
|---|---|---|
| `Load key ...: error in libcrypto` | Runner 中的私钥文件格式无效 | 使用 Base64 Secret，还原后用 `ssh-keygen` 验证 |
| `base64: invalid input` | Secret 中不是有效的 Base64 内容 | 在 Mac 上重新单行编码，只复制编码结果 |
| `Process completed with exit code 1`（Check jar） | 工作流检查的 jar 名称不存在 | 固定 Maven 产物名，或自动定位 jar |
| 上传时再次出现 `error in libcrypto` | 正式部署工作流仍使用旧私钥写法 | 同时修改 `ssh-test.yml` 和 `deploy.yml` |
| `line 20: -: command not found` | Shell 中存在多余的 `-` 或错误续行 | 替换整个 SSH 配置步骤，检查反斜杠和缩进 |
| `Permission denied (publickey)` | 公私钥不匹配或服务器权限错误 | 检查 `authorized_keys` 及目录权限 |

---

### 3. 问题一：`error in libcrypto`

#### 3.1 报错现象

```text
Load key "/home/runner/.ssh/deploy_key": error in libcrypto
Permission denied (publickey,password).
Process completed with exit code 255.
```

#### 3.2 原因

第一行才是根本原因：OpenSSH 无法解析生成的私钥文件。常见情况包括：

- GitHub Secret 中放成了 `.pub` 公钥；
- 复制私钥时丢失换行；
- 私钥包含 Windows 风格的 `\r`；
- 使用了 PuTTY `.ppk` 等非 OpenSSH 格式；
- 私钥带有密码，但流水线没有解密方式；
- 测试工作流已经修复，但正式部署工作流仍使用旧配置。

后面的 `Permission denied` 只是私钥加载失败后的结果。

#### 3.3 推荐修复：Base64 保存私钥

在 Mac 上执行：

```bash
openssl base64 -A \
  -in ~/.ssh/github_actions_deploy \
  | pbcopy
```

然后在 GitHub 仓库中进入：

```text
Settings
→ Secrets and variables
→ Actions
→ New repository secret
```

创建：

```text
Name:  SERVER_SSH_KEY_B64
Value: 直接粘贴剪贴板内容
```

正确的 Secret 应满足：

- 只有一行；
- 不带引号；
- 不包含空格；
- 不包含终端提示符和执行命令；
- 不包含 `-----BEGIN OPENSSH PRIVATE KEY-----`；
- OpenSSH 私钥编码后通常以 `LS0tLS1CRUdJTi...` 开头。

> 建议为自动部署单独生成一把无密码、低权限密钥，不要使用日常管理员私钥。

---

### 4. 问题二：`base64: invalid input`

#### 4.1 报错现象

```text
base64: invalid input
Process completed with exit code 1.
```

#### 4.2 原因

`SERVER_SSH_KEY_B64` 中并不是纯 Base64，可能误放了：

- 原始私钥；
- 私钥头尾；
- 命令本身；
- 引号或终端提示符；
- 其他说明文字。

#### 4.3 修复方法

重新执行下面的命令，并直接通过 `pbcopy` 写入剪贴板：

```bash
openssl base64 -A -in ~/.ssh/github_actions_deploy | pbcopy
```

随后完整覆盖 GitHub 中的 `SERVER_SSH_KEY_B64`，不要在内容前后添加引号。

工作流解码时可以先删除不可见空白字符：

```bash
printf '%s' "$SSH_PRIVATE_KEY_B64" \
  | tr -d '[:space:]' \
  | base64 --decode \
  > ~/.ssh/deploy_key
```

---

### 5. 问题三：构建成功但 Check jar 失败

#### 5.1 报错现象

Maven 已成功生成：

```text
target/personal-website-backend-0.1.0.jar
```

但工作流执行：

```bash
test -f target/my-app.jar
```

最后返回：

```text
Process completed with exit code 1.
```

#### 5.2 原因

`test -f` 检查的文件名与 Maven 实际产物不一致。`ls` 可以成功列出目录，但随后的 `test -f` 会因为文件不存在而返回退出码 1。

#### 5.3 方案一：固定 Maven 产物名

在 `pom.xml` 中设置：

```xml
<build>
    <finalName>personal-website-backend</finalName>

    <!-- 原有 plugins 配置继续放在这里 -->
</build>
```

产物将固定为：

```text
target/personal-website-backend.jar
```

检查步骤可写为：

```yaml
- name: Check jar
  run: test -f target/personal-website-backend.jar
```

#### 5.4 方案二：自动定位 jar（推荐）

为了避免版本号变化导致工作流失败，可以自动查找可执行 jar：

```yaml
- name: Locate jar
  run: |
    JAR_FILE="$(find target \
      -maxdepth 1 \
      -type f \
      -name '*.jar' \
      ! -name '*.jar.original' \
      ! -name '*-sources.jar' \
      ! -name '*-javadoc.jar' \
      -print \
      -quit)"

    if [ -z "$JAR_FILE" ]; then
      echo "No executable jar found"
      exit 1
    fi

    echo "Found jar: $JAR_FILE"
    echo "JAR_FILE=$JAR_FILE" >> "$GITHUB_ENV"
```

后续步骤统一使用：

```bash
"$JAR_FILE"
```

---

### 6. 问题四：SSH 测试成功，正式上传仍然失败

#### 6.1 原因

测试连接与正式部署通常是两个独立工作流：

```text
.github/workflows/ssh-test.yml
.github/workflows/deploy.yml
```

只修改 `ssh-test.yml` 不会自动修改 `deploy.yml`。如果正式工作流仍使用：

```yaml
SSH_PRIVATE_KEY: ${{ secrets.SERVER_SSH_KEY }}
```

或者：

```bash
printf '%s\n' "$SSH_PRIVATE_KEY" > ~/.ssh/deploy_key
```

上传步骤仍然会出现 `error in libcrypto`。

#### 6.2 修复方法

检查 `.github/workflows/` 下的全部 YAML：

```bash
rg 'SERVER_SSH_KEY|deploy_key' .github/workflows
```

所有部署工作流统一使用：

```yaml
SSH_PRIVATE_KEY_B64: ${{ secrets.SERVER_SSH_KEY_B64 }}
```

同时确保后续步骤没有再次覆盖 `~/.ssh/deploy_key`。

---

### 7. 问题五：`-: command not found`

#### 7.1 报错现象

```text
line 20: -: command not found
Process completed with exit code 127.
```

#### 7.2 原因

这已经不是 SSH 凭据问题，而是 Shell 语法错误。通常是：

- 脚本中存在单独一行 `-`；
- 命令选项前缺少主命令；
- YAML 复制时混入了错误的短横线；
- 反斜杠 `\` 后面还有空格；
- 命令换行后缩进或续行关系被破坏。

错误示例：

```bash
ssh-keyscan
  -p "$SERVER_PORT"
```

或者：

```bash
ssh-keyscan \ 
  -p "$SERVER_PORT"
```

第二个例子中，反斜杠后面存在空格，因此不能续行。

#### 7.3 修复方法

避免把简单命令拆得过碎，并直接替换整个 SSH 配置步骤，详见下一章的最终工作流。

---

### 8. GitHub Secrets 清单

建议在 `production` Environment 中配置：

| Secret | 示例 | 说明 |
|---|---|---|
| `SERVER_HOST` | `203.0.113.10` | 服务器 IP 或域名 |
| `SERVER_PORT` | `22` | SSH 端口 |
| `SERVER_USER` | `deploy` | 非 root 部署用户 |
| `SERVER_PATH` | `/opt/personal-website-backend` | 服务器部署目录 |
| `SERVER_SSH_KEY_B64` | 单行 Base64 | 部署私钥的 Base64 内容 |

不要把密码、私钥或服务器信息直接写进仓库。

---

### 9. 服务器端准备

#### 9.1 公钥配置

服务器上应将对应公钥加入：

```text
/home/deploy/.ssh/authorized_keys
```

权限设置：

```bash
chmod 700 ~/.ssh
chmod 600 ~/.ssh/authorized_keys
```

确认目录归属正确：

```bash
chown -R deploy:deploy ~/.ssh
```

#### 9.2 部署目录

```bash
sudo mkdir -p /opt/personal-website-backend
sudo chown -R deploy:deploy /opt/personal-website-backend
```

`SERVER_PATH` 必须和这个目录完全一致。

#### 9.3 本地先测试密钥

```bash
ssh \
  -i ~/.ssh/github_actions_deploy \
  -p 22 \
  deploy@服务器地址
```

本地能够免密登录后，再配置 GitHub Actions。

---

### 10. 最终推荐工作流

创建或替换：

```text
.github/workflows/deploy.yml
```

内容如下：

```yaml
name: Build and Deploy

on:
  push:
    branches:
      - main
  workflow_dispatch:

permissions:
  contents: read

concurrency:
  group: production-deploy
  cancel-in-progress: false

jobs:
  deploy:
    name: Build and Deploy
    runs-on: ubuntu-latest
    environment: production

    steps:
      - name: Checkout source code
        uses: actions/checkout@v6

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      - name: Build with Maven
        run: mvn --batch-mode clean verify

      - name: Locate jar
        run: |
          set -euo pipefail

          JAR_FILE="$(find target \
            -maxdepth 1 \
            -type f \
            -name '*.jar' \
            ! -name '*.jar.original' \
            ! -name '*-sources.jar' \
            ! -name '*-javadoc.jar' \
            -print \
            -quit)"

          if [ -z "$JAR_FILE" ]; then
            echo "No executable jar found"
            find target -maxdepth 1 -type f -print
            exit 1
          fi

          echo "Found jar: $JAR_FILE"
          echo "JAR_FILE=$JAR_FILE" >> "$GITHUB_ENV"

      - name: Configure SSH
        env:
          SSH_PRIVATE_KEY_B64: ${{ secrets.SERVER_SSH_KEY_B64 }}
          SERVER_HOST: ${{ secrets.SERVER_HOST }}
          SERVER_PORT: ${{ secrets.SERVER_PORT }}
        run: |
          set -euo pipefail

          install -d -m 700 ~/.ssh

          printf '%s' "$SSH_PRIVATE_KEY_B64" \
            | tr -d '[:space:]' \
            | base64 --decode > ~/.ssh/deploy_key

          chmod 600 ~/.ssh/deploy_key

          # 在连接服务器前验证私钥格式
          ssh-keygen -y -f ~/.ssh/deploy_key > /dev/null

          ssh-keyscan -p "$SERVER_PORT" -H "$SERVER_HOST" \
            >> ~/.ssh/known_hosts

          chmod 600 ~/.ssh/known_hosts
          echo "SSH configuration completed"

      - name: Test SSH connection
        env:
          SERVER_HOST: ${{ secrets.SERVER_HOST }}
          SERVER_PORT: ${{ secrets.SERVER_PORT }}
          SERVER_USER: ${{ secrets.SERVER_USER }}
        run: |
          ssh \
            -o BatchMode=yes \
            -o IdentitiesOnly=yes \
            -o ConnectTimeout=20 \
            -i ~/.ssh/deploy_key \
            -p "$SERVER_PORT" \
            "$SERVER_USER@$SERVER_HOST" \
            "echo 'SSH connection successful' && whoami && hostname"

      - name: Upload jar
        env:
          SERVER_HOST: ${{ secrets.SERVER_HOST }}
          SERVER_PORT: ${{ secrets.SERVER_PORT }}
          SERVER_USER: ${{ secrets.SERVER_USER }}
          SERVER_PATH: ${{ secrets.SERVER_PATH }}
        run: |
          scp \
            -o BatchMode=yes \
            -o IdentitiesOnly=yes \
            -o ConnectTimeout=20 \
            -i ~/.ssh/deploy_key \
            -P "$SERVER_PORT" \
            "$JAR_FILE" \
            "$SERVER_USER@$SERVER_HOST:$SERVER_PATH/personal-website-backend.jar.new"

      - name: Deploy application
        env:
          SERVER_HOST: ${{ secrets.SERVER_HOST }}
          SERVER_PORT: ${{ secrets.SERVER_PORT }}
          SERVER_USER: ${{ secrets.SERVER_USER }}
          SERVER_PATH: ${{ secrets.SERVER_PATH }}
        run: |
          ssh \
            -o BatchMode=yes \
            -o IdentitiesOnly=yes \
            -o ConnectTimeout=20 \
            -o ServerAliveInterval=15 \
            -o ServerAliveCountMax=3 \
            -i ~/.ssh/deploy_key \
            -p "$SERVER_PORT" \
            "$SERVER_USER@$SERVER_HOST" \
            "'$SERVER_PATH/release.sh'"
```

---

### 11. `release.sh` 的文件约定

上面的工作流会把新版本上传为：

```text
/opt/personal-website-backend/personal-website-backend.jar.new
```

因此服务器上的 `release.sh` 至少需要完成：

```text
确认 .jar.new 存在
    ↓
备份当前 jar
    ↓
将 .jar.new 移动为正式 jar
    ↓
重启 systemd 服务
    ↓
执行健康检查
    ↓
失败时回滚
```

工作流、`SERVER_PATH` 和 `release.sh` 中的文件名必须保持一致：

```text
personal-website-backend.jar.new
personal-website-backend.jar
```

---

### 12. 如何按顺序排查

#### 第一步：确认构建产物

```bash
find target -maxdepth 1 -type f -print
```

确认存在可执行 jar，而不是只有 `.jar.original`。

#### 第二步：确认私钥格式

工作流必须通过：

```bash
ssh-keygen -y -f ~/.ssh/deploy_key > /dev/null
```

如果这里失败，不要继续排查服务器权限。

#### 第三步：确认 SSH 身份认证

```bash
ssh \
  -o BatchMode=yes \
  -o IdentitiesOnly=yes \
  -i ~/.ssh/deploy_key \
  -p "$SERVER_PORT" \
  "$SERVER_USER@$SERVER_HOST" \
  'whoami'
```

正确输出应为：

```text
deploy
```

#### 第四步：确认服务器目录权限

```bash
test -d /opt/personal-website-backend
test -w /opt/personal-website-backend
```

#### 第五步：单独测试上传

先传一个小文件，确认 SCP 权限：

```bash
echo ok > connection-test.txt
scp connection-test.txt deploy@服务器地址:/opt/personal-website-backend/
```

#### 第六步：最后测试发布脚本

```bash
/opt/personal-website-backend/release.sh
```

按这个顺序排查，可以明确区分构建、密钥、认证、目录权限和应用启动问题。

---

### 13. 安全建议

1. 使用单独的 `deploy` 用户，不使用 `root`。
2. 为 GitHub Actions 单独生成部署密钥。
3. 部署私钥尽量不设置口令，但必须严格限制服务器权限。
4. `deploy` 用户只允许写入指定应用目录。
5. 如果需要 `sudo systemctl restart`，只授权操作指定服务。
6. 工作流使用 `permissions: contents: read`。
7. 生产凭据放入 GitHub `production` Environment。
8. 生产环境建议预先保存服务器真实 Host Key，避免每次动态执行 `ssh-keyscan`。
9. 不在日志中输出私钥或 Base64 Secret。
10. 公共仓库不要运行来自不可信 Pull Request 的生产部署流程。

---

### 14. 最终检查表

- [ ] Maven 构建在本地成功。
- [ ] GitHub Actions 中 `clean verify` 成功。
- [ ] 工作流能够自动找到可执行 jar。
- [ ] `.jar.original` 没有被当作部署产物。
- [ ] `SERVER_SSH_KEY_B64` 是纯单行 Base64。
- [ ] `ssh-keygen -y` 能验证解码后的私钥。
- [ ] `ssh-test.yml` 和 `deploy.yml` 使用同一套 Secret。
- [ ] 工作流中不存在旧的 `SERVER_SSH_KEY` 写法。
- [ ] SSH 可以以 `deploy` 用户免密登录。
- [ ] 服务器的 `authorized_keys` 权限正确。
- [ ] `SERVER_PATH` 已存在并允许 `deploy` 用户写入。
- [ ] YAML 中没有多余的 `-`。
- [ ] Shell 续行符 `\` 后没有空格。
- [ ] 上传文件名与 `release.sh` 的约定一致。
- [ ] `release.sh` 能完成重启、健康检查和失败回滚。

> 建议每次只验证一个环节：先构建，再验证私钥，再测试 SSH，再测试 SCP，最后才执行正式发布。这样比反复运行整条流水线更容易定位问题。
