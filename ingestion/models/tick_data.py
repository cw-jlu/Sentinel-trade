"""
TickData dataclass for the Sentinel-Trade ingestion module.

Represents a single standardised trade event (AggTrade) received from the
exchange.  All fields are validated on construction.
"""

from dataclasses import dataclass
from decimal import Decimal, InvalidOperation


@dataclass
class TickData:
    """
    Standardised tick data model representing a single trade event.

    Fields:
        symbol:          Trading pair, e.g. "BTCUSDT"
        price:           Execution price (must be > 0)
        quantity:        Execution quantity (must be > 0)
        timestamp:       Event timestamp in milliseconds (must be > 0)
        trade_id:        Unique trade identifier used for deduplication
        is_buyer_maker:  True if the buyer is the market maker
    """

    symbol: str
    price: Decimal
    quantity: Decimal
    timestamp: int
    trade_id: str
    is_buyer_maker: bool

    def __post_init__(self) -> None:
        """Validate all fields after initialisation."""
        self._validate()

    def _validate(self) -> None:
        # --- symbol ---
        if not self.symbol or not isinstance(self.symbol, str):
            raise ValueError("symbol must be a non-empty string")
        self.symbol = self.symbol.strip()
        if not self.symbol:
            raise ValueError("symbol must not be blank")

        # --- price ---
        if not isinstance(self.price, Decimal):
            try:
                self.price = Decimal(str(self.price))
            except (InvalidOperation, TypeError) as exc:
                raise ValueError(
                    f"price must be a valid decimal number, got {self.price!r}"
                ) from exc
        if self.price <= Decimal("0"):
            raise ValueError(f"price must be greater than 0, got {self.price}")

        # --- quantity ---
        if not isinstance(self.quantity, Decimal):
            try:
                self.quantity = Decimal(str(self.quantity))
            except (InvalidOperation, TypeError) as exc:
                raise ValueError(
                    f"quantity must be a valid decimal number, got {self.quantity!r}"
                ) from exc
        if self.quantity <= Decimal("0"):
            raise ValueError(f"quantity must be greater than 0, got {self.quantity}")

        # --- timestamp ---
        if not isinstance(self.timestamp, int):
            try:
                self.timestamp = int(self.timestamp)
            except (TypeError, ValueError) as exc:
                raise ValueError(
                    f"timestamp must be an integer, got {self.timestamp!r}"
                ) from exc
        if self.timestamp <= 0:
            raise ValueError(f"timestamp must be greater than 0, got {self.timestamp}")

        # --- trade_id ---
        if not self.trade_id or not isinstance(self.trade_id, str):
            raise ValueError("trade_id must be a non-empty string")
        self.trade_id = self.trade_id.strip()
        if not self.trade_id:
            raise ValueError("trade_id must not be blank")

        # --- is_buyer_maker ---
        if not isinstance(self.is_buyer_maker, bool):
            raise ValueError(
                f"is_buyer_maker must be a bool, got {type(self.is_buyer_maker).__name__}"
            )

    @property
    def amount(self) -> Decimal:
        """Notional value of the trade: price × quantity."""
        return self.price * self.quantity

    def to_dict(self) -> dict:
        """Return a JSON-serialisable dictionary representation."""
        return {
            "symbol": self.symbol,
            "price": str(self.price),
            "quantity": str(self.quantity),
            "timestamp": self.timestamp,
            "trade_id": self.trade_id,
            "is_buyer_maker": self.is_buyer_maker,
        }
