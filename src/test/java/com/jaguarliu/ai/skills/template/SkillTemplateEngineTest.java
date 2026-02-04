package com.jaguarliu.ai.skills.template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SkillTemplateEngine 单元测试
 */
@DisplayName("SkillTemplateEngine Tests")
class SkillTemplateEngineTest {

    private SkillTemplateEngine engine;

    @BeforeEach
    void setUp() {
        engine = new SkillTemplateEngine();
    }

    @Nested
    @DisplayName("简单变量替换")
    class SimpleVariableTests {

        @Test
        @DisplayName("替换 $ARGUMENTS")
        void replaceArguments() {
            String template = "User request: $ARGUMENTS";
            var context = engine.createContext().withArguments("fix the bug");

            String result = engine.render(template, context);

            assertEquals("User request: fix the bug", result);
        }

        @Test
        @DisplayName("替换多个变量")
        void replaceMultipleVars() {
            String template = "Project: $PROJECT_ROOT, File: $CURRENT_FILE";
            var context = engine.createContext()
                    .withProjectRoot("/home/user/project")
                    .withCurrentFile("src/main.java");

            String result = engine.render(template, context);

            assertEquals("Project: /home/user/project, File: src/main.java", result);
        }

        @Test
        @DisplayName("未定义变量保留原样")
        void undefinedVarKept() {
            String template = "Args: $ARGUMENTS, Unknown: $UNKNOWN_VAR";
            var context = engine.createContext().withArguments("test");

            String result = engine.render(template, context);

            assertEquals("Args: test, Unknown: $UNKNOWN_VAR", result);
        }

        @Test
        @DisplayName("变量值包含特殊字符")
        void valueWithSpecialChars() {
            String template = "Selection: $SELECTION";
            var context = engine.createContext()
                    .withSelection("function test() { return $value; }");

            String result = engine.render(template, context);

            assertEquals("Selection: function test() { return $value; }", result);
        }

        @Test
        @DisplayName("变量值包含换行")
        void valueWithNewlines() {
            String template = "Code:\n$SELECTION";
            var context = engine.createContext()
                    .withSelection("line1\nline2\nline3");

            String result = engine.render(template, context);

            assertEquals("Code:\nline1\nline2\nline3", result);
        }
    }

    @Nested
    @DisplayName("复杂变量替换")
    class ComplexVariableTests {

        @Test
        @DisplayName("替换环境变量 ${ENV:PATH}")
        void replaceEnvVar() {
            String template = "Path: ${ENV:PATH}";
            var context = engine.createContext();

            String result = engine.render(template, context);

            // PATH 环境变量应该存在
            assertFalse(result.contains("${ENV:PATH}"));
            assertTrue(result.startsWith("Path: "));
        }

        @Test
        @DisplayName("不存在的环境变量保留原样")
        void missingEnvVarKept() {
            String template = "Value: ${ENV:NONEXISTENT_VAR_12345}";
            var context = engine.createContext();

            String result = engine.render(template, context);

            assertEquals("Value: ${ENV:NONEXISTENT_VAR_12345}", result);
        }

        @Test
        @DisplayName("替换配置变量 ${CONFIG:key}")
        void replaceConfigVar() {
            String template = "API: ${CONFIG:api.endpoint}";
            var context = engine.createContext()
                    .setConfig("api.endpoint", "https://api.example.com");

            String result = engine.render(template, context);

            assertEquals("API: https://api.example.com", result);
        }

        @Test
        @DisplayName("替换自定义变量 ${VAR:name}")
        void replaceCustomVar() {
            String template = "Custom: ${VAR:MY_VAR}";
            var context = engine.createContext().set("MY_VAR", "custom_value");

            String result = engine.render(template, context);

            assertEquals("Custom: custom_value", result);
        }
    }

    @Nested
    @DisplayName("转义处理")
    class EscapeTests {

        @Test
        @DisplayName("$$ 转义为 $")
        void dollarEscape() {
            String template = "Price: $$100";
            var context = engine.createContext();

            String result = engine.render(template, context);

            assertEquals("Price: $100", result);
        }

        @Test
        @DisplayName("$$ARGUMENTS 转义为 $ARGUMENTS 字面量")
        void escapeVariable() {
            String template = "Literal: $$ARGUMENTS, Replaced: $ARGUMENTS";
            var context = engine.createContext().withArguments("test");

            String result = engine.render(template, context);

            assertEquals("Literal: $ARGUMENTS, Replaced: test", result);
        }

        @Test
        @DisplayName("多个 $$ 转义")
        void multipleEscapes() {
            String template = "$$a $$b $$c";
            var context = engine.createContext();

            String result = engine.render(template, context);

            assertEquals("$a $b $c", result);
        }
    }

