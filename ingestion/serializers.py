"""
Avro serialization/deserialization for TickData.

Uses fastavro for high-performance Avro encoding.  The schema mirrors the
TickData dataclass fields exactly so that round-trip fidelity is guaranteed.

Requirement 7.1 – serialize Tick_Data using Avro format.
"""

from __future__ import annotations

import io
from decimal import Decimal
from typing import Any

import fastavro

from ingestion.models.tick_data import TickData

# ---------------------------------------------------------------------------
# Avro Schema
# ---------------------------------------------------------------------------

#: Parsed (compiled) Avro schema for TickData.
#: Decimal fields are stored as strings to preserve arbitrary precision.
TICK_DATA_SCHEMA: dict[str, Any] = {
    "type": "record",
    "name": "TickData",
    "namespace": "com.sentinel_trade.ingestion",
    "doc": "Standardised tick (trade) event from the exchange.",
    "fields": [
        {
            "name": "symbol",
            "type": "string",
            "doc": "Trading pair, e.g. BTCUSDT",
        },
        {
            "name": "price",
            "type": "string",
            "doc": "Execution price as a decimal string to preserve precision",
        },
        {
            "name": "quantity",
            "type": "string",
            "doc": "Execution quantity as a decimal string to preserve precision",
        },
        {
            "name": "timestamp",
            "type": "long",
            "doc": "Event timestamp in milliseconds since Unix epoch",
        },
        {
            "name": "trade_id",
            "type": "string",
            "doc": "Unique trade identifier used for deduplication",
        },
        {
            "name": "is_buyer_maker",
            "type": "boolean",
            "doc": "True if the buyer is the market maker",
        },
    ],
}

# Pre-parse the schema once at import time for efficiency.
_PARSED_SCHEMA = fastavro.parse_schema(TICK_DATA_SCHEMA)


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------


def serialize_avro(tick: TickData) -> bytes:
    """Serialize a TickData instance to Avro binary format.

    Args:
        tick: A valid :class:`~ingestion.models.tick_data.TickData` instance.

    Returns:
        Avro-encoded bytes (using the Object Container File format with a
        single record so that the schema is embedded and the payload is
        self-describing).
    """
    record = {
        "symbol": tick.symbol,
        "price": str(tick.price),
        "quantity": str(tick.quantity),
        "timestamp": tick.timestamp,
        "trade_id": tick.trade_id,
        "is_buyer_maker": tick.is_buyer_maker,
    }
    buf = io.BytesIO()
    fastavro.writer(buf, _PARSED_SCHEMA, [record])
    return buf.getvalue()


def deserialize_avro(data: bytes) -> TickData:
    """Deserialize Avro binary data back into a TickData instance.

    Args:
        data: Avro-encoded bytes as produced by :func:`serialize_avro`.

    Returns:
        A reconstructed :class:`~ingestion.models.tick_data.TickData` with
        all original fields intact.

    Raises:
        ValueError: If the bytes cannot be decoded or a required field is
            missing / invalid.
    """
    buf = io.BytesIO(data)
    records = list(fastavro.reader(buf))
    if not records:
        raise ValueError("Avro data contains no records")
    if len(records) > 1:
        raise ValueError(
            f"Expected exactly 1 record, got {len(records)}. "
            "Use deserialize_avro_many() for multi-record payloads."
        )
    rec = records[0]
    return TickData(
        symbol=rec["symbol"],
        price=Decimal(rec["price"]),
        quantity=Decimal(rec["quantity"]),
        timestamp=rec["timestamp"],
        trade_id=rec["trade_id"],
        is_buyer_maker=rec["is_buyer_maker"],
    )


def deserialize_avro_many(data: bytes) -> list[TickData]:
    """Deserialize an Avro container that holds multiple TickData records.

    Args:
        data: Avro-encoded bytes potentially containing multiple records.

    Returns:
        A list of :class:`~ingestion.models.tick_data.TickData` instances.
    """
    buf = io.BytesIO(data)
    return [
        TickData(
            symbol=rec["symbol"],
            price=Decimal(rec["price"]),
            quantity=Decimal(rec["quantity"]),
            timestamp=rec["timestamp"],
            trade_id=rec["trade_id"],
            is_buyer_maker=rec["is_buyer_maker"],
        )
        for rec in fastavro.reader(buf)
    ]
