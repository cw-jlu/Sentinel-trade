# 大数据分析课程作业 (作业代码及评估)

这个文件夹 (`homework_assignment`) 包含了根据 `Sentinel Trade` 实际业务场景拓展完成的大数据计算分析代码，可以直接用于作业演示与评估。

## 目录结构
- `data/input/ticks.csv`: 模拟的逐笔交易 (Tick) 测试数据集。
- `src/.../mapreduce/`: 离线数据分析 (Hadoop MapReduce) 的**完整可运行代码**，用于计算 **VWAP（成交量加权平均价）**。
- `src/.../flink/`: 实时流处理 (Flink) 的**补全版代码**，包含了原先缺失的 `merge()` 窗口合并逻辑、累加器结构以及实体类的完整定义。
- `pom.xml`: 包含了运行这些代码所需的 Hadoop 和 Flink 依赖。

## 如何评估 / 运行 MapReduce 测试

由于我们把 Hadoop MapReduce 的 `framework.name` 设置成了 `local`，这允许你**直接在本地 IDE (如 IntelliJ IDEA) 中运行它**而无需搭建完整的 Hadoop 集群，非常适合用于作业结果截图。

### 运行步骤：
1. 使用 IntelliJ IDEA 或 Eclipse 打开 `homework_assignment` 目录作为一个 Maven 项目（或者直接将其集成到原项目的父级 `pom.xml` 中）。
2. Maven 自动下载依赖（Hadoop 和 Flink）。
3. 打开 `src/main/java/com/sentinel_trade/homework/mapreduce/VWAPDriver.java`。
4. 在 IDE 的运行配置 (Run/Debug Configurations) 中，将 `Program arguments` 配置为：
   `data/input/ticks.csv data/output`
5. 点击 **Run** 运行 `VWAPDriver.main()`。
6. 运行成功后，你会在 `homework_assignment/data/output/` 文件夹下看到 `part-r-00000` 文件，里面就是最终的汇总结论（带 VWAP）。
7. **截图该文件内容即可完美交差！**

## 代码重点补全内容说明
在 `KLineAggregatorComplete.java` 中，原先省略的 `merge` (分布式窗口合并逻辑) 已经补全。在分布式流处理中，为了应对乱序并发，它会根据不同线程累加器内的 `minTimestamp` 和 `maxTimestamp` 正确判断哪一条数据才是真正的“开盘价(Open)”与“收盘价(Close)”。