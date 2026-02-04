package com.jaguarliu.ai.skills.gating;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Skill 可用性检查结果
 *
 * 记录所有不满足的条件，便于 UI 展示和诊断
 */
@Getter
@Builder
public class GatingResult {

    /**
     * 是否通过所有检查
     */
    private final boolean available;

    /**
     * 缺失的环境变量
     */
    @Builder.Default
    private final List<String> missingEnvVars = Collections.emptyList();

    /**
     * 缺失的必需二进制程序
     */
    @Builder.Default
    private final List<String> missingBins = Collections.emptyList();

    /**
     * anyBins 全部缺失时记录所有候选
     */
    @Builder.Default
    private final List<String> unsatisfiedAnyBins = Collections.emptyList();

    /**
     * 缺失或为 false 的配置项
     */
    @Builder.Default
    private final List<String> missingConfigs = Collections.emptyList();

    /**
     * 当前 OS 不在支持列表时记录当前 OS
     */
    private final String unsupportedOs;

    /**
     * 无条件通过的结果（用于无 requires 的 skill）
     */
    public static final GatingResult PASSED = GatingResult.builder()
            .available(true)
            .build();

    /**
     * 获取人类可读的失败原因
     */
    public String getFailureReason() {
        if (available) {
            return null;
        }

        List<String> reasons = new ArrayList<>();

        if (!missingEnvVars.isEmpty()) {
            reasons.add("Missing env vars: " + String.join(", ", missingEnvVars));
        }
        if (!missingBins.isEmpty()) {
            reasons.add("Missing binaries: " + String.join(", ", missingBins));
        }
        if (!unsatisfiedAnyBins.isEmpty()) {
            reasons.add("Need one of: " + String.join(", ", unsatisfiedAnyBins));
        }
        if (!missingConfigs.isEmpty()) {
            reasons.add("Missing configs: " + String.join(", ", missingConfigs));
        }
        if (unsupportedOs != null) {
            reasons.add("Unsupported OS: " + unsupportedOs);
        }

        return String.join("; ", reasons);
    }

    /**
     * 是否有任何缺失项
     */
    public boolean hasMissing() {
        return !available;
    }

    /**
     * 获取所有缺失项的总数
     */
    public int getTotalMissingCount() {
        int count = missingEnvVars.size() + missingBins.size() + missingConfigs.size();
        if (!unsatisfiedAnyBins.isEmpty()) count++;
        if (unsupportedOs != null) count++;
        return count;
    }
}
