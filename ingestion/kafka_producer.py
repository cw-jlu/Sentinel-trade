"""
KafkaProducer: async Kafka producer for publishing serialized TickData messages.

Uses aiokafka for non-blocking I/O.  Configured with batching parameters
(batch_size=16384, linger_ms=5) to improve throughput while keeping latency low.

Requirement 1.3 – publish standardised messages to Kafka topic.
"""

from __future__ import annotations

import logging
from typing import Optional

from aiokafka import AIOKafkaProducer
from aiokafka.errors import KafkaConnectionError, KafkaError

logger = logging.getLogger(__name__)

# Default batching configuration (matches design doc: batch.size=16KB, linger_ms=5)
_DEFAULT_BATCH_SIZE = 16384  # 16 KB
_DEFAULT_LINGER_MS = 5


class KafkaProducer:
    """Async Kafka producer that publishes byte messages to topics.

    Args:
        bootstrap_servers: Comma-separated list of Kafka broker addresses,
            e.g. ``"localhost:9092"`` or ``"broker1:9092,broker2:9092"``.
        batch_size: Maximum number of bytes to batch before sending.
            Defaults to 16384 (16 KB).
        linger_ms: Milliseconds to wait for additional messages before
            sending a batch.  Defaults to 5.

    Example::

        producer = KafkaProducer("localhost:9092")
        await producer.start()
        await producer.send("raw-tick-data", key="BTCUSDT", value=avro_bytes)
        await producer.flush()
        await producer.stop()
    """

    def __init__(
        self,
        bootstrap_servers: str = "localhost:9092",
        batch_size: int = _DEFAULT_BATCH_SIZE,
        linger_ms: int = _DEFAULT_LINGER_MS,
    ) -> None:
        self._bootstrap_servers = bootstrap_servers
        self._batch_size = batch_size
        self._linger_ms = linger_ms
        self._producer: Optional[AIOKafkaProducer] = None

    # ------------------------------------------------------------------
    # Lifecycle
    # ------------------------------------------------------------------

    async def start(self) -> None:
        """Create and start the underlying aiokafka producer.

        Must be called before :meth:`send` or :meth:`flush`.

        Raises:
            KafkaConnectionError: If the broker cannot be reached.
        """
        try:
            self._producer = AIOKafkaProducer(
                bootstrap_servers=self._bootstrap_servers,
                max_batch_size=self._batch_size,
                linger_ms=self._linger_ms,
            )
            await self._producer.start()
            logger.info(
                "KafkaProducer started – brokers=%s batch_size=%d linger_ms=%d",
                self._bootstrap_servers,
                self._batch_size,
                self._linger_ms,
            )
        except KafkaConnectionError as exc:
            logger.error(
                "KafkaProducer failed to connect to brokers %s: %s",
                self._bootstrap_servers,
                exc,
            )
            raise

    async def stop(self) -> None:
        """Flush pending messages and stop the producer gracefully."""
        if self._producer is not None:
            try:
                await self._producer.stop()
                logger.info("KafkaProducer stopped.")
            except KafkaError as exc:
                logger.error("KafkaProducer encountered error during stop: %s", exc)
            finally:
                self._producer = None

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    async def send(self, topic: str, key: str, value: bytes) -> None:
        """Asynchronously send a message to a Kafka topic.

        Args:
            topic: Target Kafka topic name.
            key: Message key (encoded as UTF-8 before sending).
            value: Pre-serialized message payload (e.g. Avro bytes).

        Raises:
            RuntimeError: If the producer has not been started.
            KafkaError: If the message cannot be delivered.
        """
        if self._producer is None:
            raise RuntimeError(
                "KafkaProducer is not started. Call await producer.start() first."
            )
        try:
            await self._producer.send(
                topic,
                key=key.encode("utf-8"),
                value=value,
            )
            logger.debug("Sent message to topic=%s key=%s size=%d", topic, key, len(value))
        except KafkaError as exc:
            logger.error(
                "Failed to send message to topic=%s key=%s: %s",
                topic,
                key,
                exc,
            )
            raise

    async def flush(self) -> None:
        """Flush all buffered messages, waiting until they are acknowledged.

        Raises:
            RuntimeError: If the producer has not been started.
            KafkaError: If flushing fails.
        """
        if self._producer is None:
            raise RuntimeError(
                "KafkaProducer is not started. Call await producer.start() first."
            )
        try:
            await self._producer.flush()
            logger.debug("KafkaProducer flushed.")
        except KafkaError as exc:
            logger.error("KafkaProducer flush failed: %s", exc)
            raise

    # ------------------------------------------------------------------
    # Context manager support
    # ------------------------------------------------------------------

    async def __aenter__(self) -> "KafkaProducer":
        await self.start()
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb) -> None:
        await self.stop()
