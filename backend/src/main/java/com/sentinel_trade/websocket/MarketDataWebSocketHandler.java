package com.sentinel_trade.websocket;

import com.sentinel_trade.service.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * WebSocket handler that pushes real-time K-line and alert data to clients.
 * Requirements: 5.1, 5.2
 */
@Component
public class MarketDataWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOG = LoggerFactory.getLogger(MarketDataWebSocketHandler.class);

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final RedisService redisService;

    // Default symbols and intervals to push
    private static final String[] SYMBOLS   = {"BTCUSDT", "ETHUSDT"};
    private static final String[] INTERVALS = {"1m", "5m", "1h"};

    public MarketDataWebSocketHandler(RedisService redisService) {
        this.redisService = redisService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        LOG.info("WebSocket connected: sessionId={} total={}", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        LOG.info("WebSocket disconnected: sessionId={} status={} total={}",
                session.getId(), status, sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        LOG.error("WebSocket transport error: sessionId={} error={}",
                session.getId(), exception.getMessage());
        sessions.remove(session);
    }

    /**
     * Push latest K-line data to all connected clients every second.
     * Requirements: 5.2 – push within 50ms of new data
     */
    @Scheduled(fixedRate = 1000)
    public void pushLatestData() {
        if (sessions.isEmpty()) return;

        for (String symbol : SYMBOLS) {
            for (String interval : INTERVALS) {
                redisService.getLatestKLine(symbol, interval).ifPresent(json -> {
                    String message = String.format(
                            "{\"type\":\"kline\",\"symbol\":\"%s\",\"interval\":\"%s\",\"data\":%s}",
                            symbol, interval, json);
                    broadcast(message);
                });
            }
            redisService.getLatestAlert(symbol).ifPresent(json -> {
                String message = String.format(
                        "{\"type\":\"alert\",\"symbol\":\"%s\",\"data\":%s}", symbol, json);
                broadcast(message);
            });
        }
    }

    private void broadcast(String message) {
        TextMessage textMessage = new TextMessage(message);
        for (WebSocketSession session : sessions) {
            if (!session.isOpen()) {
                sessions.remove(session);
                continue;
            }
            try {
                session.sendMessage(textMessage);
            } catch (IOException e) {
                LOG.error("Failed to send to sessionId={}: {}", session.getId(), e.getMessage());
                sessions.remove(session);
            }
        }
    }

    /** Exposed for testing. */
    public int getSessionCount() {
        return sessions.size();
    }
}
