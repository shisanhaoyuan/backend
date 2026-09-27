# Backend Exam — Spring Boot + Spring Security

基于 **Spring Boot 3.2.0** 的后端笔试项目：提供用户名密码登录 API，以及必须登录才能访问的 GET HelloWorld API。

GitHub: https://github.com/shisanhaoyuan/backend

## 技术与运行

- Java 17、Spring Boot **3.2.0**、Spring Security 6。
- Maven Wrapper 已包含，无需单独安装 Maven；首次运行需要联网下载依赖。
- 使用内存用户，无需数据库。演示账号：**`test` / `123456`**。
- Spring Security 标准表单登录，BCrypt 校验密码，通过 `JSESSIONID` Cookie 保存 Session；保留 CSRF 防护。

```bash
git clone https://github.com/shisanhaoyuan/backend.git
cd backend
./mvnw spring-boot:run
```

Windows 使用 `mvnw.cmd spring-boot:run`。请安装 JDK 17 并设置 `JAVA_HOME`。服务默认地址为 `http://localhost:8080`。

也可运行打包产物：

```bash
./mvnw clean verify
java -jar target/backend-1.0.0.jar
```

## API

| 方法 | 路径 | 请求 | 成功响应 |
| --- | --- | --- | --- |
| GET | `/api/csrf` | 无需登录；客户端保存 Cookie | `200`，`{"headerName":"X-CSRF-TOKEN","token":"…"}` |
| POST | `/api/login` | 表单 `username`、`password`，Cookie 和 CSRF 请求头 | `200`，`{"message":"Login successful"}` |
| GET | `/api/hello` | 登录成功后的 Session Cookie | `200`，纯文本 **`Hello World`** |
| POST | `/api/logout` | Session Cookie 和最新 CSRF 请求头 | `200`，`{"message":"Logout successful"}` |

登录请求的 `Content-Type` 为 `application/x-www-form-urlencoded`。认证由 Spring Security 过滤器处理，登录接口不需要自定义 Controller。登录成功会轮换 Session ID；客户端应保存响应中的新 Cookie。

- 未登录访问 `/api/hello`：`401`，`{"error":"Authentication required"}`。
- CSRF 校验通过后，用户名或密码错误：`401`，`{"error":"Invalid username or password"}`。
- POST 请求缺少或携带无效 CSRF Token：`403`，`{"error":"Forbidden"}`。
- Session 空闲 30 分钟后过期；退出登录会使 Session 失效。重启应用后需要重新登录。

## 完整调用示例

以下命令适用于 Bash / Zsh，需要 `curl` 和 `python3`（Python 仅用于从 JSON 提取 Token）。在项目根目录运行，保持同一终端。`cookies.txt` 已加入 Git 忽略。

```bash
# 1. 未登录访问，应返回 HTTP 401
curl -i http://localhost:8080/api/hello

# 2. 获取 CSRF Token，并保存当前会话 Cookie
CSRF_TOKEN=$(curl -fsS -c cookies.txt http://localhost:8080/api/csrf \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')

# 3. 使用用户名密码登录，应返回 HTTP 200
curl -i -b cookies.txt -c cookies.txt \
  -H "X-CSRF-TOKEN: $CSRF_TOKEN" \
  --data-urlencode 'username=test' \
  --data-urlencode 'password=123456' \
  http://localhost:8080/api/login

# 4. 携带登录 Cookie，应返回 HTTP 200，正文为 Hello World
curl -i -b cookies.txt http://localhost:8080/api/hello

# 5. 登录后旧 CSRF Token 已失效，退出前获取新 Token
CSRF_TOKEN=$(curl -fsS -b cookies.txt -c cookies.txt http://localhost:8080/api/csrf \
  | python3 -c 'import json,sys; print(json.load(sys.stdin)["token"])')

curl -i -X POST -b cookies.txt -c cookies.txt \
  -H "X-CSRF-TOKEN: $CSRF_TOKEN" \
  http://localhost:8080/api/logout

# 6. 退出后再次访问，应返回 HTTP 401
curl -i -b cookies.txt http://localhost:8080/api/hello
```

Postman 同样先调用 `/api/csrf`，保留自动管理的 Cookie，把 `token` 放入 `X-CSRF-TOKEN` 请求头，然后用 `x-www-form-urlencoded` 提交账号密码。

## 测试

```bash
./mvnw verify
```

10 个集成测试场景使用完整 Spring 应用上下文和真实 Security 过滤器链，覆盖未认证拦截、CSRF 获取、正确登录、Session ID 轮换与会话隔离、错误密码、未知用户、空密码、缺失/无效 CSRF Token、退出失效以及退出请求的 CSRF 防护。测试通过真实登录流程获得会话，不使用模拟已登录用户。GitHub Actions 在 push 和 pull request 时运行相同检查。

## 项目结构

```text
src/main/java/com/example/backend/
  BackendApplication.java              应用入口
  SecurityConfig.java                  用户、密码编码、认证与访问控制
  HelloController.java                 受保护的 Hello World 接口
  CsrfController.java                  CSRF Token 接口
src/main/resources/application.properties
src/test/java/com/example/backend/AuthenticationIntegrationTest.java
.github/workflows/ci.yml
```

此项目按题目固定使用 Spring Boot 3.2.0，内存中的固定账号仅用于笔试演示。
