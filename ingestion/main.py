"""
Sentinel-Trade Data Ingestion Service

Entry point: connects to Binance AggTrade WebSocket, transforms messages,
and publishes them to Kafka.

Requirements: 1.1, 1.2, 1.3
"""

from __future__ import annotations

import asyncio
import json
import logging
import os
import signal
import sys
import time
from typing import Optional

from fastapi import FastAPI
import uvicorn

from ingestion.kafka_producer import KafkaProducer
from ingestion.serializers import serialize_avro
from ingestion.transformer import DataTransformer
from ingestion.websocket_client import WebSocketClient
from ingestion.monitoring import ThroughputMonitor

# ---------------------------------------------------------------------------
# Configuration (from environment variables)
# ---------------------------------------------------------------------------

KAFKA_BOOTSTRAP_SERVERS: str = os.getenv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
KAFKA_TOPIC: str = os.getenv("KAFKA_TOPIC", "raw-tick-data")
BINANCE_WS_URL: str = os.getenv(
    "BINANCE_WS_URL",
    "wss://stream.binance.com:9443/ws/btcusdt@aggTrade",
)
LOG_LEVEL: str = os.getenv("LOG_LEVEL", "INFO").upper()

# ---------------------------------------------------------------------------
# Logging setup
# ---------------------------------------------------------------------------

logging.basicConfig(
    level=getattr(logging, LOG_LEVEL, logging.INFO),
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    datefmt="%Y-%m-%dT%H:%M:%S",
    stream=sys.stdout,
)
logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# FastAPI app (health endpoint)
# ---------------------------------------------------------------------------

app = FastAPI(title="Sentinel-Trade Ingestion", version="1.0.0")


@app.get("/health")
async def health() -> dict:
    return {"status": "ok"}


# ---------------------------------------------------------------------------
# Core ingestion pipeline
# ---------------------------------------------------------------------------

class IngestionPipeline:
    """Wires WebSocketClient → DataTransformer → KafkaProducer."""

    def __init__(self) -> None:
        self._transformer = DataTransformer()
        self._producer = KafkaProducer(bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS)
        self._ws_client = WebSocketClient()
        self._monitor = ThroughputMonitor(report_interval_seconds=10)
        self._running = False

    async def start(self) -> None:
        logger.info(
            "Starting ingestion pipeline – broker=%s topic=%s ws=%s",
            KAFKA_BOOTSTRAP_SERVERS,
            KAFKA_TOPIC,
            BINANCE_WS_URL,
        )
        await self._producer.start()
        self._running = True
        self._monitor.start()
        await self._ws_client.connect(BINANCE_WS_URL, self._on_message)

    async def stop(self) -> None:
        logger.info("Stopping ingestion pipeline.")
        self._running = False
        await self._ws_client.close()
        await self._producer.flush()
        await self._producer.stop()
        self._monitor.stop()

    async def _on_message(self, raw: str) -> None:
        """Process a single raw WebSocket message."""
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError as exc:
            logger.error("Failed to parse JSON message: %s | raw=%r", exc, raw[:200])
            return

        tick = self._transformer.transform_aggtrade(payload)
        if tick is None:
            return  # Error already logged by transformer

        try:
            avro_bytes = serialize_avro(tick)
            await self._producer.send(KAFKA_TOPIC, key=tick.symbol, value=avro_bytes)
            self._monitor.record_message()
        except Exception as exc:  # noqa: BLE001
            logger.error("Failed to publish tick to Kafka: %s | tick=%s", exc, tick.trade_id)


# ---------------------------------------------------------------------------
# Graceful shutdown
# ---------------------------------------------------------------------------

_pipeline: Optional[IngestionPipeline] = None


def _handle_signal(sig, frame) -> None:
    logger.info("Received signal %s – initiating graceful shutdown.", sig)
    if _pipeline is not None:
        asyncio.get_event_loop().create_task(_pipeline.stop())


async def run() -> None:
    global _pipeline
    _pipeline = IngestionPipeline()

    loop = asyncio.get_running_loop()
    for sig in (signal.SIGINT, signal.SIGTERM):
        try:
            loop.add_signal_handler(sig, lambda s=sig: asyncio.create_task(_pipeline.stop()))
        except NotImplementedError:
            # Windows does not support add_signal_handler
            signal.signal(sig, _handle_signal)

    try:
        await _pipeline.start()
    except asyncio.CancelledError:
        pass
    finally:
        if _pipeline._running:
            await _pipeline.stop()


if __name__ == "__main__":
    asyncio.run(run())
