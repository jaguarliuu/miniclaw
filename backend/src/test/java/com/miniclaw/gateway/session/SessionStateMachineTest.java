package com.miniclaw.gateway.session;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SessionStateMachineTest {

    private final SessionStateMachine stateMachine = new SessionStateMachine();

    @Test
    void shouldCreateNewSessionInIdleState() {
        GatewaySession session = new GatewaySession(
                "session-001",
                "connection-001",
                Instant.parse("2026-03-22T10:15:30Z"),
                SessionState.IDLE
        );

        assertEquals(SessionState.IDLE, session.getState());
    }

    @Test
    void shouldAllowIdleToRunningTransition() {
        GatewaySession session = new GatewaySession(
                "session-001",
                "connection-001",
                Instant.parse("2026-03-22T10:15:30Z"),
                SessionState.IDLE
        );

        stateMachine.transition(session, SessionState.RUNNING);

        assertEquals(SessionState.RUNNING, session.getState());
    }

    @Test
    void shouldAllowRunningToIdleTransition() {
        GatewaySession session = new GatewaySession(
                "session-001",
                "connection-001",
                Instant.parse("2026-03-22T10:15:30Z"),
                SessionState.RUNNING
        );

        stateMachine.transition(session, SessionState.IDLE);

        assertEquals(SessionState.IDLE, session.getState());
    }

    @Test
    void shouldRejectRunningToClosedTransition() {
        GatewaySession session = new GatewaySession(
                "session-001",
                "connection-001",
                Instant.parse("2026-03-22T10:15:30Z"),
                SessionState.RUNNING
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> stateMachine.transition(session, SessionState.CLOSED)
        );

        assertEquals("Illegal session state transition: RUNNING -> CLOSED", error.getMessage());
    }

    @Test
    void shouldRejectChatSendWhenSessionIsClosed() {
        GatewaySession session = new GatewaySession(
                "session-001",
                "connection-001",
                Instant.parse("2026-03-22T10:15:30Z"),
                SessionState.CLOSED
        );

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> stateMachine.assertAllowsMethod(session, "chat.send")
        );

        assertEquals("Session session-001 in state CLOSED does not allow method chat.send", error.getMessage());
    }
}
