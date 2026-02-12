package com.jaguarliu.ai.soul;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Soul 配置 API
 */
@RestController
@RequestMapping("/api/soul")
@RequiredArgsConstructor
public class SoulConfigController {

    private final SoulConfigService soulConfigService;

    /**
     * 获取 Soul 配置
     */
    @GetMapping("/config")
    public Map<String, Object> getConfig() {
        return soulConfigService.getConfig();
    }

    /**
     * 保存 Soul 配置
     */
    @PostMapping("/config")
    public Map<String, Object> saveConfig(@RequestBody Map<String, Object> config) {
        soulConfigService.saveConfig(config);
        return Map.of("success", true);
    }
}
