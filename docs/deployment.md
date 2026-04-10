# Sentinel-Trade 部署文档

## 前置条件

- Docker 20.10+
- Docker Compose 2.0+
- 可用内存 ≥ 14GB
- 可用磁盘 ≥ 100GB

## 🚀 快速启动

你可以使用统一的管理脚本或双击批处理文件来控制系统。

### 方式 A：双击即用 (Windows 推荐)
直接在项目根目录双击以下文件：
- **`start.bat`**: 启动系统。
- **`status.bat`**: 查看状态。
- **`logs.bat`**: 查看日志。
- **`stop.bat`**: 停止系统。

### 方式 B：命令行管理 (PowerShell)
```powershell
# 启动所有服务
./manage.ps1 start

# 查看状态
./manage.ps1 status

# 查看实时日志 (例如数据采集模块)
./manage.ps1 logs ingestion
```

### Linux / macOS / WSL (Bash)
```bash
# 赋予执行权限 (仅需一次)
chmod +x manage.sh

# 启动所有服务
./manage.sh start

# 查看状态
./manage.sh status
```

## 🛠️ 管理脚本详细用法

管理脚本封装了常用的 `docker-compose` 命令，提供了更友好的输出：

| 操作 | 命令 | 说明 |
| :--- | :--- | :--- |
| **启动** | `start` | 构建镜像并后台启动所有服务 |
| **停止** | `stop` | 停止服务（但不删除容器） |
| **重启** | `restart [service]` | 重启所有或指定服务 |
| **状态**| `status` | 查看容器健康状态和端口映射 |
| **日志** | `logs [service]` | 查看实时滚动日志（默认展示最后 100 行） |
| **清理** | `clean` | **慎用！** 删除容器并清空所有数据库卷数据 |
| **界面** | `ui` | 获取/打开前端看板和 Flink 控制台地址 |

## 📦 传统 Docker Compose 部署 (备选)

若不习惯使用脚本，依然可以使用原生命令：

## 🌐 服务访问与端口映射

系统启动后，可以通过以下端口访问各个组件：

| 组件 | 对应端口 | 访问方式 | 说明 |
| :--- | :--- | :--- | :--- |
| **前端看板** | `3000` | `http://localhost:3000` | 核心可视化界面 |
| **后端服务** | `8080` | `http://localhost:8080` | REST API 与 WebSocket |
| **Flink 控制台**| `8081` | `http://localhost:8081` | 查看流计算作业状态 |
| **MySQL** | `3307` | `localhost:3307` | 存储聚合 K 线 (root/sentinel123) |
| **ClickHouse** | `8123` | `http://localhost:8123` | 分布式时序数据查询 |
| **Redis** | `6379` | `localhost:6379` | 实时热数据缓存 |
| **Kafka** | `9092` | `localhost:9092` | 消息队列入口 |

## 环境变量配置

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` | Kafka 地址 |
| `BINANCE_WS_URL` | `wss://stream.binance.com:9443/ws/btcusdt@aggTrade` | 币安 WebSocket |
| `SPRING_REDIS_HOST` | `redis` | Redis 主机 |
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://mysql:3306/sentinel_trade` | MySQL 连接 |
| `CLICKHOUSE_URL` | `http://clickhouse:8123` | ClickHouse HTTP |
| `LOG_LEVEL` | `INFO` | 日志级别 |

## 服务启动顺序

Docker Compose 通过 `depends_on` + `healthcheck` 保证顺序：

1. Kafka, Redis, ClickHouse, MySQL（基础设施）
2. ingestion（依赖 Kafka healthy）
3. backend（依赖 Redis, ClickHouse, MySQL healthy）
4. frontend（依赖 backend）

## 常见问题排查

### Kafka 无法启动
```bash
docker-compose logs kafka
# 检查端口 9092 是否被占用
netstat -an | grep 9092
```

### 数据库连接失败
```bash
# 检查 MySQL 是否就绪
docker exec sentinel-mysql mysqladmin ping -h localhost -uroot -psentinel123

# 检查 ClickHouse
curl http://localhost:8123/ping
```

### 数据采集无数据
```bash
# 检查 ingestion 日志
docker-compose logs -f ingestion

# 验证 Kafka topic 有消息
docker exec sentinel-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic raw-tick-data --max-messages 5
```

### 重置所有数据
```bash
docker-compose down -v  # 删除所有数据卷
docker-compose up -d
```
