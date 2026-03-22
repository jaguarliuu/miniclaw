package com.miniclaw.gateway.session;

import org.springframework.stereotype.Component;

@Component
public class SessionStateMachine {

    public void transition(GatewaySession session, SessionState targetState) {
        SessionState currentState = session.getState();
        if (!isAllowedTransition(currentState, targetState)) {
            throw new IllegalStateException(
                    "Illegal session state transition: " + currentState + " -> " + targetState
            );
        }

        session.setState(targetState);
    }

    public void assertAllowsMethod(GatewaySession session, String method) {
        if (session.getState() == SessionState.CLOSED && "chat.send".equals(method)) {
            throw new IllegalStateException(
                    "Session " + session.getSessionId() + " in state CLOSED does not allow method " + method
            );
        }
    }

    private boolean isAllowedTransition(SessionState currentState, SessionState targetState) {
        if (currentState == targetState) {
            return true;
        }

        return switch (currentState) {
            case IDLE -> targetState == SessionState.RUNNING || targetState == SessionState.CLOSED;
            case RUNNING -> targetState == SessionState.IDLE;
            case CLOSED -> false;
        };
    }
}
