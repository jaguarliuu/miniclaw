package com.jaguarliu.ai.skills.template;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill 模板引擎
 *
 * 负责在 skill 激活时替换 body 中的变量占位符：
 * - $ARGUMENTS     → 用户输入的参数
 * - $PROJECT_ROOT  → 项目根目录
 * - $CURRENT_FILE  → 当前文件路径
 * - $SELECTION     → 选中的文本
 * - $CLIPBOARD     → 剪贴板内容
 * - ${ENV:VAR}     → 环境变量
 * - ${CONFIG:key}  → 配置值
 *
 * 设计原则：
 * 1. 未提供的变量保留原样（不报错）
 * 2. 支持转义：$$ARGUMENTS → $ARGUMENTS（字面量）
 * 3. 大小写敏感
 */
@Slf4j
@Service
public class SkillTemplateEngine {

    // 简单变量：$VARIABLE_NAME
    private static final Pattern SIMPLE_VAR_PATTERN = Pattern.compile("\\$([A-Z_][A-Z0-9_]*)");

    // 复杂变量：${TYPE:key}
    private static final Pattern COMPLEX_VAR_PATTERN = Pattern.compile("\\$\\{([A-Z]+):([^}]+)}");

    // 转义占位符（使用不可能出现在正常文本中的字符序列）
    private static final String ESCAPE_PLACEHOLDER = "\u0000DOLLAR\u0000";

    /**
     * 渲染模板
     *
     * @param template 原始模板（skill body）
     * @param context  变量上下文
     * @return 渲染后的内容
     */
    public String render(String template, TemplateContext context) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        String result = template;

        // 1. 先处理转义：$$ → 占位符（避免被变量替换）
        result = result.replace("$$", ESCAPE_PLACEHOLDER);

        // 2. 处理复杂变量 ${TYPE:key}
        result = replaceComplexVars(result, context);

        // 3. 处理简单变量 $VARIABLE
        result = replaceSimpleVars(result, context);

        // 4. 最后还原占位符 → $
        result = result.replace(ESCAPE_PLACEHOLDER, "$");

        return result;
    }

    /**
     * 替换简单变量
     */
    private String replaceSimpleVars(String template, TemplateContext context) {
        Matcher matcher = SIMPLE_VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String value = context.get(varName);

            if (value != null) {
                // 需要转义替换字符串中的 $ 和 \
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            } else {
                // 未定义的变量保留原样
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 替换复杂变量
     */
    private String replaceComplexVars(String template, TemplateContext context) {
        Matcher matcher = COMPLEX_VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String type = matcher.group(1);
            String key = matcher.group(2);
            String value = null;

            switch (type) {
                case "ENV" -> value = System.getenv(key);
                case "CONFIG" -> value = context.getConfig(key);
                case "VAR" -> value = context.get(key);
            }

            if (value != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            } else {
                // 未定义的变量保留原样
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    /**
     * 创建基础上下文（包含常用变量）
     */
    public TemplateContext createContext() {
        return new TemplateContext();
    }

    /**
     * 模板上下文
     */
    public static class TemplateContext {
        private final Map<String, String> variables = new HashMap<>();
        private final Map<String, String> configs = new HashMap<>();

        /**
         * 设置变量
         */
        public TemplateContext set(String name, String value) {
            if (name != null && value != null) {
                variables.put(name, value);
            }
            return this;
        }

        /**
         * 获取变量
         */
        public String get(String name) {
            return variables.get(name);
        }

        /**
         * 设置配置
         */
        public TemplateContext setConfig(String key, String value) {
            if (key != null && value != null) {
                configs.put(key, value);
            }
            return this;
        }

        /**
         * 获取配置
         */
        public String getConfig(String key) {
            return configs.get(key);
        }

        /**
         * 设置用户参数（最常用）
         */
        public TemplateContext withArguments(String arguments) {
            return set("ARGUMENTS", arguments);
        }

        /**
         * 设置项目根目录
         */
        public TemplateContext withProjectRoot(String path) {
            return set("PROJECT_ROOT", path);
        }

        /**
         * 设置当前文件
         */
        public TemplateContext withCurrentFile(String path) {
            return set("CURRENT_FILE", path);
        }

        /**
         * 设置选中文本
         */
        public TemplateContext withSelection(String selection) {
            return set("SELECTION", selection);
        }

        /**
         * 设置剪贴板内容
         */
        public TemplateContext withClipboard(String content) {
            return set("CLIPBOARD", content);
        }

        /**
         * 设置当前工作目录
         */
        public TemplateContext withCwd(String cwd) {
            return set("CWD", cwd);
        }

        /**
         * 设置技能基础目录
         */
        public TemplateContext withSkillBasePath(String path) {
            return set("SKILL_BASE_PATH", path);
        }

        /**
         * 设置当前分支
         */
        public TemplateContext withGitBranch(String branch) {
            return set("GIT_BRANCH", branch);
        }

        /**
         * 批量设置变量
         */
        public TemplateContext withAll(Map<String, String> vars) {
            if (vars != null) {
                variables.putAll(vars);
            }
            return this;
        }

        /**
         * 检查是否包含变量
         */
        public boolean has(String name) {
            return variables.containsKey(name);
        }

        /**
         * 获取所有变量（只读）
         */
        public Map<String, String> getAll() {
            return Map.copyOf(variables);
        }
    }
}
