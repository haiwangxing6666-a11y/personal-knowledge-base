# 运行说明

本文说明如何在 Windows 使用 Docker Compose 一键部署项目，或使用本机 JDK、Maven 和 PostgreSQL 启动应用，并介绍功能验证、测试、打包和故障排查方法。

## 1. 环境要求

| 软件 | 建议版本 | 检查命令 |
|---|---|---|
| JDK | 17 或更高 | `java -version` |
| Maven | 3.9+ | `mvn -version` |
| PostgreSQL | 14+ | `psql --version` |
| pgvector | 与 PostgreSQL 兼容 | 在数据库执行扩展查询 |
| Docker Desktop | 当前稳定版本 | `docker --version`、`docker compose version` |

使用 Docker 方式时不要求 Windows 单独安装 JDK、Maven、PostgreSQL 和 pgvector，它们由镜像提供。使用本地方式时需要自行准备这些环境。项目编译目标为 Java 17；使用更新的 JDK 运行时可能出现 Maven、Jansi 或 Mockito 的未来兼容性警告，只要最终是 `BUILD SUCCESS`，这些警告不会影响当前构建。

## 2. 获取项目

```powershell
git clone https://github.com/haiwangxing6666-a11y/personal-knowledge-base.git
Set-Location personal-knowledge-base
```

如果已经克隆项目：

```powershell
git switch main
git pull origin main
```

## 3. 准备本地 PostgreSQL（本地方式）

### 3.1 创建数据库

使用 DataGrip、psql 或其他 PostgreSQL 客户端连接到服务器，执行：

```sql
CREATE DATABASE personal_knowledge_base;
```

数据库已存在时不要重复创建。

### 3.2 启用 pgvector

切换连接到 `personal_knowledge_base` 数据库，再执行：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

检查结果：

```sql
SELECT extname, extversion
FROM pg_extension
WHERE extname = 'vector';
```

查询应返回一行 `vector`。扩展必须安装在应用实际连接的数据库中，而不是只安装在默认的 `postgres` 数据库中。

## 4. 配置 .env

在项目根目录执行：

```powershell
Copy-Item .env.example .env
```

然后编辑 `.env`：

```properties
DB_URL=jdbc:postgresql://localhost:5432/personal_knowledge_base
DB_USERNAME=postgres
DB_PASSWORD=填写本机数据库密码

SILICONFLOW_API_KEY=填写模型服务密钥
SILICONFLOW_BASE_URL=https://api.siliconflow.cn/v1
EMBEDDING_MODEL=BAAI/bge-m3
EMBEDDING_DIMENSIONS=1024
CHAT_MODEL=Qwen/Qwen3-8B
```

变量说明：

| 变量 | 用途 |
|---|---|
| `DB_URL` | PostgreSQL JDBC 地址和数据库名 |
| `DB_USERNAME` | 数据库用户 |
| `DB_PASSWORD` | 数据库密码 |
| `SILICONFLOW_API_KEY` | OpenAI 兼容模型服务密钥 |
| `SILICONFLOW_BASE_URL` | 模型服务基础地址 |
| `EMBEDDING_MODEL` | 文本向量模型 |
| `EMBEDDING_DIMENSIONS` | Embedding 输出维度 |
| `CHAT_MODEL` | 问题改写和回答模型 |

注意：

- `.env` 已在 `.gitignore` 中，不能使用 `git add -f .env`。
- `.env.example` 只能保留占位符，不能填写真实密码和密钥。
- `EMBEDDING_DIMENSIONS` 必须与所选 Embedding 模型一致。
- 修改向量模型或维度前，需要评估已有 `vector_store` 数据是否应重新生成。

Docker Compose 会读取同一个 `.env`，但会把容器内的 `DB_URL` 固定为 `jdbc:postgresql://db:5432/personal_knowledge_base`。因此 `.env` 中的本机数据库地址只影响本地启动，不影响 Compose 中的应用容器。

## 5. 使用 Docker Compose 一键部署（推荐）

Docker Compose 会同时启动两个容器：

- `app`：运行 Spring Boot 应用。
- `db`：运行 PostgreSQL 16 和 pgvector，并通过初始化脚本启用 `vector` 扩展。

### 5.1 构建并启动

确认 Docker Desktop 已启动，并已创建 `.env`，然后在项目根目录执行：

```powershell
docker compose up --build -d
```

第一次构建需要下载基础镜像和 Maven 依赖，耗时通常比后续启动更长。以后没有修改镜像内容时可以直接执行：

```powershell
docker compose up -d
```

### 5.2 检查状态和访问应用

```powershell
docker compose ps
```

正常情况下，应用容器状态为 `Up`，数据库容器状态为 `healthy`。启动后访问：

- 资料管理：<http://localhost:8080/>
- 知识问答：<http://localhost:8080/chat.html>
- 健康检查：<http://localhost:8080/api/health>
- Windows 数据库客户端：`localhost:5433`

### 5.3 端口、容器网络和数据卷

浏览器通过 Windows 的 `8080` 端口访问应用容器的 `8080` 端口。DataGrip 等 Windows 程序通过 `localhost:5433` 访问数据库容器的 `5432` 端口。

应用容器不会使用 `localhost:5433` 连接数据库，而是通过 Compose 内部网络访问 `db:5432`；其中 `db` 是数据库服务名。

PostgreSQL 数据保存在名为 `personal-knowledge-base_postgres-data` 的 Docker Volume 中。普通停止、删除和重新创建容器不会清空资料。

### 5.4 日志、停止和重新启动

查看应用日志：

```powershell
docker compose logs -f app
```

按 `Ctrl + C` 只会退出日志查看，不会停止容器。停止容器并保留数据库资料：

