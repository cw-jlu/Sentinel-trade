# Sentinel-Trade

高性能分布式实时金融数据监控与分析平台

## 项目结构

```
sentinel-trade/
├── ingestion/              # 数据采集模块 (Python)
│   └── config/            # 配置文件目录
├── stream-processing/      # 流式计算模块 (Apache Flink + Java)
│   └── config/            # 配置文件目录
├── backend/               # 服务层 (Spring Boot + Java)
│   └── config/            # 配置文件目录
├── frontend/              # 前端看板 (Vue.js)
│   └── config/            # 配置文件目录
└── .kiro/                 # Kiro 规范文档
    └── specs/
        └── sentinel-trade/
            ├── requirements.md  # 需求文档
            ├── design.md       # 设计文档
            └── tasks.md        # 任务列表
```

## 模块说明

### ingestion/ 数据采集模块
- 技术栈: Python 3.10+, FastAPI, asyncio, websockets, avro-python3
- 职责: 从交易所采集实时数据，转换格式，推送到 Kafka

### stream-processing/ 分布流式计算模块
- 技术栈: Apache Flink 1.17+, Java 17
- 职责: 实时聚合 K 线、检测异常交易

### backend/ CURD层
- 技术栈: Spring Boot 3.0, Java 17
- 职责: 对外提供 WebSocket 推送和 HTTP 查询接口

### frontend/ 前端
- 技术栈: Vue.js 3, ECharts
- 职责: 实时数据可视化和交互式查询
