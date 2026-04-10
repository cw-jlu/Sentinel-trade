"""
ThroughputMonitor: tracks messages-per-second and logs stats every N seconds.

Requirements: 8.1, 8.2
"""

from __future__ import annotations

import asyncio
import logging
import threading
import time
from typing import Optional

logger = logging.getLogger(__name__)


class ThroughputMonitor:
    """Thread-safe throughput counter that logs stats at a fixed interval.

    Args:
        report_interval_seconds: How often (in seconds) to log throughput.
            Defaults to 10 (Requirement 8.1: every 10 seconds).
    """

    def __init__(self, report_interval_seconds: int = 10) -> None:
        self._interval = report_interval_seconds
        self._count: int = 0
        self._lock = threading.Lock()
        self._start_time: float = 0.0
        self._timer: Optional[threading.Timer] = None
        self._running: bool = False

    # ------------------------------------------------------------------
    # Lifecycle
    # ------------------------------------------------------------------

    def start(self) -> None:
        """Start the periodic reporting timer."""
        self._running = True
        self._start_time = time.monotonic()
        self._schedule_next()
        logger.info("ThroughputMonitor started (interval=%ds).", self._interval)

    def stop(self) -> None:
        """Stop the timer and log final stats."""
        self._running = False
        if self._timer is not None:
            self._timer.cancel()
            self._timer = None
        self._log_stats(final=True)

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def record_message(self) -> None:
        """Increment the message counter (thread-safe)."""
        with self._lock:
            self._count += 1

    # ------------------------------------------------------------------
    # Internal
    # ------------------------------------------------------------------

    def _schedule_next(self) -> None:
        if not self._running:
            return
        self._timer = threading.Timer(self._interval, self._report)
        self._timer.daemon = True
        self._timer.start()

    def _report(self) -> None:
        self._log_stats()
        self._schedule_next()

    def _log_stats(self, final: bool = False) -> None:
        with self._lock:
            count = self._count
            self._count = 0

        elapsed = time.monotonic() - self._start_time
        self._start_time = time.monotonic()

        rate = count / max(elapsed, 1e-9)
        label = "Final" if final else "Throughput"
        logger.info(
            "%s stats: messages=%d elapsed=%.1fs rate=%.1f msg/s",
            label,
            count,
            elapsed,
            rate,
        )
