import asyncio
from unittest.mock import AsyncMock, MagicMock, patch

def _make_ws_mock(messages):
    ws = MagicMock()
    ws.close = AsyncMock()
    msgs = list(messages or [])
    async def _aiter():
        for m in msgs:
            yield m
    ws.__aiter__ = _aiter
    return ws

def _make_connect_cm(ws_mock):
    cm = MagicMock()
    cm.__aenter__ = AsyncMock(return_value=ws_mock)
    cm.__aexit__ = AsyncMock(return_value=False)
    return cm

from websocket_client import WebSocketClient

async def run():
    messages = ['msg1', 'msg2', 'msg3']
    ws_mock = _make_ws_mock(messages)
    received = []
    client = WebSocketClient()

    async def on_message(msg):
        received.append(msg)
        print(f'received {msg}, total={len(received)}', flush=True)
        if len(received) == len(messages):
            print('calling close', flush=True)
            await client.close()
            print('close done', flush=True)

    with patch('websocket_client.websockets.connect', return_value=_make_connect_cm(ws_mock)):
        print('connecting...', flush=True)
        try:
            await asyncio.wait_for(client.connect('ws://test', on_message), timeout=5.0)
            print('connect returned', flush=True)
        except asyncio.TimeoutError:
            print('TIMED OUT', flush=True)

    print('received:', received)

asyncio.run(run())
