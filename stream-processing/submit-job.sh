#!/bin/bash
# 等待 Flink JobManager 就绪，然后自动提交 Job

set -e

FLINK_URL="${FLINK_JOBMANAGER_URL:-http://flink-jobmanager:8081}"
JAR_PATH="/opt/flink/usrlib/sentinel-job.jar"

echo "等待 Flink JobManager 就绪: $FLINK_URL"
until curl -sf "$FLINK_URL/overview" > /dev/null 2>&1; do
  echo "  JobManager 未就绪，5秒后重试..."
  sleep 5
done

echo "Flink JobManager 已就绪，提交 Job..."

# 上传 jar
UPLOAD_RESPONSE=$(curl -sf -X POST \
  "$FLINK_URL/jars/upload" \
  -H "Expect:" \
  -F "jarfile=@$JAR_PATH")

JAR_ID=$(echo "$UPLOAD_RESPONSE" | grep -o '"filename":"[^"]*"' | cut -d'"' -f4 | xargs basename)
echo "Jar 上传成功: $JAR_ID"

# 提交 Job
RUN_RESPONSE=$(curl -sf -X POST \
  "$FLINK_URL/jars/$JAR_ID/run" \
  -H "Content-Type: application/json" \
  -d "{\"entryClass\": \"com.sentinel_trade.SentinelTradeJob\"}")

JOB_ID=$(echo "$RUN_RESPONSE" | grep -o '"jobid":"[^"]*"' | cut -d'"' -f4)
echo "Job 提交成功，Job ID: $JOB_ID"
echo "Flink UI: $FLINK_URL/#/job/$JOB_ID/overview"