    @Nested
    @DisplayName("边界情况")
    class EdgeCaseTests {

        @Test
        @DisplayName("null 模板返回 null")
        void nullTemplate() {
            String result = engine.render(null, engine.createContext());

            assertNull(result);
        }

        @Test
        @DisplayName("空模板返回空")
        void emptyTemplate() {
            String result = engine.render("", engine.createContext());

            assertEquals("", result);
        }

        @Test
        @DisplayName("无变量的模板原样返回")
        void noVariables() {
            String template = "Just plain text without any variables.";
            var context = engine.createContext();

            String result = engine.render(template, context);

            assertEquals(template, result);
        }

        @Test
        @DisplayName("只有 $ 符号不是变量")
        void loneDollar() {
            String template = "Cost: $ 100";
            var context = engine.createContext();

            String result = engine.render(template, context);

            assertEquals("Cost: $ 100", result);
        }

        @Test
        @DisplayName("$后跟小写字母不是变量")
        void lowercaseNotVariable() {
            String template = "$lowercase is not a variable";
            var context = engine.createContext();

            String result = engine.render(template, context);

            assertEquals("$lowercase is not a variable", result);
        }

        @Test
        @DisplayName("变量名边界：数字开头无效")
        void numberStartInvalid() {
            String template = "$123 is not valid";
            var context = engine.createContext();

            String result = engine.render(template, context);

            assertEquals("$123 is not valid", result);
        }
    }

    @Nested
    @DisplayName("实际 Skill 模板")
    class RealWorldTests {

        @Test
        @DisplayName("完整的 commit skill 模板")
        void commitSkillTemplate() {
            String template = """
                    # Git Commit Assistant

                    Working directory: $PROJECT_ROOT
                    Current branch: $GIT_BRANCH

                    User request: $ARGUMENTS

                    Please analyze the changes and create a commit.
                    """;

            var context = engine.createContext()
                    .withProjectRoot("/home/user/myproject")
                    .withGitBranch("feature/new-feature")
                    .withArguments("commit the login fix");

            String result = engine.render(template, context);

            assertTrue(result.contains("/home/user/myproject"));
            assertTrue(result.contains("feature/new-feature"));
            assertTrue(result.contains("commit the login fix"));
        }

        @Test
        @DisplayName("代码审查 skill 模板")
        void codeReviewTemplate() {
            String template = """
                    Review the following code:

                    File: $CURRENT_FILE

                    ```
                    $SELECTION
                    ```

                    API Key configured: ${CONFIG:review.api_key}
                    """;

            var context = engine.createContext()
                    .withCurrentFile("src/main/Service.java")
                    .withSelection("public void process() {\n    // TODO\n}")
                    .setConfig("review.api_key", "sk-xxx");

            String result = engine.render(template, context);

            assertTrue(result.contains("src/main/Service.java"));
            assertTrue(result.contains("public void process()"));
            assertTrue(result.contains("sk-xxx"));
        }
    }

    @Nested
    @DisplayName("TemplateContext")
    class ContextTests {

        @Test
        @DisplayName("链式调用")
        void chainedCalls() {
            var context = engine.createContext()
                    .withArguments("test")
                    .withProjectRoot("/root")
                    .withCurrentFile("file.txt")
                    .withCwd("/cwd")
                    .withGitBranch("main");

            assertEquals("test", context.get("ARGUMENTS"));
            assertEquals("/root", context.get("PROJECT_ROOT"));
            assertEquals("file.txt", context.get("CURRENT_FILE"));
            assertEquals("/cwd", context.get("CWD"));
            assertEquals("main", context.get("GIT_BRANCH"));
        }

        @Test
        @DisplayName("批量设置")
        void batchSet() {
            var context = engine.createContext()
                    .withAll(Map.of(
                            "VAR1", "value1",
                            "VAR2", "value2"
                    ));

            assertEquals("value1", context.get("VAR1"));
            assertEquals("value2", context.get("VAR2"));
        }

        @Test
        @DisplayName("has 方法")
        void hasMethod() {
            var context = engine.createContext().withArguments("test");

            assertTrue(context.has("ARGUMENTS"));
            assertFalse(context.has("UNKNOWN"));
        }

        @Test
        @DisplayName("getAll 返回不可变副本")
        void getAllImmutable() {
            var context = engine.createContext().withArguments("test");
            var all = context.getAll();

            assertThrows(UnsupportedOperationException.class, () -> {
                all.put("NEW", "value");
            });
        }

        @Test
        @DisplayName("null 值被忽略")
        void nullIgnored() {
            var context = engine.createContext()
                    .set("KEY", null)
                    .set(null, "value");

            assertFalse(context.has("KEY"));
            assertNull(context.get("KEY"));
        }
    }
}
