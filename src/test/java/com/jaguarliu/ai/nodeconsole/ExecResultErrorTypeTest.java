package com.jaguarliu.ai.nodeconsole;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExecResultErrorTypeTest {

    @Test
    void testSuccessResultHasNoErrorType() {
        ExecResult result = new ExecResult.Builder()
                .stdout("output")
                .exitCode(0)
                .build();

        assertEquals(ExecResult.ErrorType.NONE, result.getErrorType());
        assertFalse(result.formatOutput().contains("Error type"));
    }

    @Test
    void testTimeoutResultHasTimeoutErrorType() {
        ExecResult result = new ExecResult.Builder()
                .stderr("timed out")
                .exitCode(-1)
                .timedOut(true)
                .build();

        assertEquals(ExecResult.ErrorType.TIMEOUT, result.getErrorType());
        assertTrue(result.formatOutput().contains("Error type: TIMEOUT"));
    }

    @Test
    void testExplicitErrorType() {
        ExecResult result = new ExecResult.Builder()
                .stderr("Permission denied")
                .exitCode(1)
                .errorType(ExecResult.ErrorType.PERMISSION_DENIED)
                .build();

        assertEquals(ExecResult.ErrorType.PERMISSION_DENIED, result.getErrorType());
        assertTrue(result.formatOutput().contains("Error type: PERMISSION_DENIED"));
    }

    @Test
    void testAuthenticationFailedErrorType() {
        ExecResult result = new ExecResult.Builder()
                .stderr("Authentication failed")
                .exitCode(-1)
                .errorType(ExecResult.ErrorType.AUTHENTICATION_FAILED)
                .build();

        assertEquals(ExecResult.ErrorType.AUTHENTICATION_FAILED, result.getErrorType());
        assertTrue(result.formatOutput().contains("AUTHENTICATION_FAILED"));
    }

    @Test
    void testValidationErrorType() {
        ExecResult result = new ExecResult.Builder()
                .stderr("Command validation failed")
                .exitCode(-1)
                .errorType(ExecResult.ErrorType.VALIDATION_ERROR)
                .build();

        assertEquals(ExecResult.ErrorType.VALIDATION_ERROR, result.getErrorType());
    }

    @Test
    void testResourceNotFoundErrorType() {
        ExecResult result = new ExecResult.Builder()
                .stderr("Pod not found")
                .exitCode(-1)
                .errorType(ExecResult.ErrorType.RESOURCE_NOT_FOUND)
                .build();

        assertEquals(ExecResult.ErrorType.RESOURCE_NOT_FOUND, result.getErrorType());
    }

    @Test
    void testNetworkErrorType() {
        ExecResult result = new ExecResult.Builder()
                .stderr("Connection refused")
                .exitCode(-1)
                .errorType(ExecResult.ErrorType.NETWORK_ERROR)
                .build();

        assertEquals(ExecResult.ErrorType.NETWORK_ERROR, result.getErrorType());
    }

    @Test
    void testFormatOutputIncludesAllContext() {
        ExecResult result = new ExecResult.Builder()
                .stdout("partial output")
                .stderr("error message")
                .exitCode(1)
                .truncated(true)
                .originalLength(100000)
                .errorType(ExecResult.ErrorType.INTERNAL_ERROR)
                .build();

        String output = result.formatOutput();
        assertTrue(output.contains("partial output"));
        assertTrue(output.contains("error message"));
        assertTrue(output.contains("exit code: 1"));
        assertTrue(output.contains("truncated"));
        assertTrue(output.contains("Error type: INTERNAL_ERROR"));
    }
}
