package com.jaguarliu.ai.nodeconsole;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SafetyPolicyGuardTest {

    @Test
    void testStrictPolicyBlocksSideEffectCommands() {
        SafetyPolicyGuard guard = new SafetyPolicyGuard();
        RemoteCommandClassifier classifier = new RemoteCommandClassifier();

        // 只读命令在 strict 策略下应该需要 HITL
        var classification = classifier.classify("ls -la", "strict");
        assertTrue(classification.requiresHitl(), "Read-only commands need HITL in strict mode");

        // 副作用命令在 strict 策略下应该需要 HITL
        classification = classifier.classify("systemctl restart nginx", "strict");
        assertTrue(classification.requiresHitl(), "Side-effect commands need HITL in strict mode");
    }

    @Test
    void testStandardPolicyAllowsReadOnly() {
        RemoteCommandClassifier classifier = new RemoteCommandClassifier();

        // 只读命令在 standard 策略下应该自动执行
        var classification = classifier.classify("df -h", "standard");
        assertFalse(classification.requiresHitl(), "Read-only commands auto-execute in standard mode");

        // 副作用命令在 standard 策略下需要 HITL
        classification = classifier.classify("systemctl restart nginx", "standard");
        assertTrue(classification.requiresHitl(), "Side-effect commands need HITL in standard mode");
    }

    @Test
    void testRelaxedPolicyAllowsMostCommands() {
        RemoteCommandClassifier classifier = new RemoteCommandClassifier();

        // 只读命令自动执行
        var classification = classifier.classify("ps aux", "relaxed");
        assertFalse(classification.requiresHitl(), "Read-only commands auto-execute in relaxed mode");

        // 副作用命令也自动执行
        classification = classifier.classify("systemctl restart nginx", "relaxed");
        assertFalse(classification.requiresHitl(), "Side-effect commands auto-execute in relaxed mode");

        // 破坏性命令永远被拒绝
        classification = classifier.classify("rm -rf /", "relaxed");
        assertEquals(2, classification.level(), "Destructive commands blocked even in relaxed mode");
    }

    @Test
    void testDestructiveCommandsAlwaysBlocked() {
        RemoteCommandClassifier classifier = new RemoteCommandClassifier();

        String[] policies = {"strict", "standard", "relaxed"};
        String[] destructiveCommands = {
            "rm -rf /",
            "shutdown now",
            "kubectl delete namespace production",
            "DROP DATABASE users"
        };

        for (String policy : policies) {
            for (String command : destructiveCommands) {
                var classification = classifier.classify(command, policy);
                assertEquals(2, classification.level(),
                    "Destructive command should be level 2 in " + policy + " mode: " + command);
            }
        }
    }
}
