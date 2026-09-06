package com.cex.server.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/** /ws로 접속한 클라이언트 전원에게 체결 이벤트를 밀어주는 브로드캐스터. */
@Component
public class TradeBroadcaster extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(TradeBroadcaster.class);

    private final Set<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("웹소켓 연결. 현재 {}개 세션", sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("웹소켓 종료. 현재 {}개 세션", sessions.size());
    }

    /** 개별 세션 전송 실패는 그 세션만 정리하고 나머지에게 계속 보낸다. */
    public void broadcast(String json) {
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                } else {
                    sessions.remove(session);
                }
            } catch (IOException e) {
                log.warn("웹소켓 전송 실패, 세션 제거: {}", e.getMessage());
                sessions.remove(session);
            }
        }
    }
}