```powershell
docker compose down
```

重新启动：

```powershell
docker compose up -d
```

不要随意执行 `docker compose down -v`。其中 `-v` 会删除数据库数据卷，Docker 数据库中的资料和向量也会被清空。

## 6. 本地启动应用

确认 PostgreSQL 已运行，然后在项目根目录执行：

```powershell
mvn spring-boot:run
```

看到类似下面的日志表示启动成功：

```text
Started PersonalKnowledgeBaseApplication
```

默认端口是 `8080`。

### 6.1 健康检查

浏览器打开：

<http://localhost:8080/api/health>

或在 PowerShell 执行：

```powershell
Invoke-RestMethod http://localhost:8080/api/health
```

预期包含：

```json
{
  "status": "UP",
  "application": "personal-knowledge-base"
}
```

### 6.2 Web 页面

- 资料管理：<http://localhost:8080/>
- 知识问答：<http://localhost:8080/chat.html>

页面和 API 由同一个 Spring Boot 应用提供，不需要单独启动前端，也不需要安装 Node.js。

## 7. 基本使用流程

### 7.1 上传文件

在资料管理页面选择 TXT、Markdown、PDF 或 DOCX。成功后资料会出现在列表中，状态为 `READY`，并显示文本块数量。

也可以使用命令：

```powershell
curl.exe -X POST http://localhost:8080/api/documents `
  -F "file=@C:\path\to\document.txt"
```

### 7.2 创建笔记

```powershell
$noteBody = @{
  title = "Spring AI 学习笔记"
  content = "Spring AI 提供模型调用和向量存储抽象。"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/documents/notes `
  -ContentType "application/json" `
  -Body $noteBody
```

### 7.3 收藏网页

```powershell
$linkBody = @{
  url = "https://example.com/article"
  title = "示例文章"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/documents/links `
  -ContentType "application/json" `
  -Body $linkBody
```

网页必须能从当前电脑公开访问，且不能指向本机或内网地址。

### 7.4 知识库问答

先至少添加一份与问题相关的资料，再打开问答页面，或执行：

```powershell
$chatBody = @{
  question = "Spring AI 在我的笔记中有什么作用？"
} | ConvertTo-Json

Invoke-RestMethod -Method Post `
  -Uri http://localhost:8080/api/chat `
  -ContentType "application/json" `
  -Body $chatBody
```

返回值会包含回答、是否拒答、是否二次检索以及资料来源。

## 8. 数据库验证

应用首次成功启动后检查表：

```sql
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('document', 'vector_store');
```

查看资料：

```sql
SELECT id, name, file_type, status, chunk_count, upload_time
FROM document
ORDER BY upload_time DESC;
```

查看向量条数：

```sql
SELECT COUNT(*) FROM vector_store;
```

不要在不理解影响的情况下手动修改或删除 `vector_store` 数据，应用通过 metadata 中的 `documentId` 维护同步关系。

## 9. 运行测试

确保 PostgreSQL 已启动、数据库已创建、`vector` 扩展已启用，然后执行：

```powershell
mvn test
```

测试范围包括：

- 四种文档解析和中文内容。
- 文本切分、重叠和超长段落。
- 网页正文提取和地址安全校验。
- 向量创建、更新和删除编排。
- RAG 检索、改写、回答、来源和拒答。
- Controller、统一异常响应和完整问答链路。
- Spring 上下文、数据库表和前端静态资源。

测试中的 ChatModel 和 EmbeddingModel 使用 Mock，不消耗真实模型额度。

## 10. 打包与运行 JAR

执行测试并打包：

```powershell
mvn clean package
```

生成文件：

```text
target/personal-knowledge-base-0.0.1-SNAPSHOT.jar
```

运行：

```powershell
java -jar target/personal-knowledge-base-0.0.1-SNAPSHOT.jar
```

JAR 仍会从当前工作目录读取 `.env`，因此应在项目根目录运行，或者使用系统环境变量提供同名配置。

## 11. 常见问题

### 数据库连接失败

检查 PostgreSQL 服务、端口、数据库名、用户名和密码。确认 `.env` 位于项目根目录，变量名没有拼写错误。

### 提示 vector 类型或扩展不存在

连接到 `personal_knowledge_base` 后重新执行：

```sql
CREATE EXTENSION IF NOT EXISTS vector;
```

### 向量维度错误

确认 `EMBEDDING_DIMENSIONS` 与 `EMBEDDING_MODEL` 一致。更换模型后，旧向量表可能需要在备份后重建并重新入库资料。

### 模型返回 401 或 403

检查 `SILICONFLOW_API_KEY` 是否有效，以及账号是否有权使用配置的 Embedding 和 Chat 模型。不要把密钥粘贴到 Issue、PR、日志截图或聊天记录中。

### 8080 端口被占用

本地启动时可以临时使用其他端口：

```powershell
mvn spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

随后使用 `http://localhost:8081/` 访问。

使用 Docker Compose 时，可以把 `compose.yaml` 中的端口映射从 `"8080:8080"` 改为 `"8081:8080"`，然后访问 `http://localhost:8081/`。这只改变 Windows 对外端口，不需要修改 `application.yaml` 中的容器内部端口。

### GitHub 推送连接超时

这属于 GitHub 网络或本地代理问题，不影响已经完成的本地 commit。网络恢复后重新执行 `git push` 即可，不需要重新修改或提交代码。

## 12. 停止应用

在运行 Spring Boot 的终端按：

```text
Ctrl + C
```

等待程序释放数据库连接和端口后再关闭终端。

Docker Compose 方式使用：

```powershell
docker compose down
```

该命令会停止并删除容器和 Compose 网络，但保留 PostgreSQL 数据卷。
