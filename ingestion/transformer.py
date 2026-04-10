"""
DataTransformer: converts raw exchange JSON payloads into TickData objects.

Currently supports Binance AggTrade stream format.
"""

import logging
from decimal import Decimal, InvalidOperation
from typing import Optional

from ingestion.models.tick_data import TickData

logger = logging.getLogger(__name__)

# Binance AggTrade field mapping
_FIELD_MAP = {
    "s": "symbol",
    "p": "price",
    "q": "quantity",
    "T": "timestamp",
    "a": "trade_id",
    "m": "is_buyer_maker",
}

_REQUIRED_FIELDS = list(_FIELD_MAP.keys())


class DataTransformer:
    """Transforms raw exchange JSON payloads into standardised TickData objects."""

    def transform_aggtrade(self, raw_json: dict) -> Optional[TickData]:
        """
        Convert a Binance AggTrade JSON payload to a TickData instance.

        Field mapping:
            s -> symbol
            p -> price
            q -> quantity
            T -> timestamp
            a -> trade_id
            m -> is_buyer_maker

        Returns:
            TickData on success, None if the input is invalid or missing fields.

        Raises:
            ValueError: if raw_json is not a dict.
        """
        if not isinstance(raw_json, dict):
            logger.error(
                "transform_aggtrade received non-dict input: type=%s value=%r",
                type(raw_json).__name__,
                raw_json,
            )
            raise ValueError(
                f"raw_json must be a dict, got {type(raw_json).__name__!r}"
            )

        # Check for missing required fields
        missing = [f for f in _REQUIRED_FIELDS if f not in raw_json]
        if missing:
            logger.error(
                "transform_aggtrade missing required fields %s in payload: %r",
                missing,
                raw_json,
            )
            return None

        # Extract and coerce each field
        try:
            symbol = str(raw_json["s"]).strip()
            price = Decimal(str(raw_json["p"]))
            quantity = Decimal(str(raw_json["q"]))
            timestamp = int(raw_json["T"])
            trade_id = str(raw_json["a"]).strip()
            is_buyer_maker = bool(raw_json["m"])
        except (InvalidOperation, TypeError, ValueError) as exc:
            logger.error(
                "transform_aggtrade failed to coerce fields: %s | payload: %r",
                exc,
                raw_json,
            )
            return None

        # Delegate remaining validation to TickData
        try:
            return TickData(
                symbol=symbol,
                price=price,
                quantity=quantity,
                timestamp=timestamp,
                trade_id=trade_id,
                is_buyer_maker=is_buyer_maker,
            )
        except ValueError as exc:
            logger.error(
                "transform_aggtrade produced invalid TickData: %s | payload: %r",
                exc,
                raw_json,
            )
            return None
