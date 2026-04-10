"""
WebSocketClient: async WebSocket client with exponential-backoff reconnect logic.

Requirement 1.1 – establish async connection and receive AggTrade stream data.
Requirement 1.4 – automatically attempt reconnection within 5 seconds of disconnect.
Requirement 1.5 – resume data ingestion without data loss after reconnect.
"""

from __future__ import annotations

import asyncio
import logging
from typing import Callable, Awaitable, Optional

import websockets
from websockets.asyncio.client import ClientConnection
from websockets.exceptions import ConnectionClosed, WebSocketException

logger = logging.getLogger(__name__)

# Reconnect backoff parameters (design doc: initial 5s, max 60s, multiplier 2x)
_INITIAL_DELAY: float = 5.0
_MAX_DELAY: float = 60.0
_BACKOFF_MULTIPLIER: float = 2.0

# Type alias for the message callback
MessageCallback = Callable[[str], Awaitable[None]]


class WebSocketClient:
    """Async WebSocket client with automatic exponential-backoff reconnection.

    Args:
        initial_delay: Seconds to wait before the first reconnect attempt.
        max_delay: Maximum seconds to wait between reconnect attempts.
        backoff_multiplier: Factor by which the delay grows each attempt.

    Example::

        async def on_message(msg: str) -> None:
            print(msg)

        client = WebSocketClient()
        await client.connect("wss://stream.binance.com:9443/ws/btcusdt@aggTrade", on_message)
    """

    def __init__(
        self,
        initial_delay: float = _INITIAL_DELAY,
        max_delay: float = _MAX_DELAY,
        backoff_multiplier: float = _BACKOFF_MULTIPLIER,
    ) -> None:
        self._initial_delay = initial_delay
        self._max_delay = max_delay
        self._backoff_multiplier = backoff_multiplier

        self._ws: Optional[ClientConnection] = None
        self._url: Optional[str] = None
        self._on_message: Optional[MessageCallback] = None
        self._running: bool = False

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    async def connect(self, url: str, on_message: MessageCallback) -> None:
        """Establish a WebSocket connection and start receiving messages.

        Blocks until :meth:`close` is called or an unrecoverable error occurs.
        Automatically reconnects on connection drops using exponential backoff.

        Args:
            url: WebSocket URL to connect to.
            on_message: Async callback invoked for every received message.
        """
        self._url = url
        self._on_message = on_message
        self._running = True

        logger.info("WebSocketClient connecting to %s", url)
        await self._run_loop()

    async def close(self) -> None:
        """Gracefully stop the client and close the underlying connection."""
        logger.info("WebSocketClient closing.")
        self._running = False
        if self._ws is not None:
            try:
                await self._ws.close()
            except Exception as exc:  # noqa: BLE001
                logger.debug("Error while closing WebSocket: %s", exc)
            finally:
                self._ws = None

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    async def _run_loop(self) -> None:
        """Main loop: connect, receive messages, reconnect on failure."""
        while self._running:
            try:
                await self._open_connection()
                # Normal exit from _open_connection (messages exhausted or close() called)
                if not self._running:
                    break
            except (ConnectionClosed, WebSocketException, OSError) as exc:
                if not self._running:
                    break
                logger.warning(
                    "WebSocketClient connection lost (%s). Triggering reconnect.", exc
                )
                await self.reconnect()
            except asyncio.CancelledError:
                logger.info("WebSocketClient receive loop cancelled.")
                break
            except Exception as exc:  # noqa: BLE001
                if not self._running:
                    break
                logger.error(
                    "WebSocketClient unexpected error: %s. Triggering reconnect.", exc
                )
                await self.reconnect()

    async def _open_connection(self) -> None:
        """Open the WebSocket and receive messages until the connection closes."""
        assert self._url is not None
        assert self._on_message is not None

        async with websockets.connect(self._url) as ws:
            self._ws = ws
            logger.info("WebSocketClient connected to %s", self._url)
            async for message in ws:
                if not self._running:
                    break
                try:
                    await self._on_message(message)
                except Exception as exc:  # noqa: BLE001
                    logger.error("on_message callback raised an error: %s", exc)
        self._ws = None

    async def reconnect(self) -> None:
        """Attempt to reconnect using exponential backoff.

        Delays start at ``initial_delay`` seconds and double each attempt up to
        ``max_delay`` seconds.  Reconnect attempts are logged at WARNING level.
        """
        if not self._running:
            return

        delay = self._initial_delay
        attempt = 0

        while self._running:
            attempt += 1
            logger.warning(
                "WebSocketClient reconnect attempt #%d – waiting %.1fs before retry.",
                attempt,
                delay,
            )
            await asyncio.sleep(delay)

            if not self._running:
                break

            try:
                await self._open_connection()
                logger.info(
                    "WebSocketClient reconnected successfully on attempt #%d.", attempt
                )
                return  # Successfully reconnected – exit backoff loop
            except (ConnectionClosed, WebSocketException, OSError) as exc:
                logger.warning(
                    "WebSocketClient reconnect attempt #%d failed: %s", attempt, exc
                )
            except asyncio.CancelledError:
                logger.info("WebSocketClient reconnect cancelled.")
                return
            except Exception as exc:  # noqa: BLE001
                logger.error(
                    "WebSocketClient unexpected error during reconnect #%d: %s",
                    attempt,
                    exc,
                )

            # Grow delay for next attempt, capped at max_delay
            delay = min(delay * self._backoff_multiplier, self._max_delay)
