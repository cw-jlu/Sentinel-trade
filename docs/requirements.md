# Requirements Document

## Introduction

Sentinel-Trade 是一个高性能分布式实时金融数据监控与分析平台，旨在解决传统金融分析系统在高频交易场景下面临的数据丢失、处理延迟和存储空间爆炸问题。系统采用 Lambda 架构，能够每秒处理 10,000+ 条逐笔成交数据，实时生成多尺度 K 线，并对异常交易进行毫秒级告警。

## Glossary

- **System**: Sentinel-Trade 平台
- **Data_Ingestion_Module**: 实时数据采集模块
- **Stream_Processing_Module**: 流式计算处理模块
- **Storage_Module**: 多级存储与生命周期管理模块
- **Serving_Module**: 数据看板与推送模块
- **Tick_Data**: 逐笔成交数据，包含价格、数量、时间戳等信息
- **K_Line**: K 线数据，包含开盘价（Open）、最高价（High）、最低价（Low）、收盘价（Close）
- **Watermark**: 水位线，用于处理乱序数据的时间戳机制
- **TTL**: Time To Live，数据生命周期
- **Hot_Data**: 热数据，存储在 Redis 中的最新数据
- **Warm_Data**: 温数据，存储在 ClickHouse 中的近期数据
- **Cold_Data**: 冷数据，存储在 MySQL 中的历史聚合数据

