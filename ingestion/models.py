"""
Backward-compatible re-export.  The canonical implementation lives at
ingestion/models/tick_data.py.
"""

from ingestion.models.tick_data import TickData  # noqa: F401

__all__ = ["TickData"]
