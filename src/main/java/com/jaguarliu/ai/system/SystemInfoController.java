package com.jaguarliu.ai.system;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统信息和环境检测接口
 */
@Slf4j
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemInfoController {

    private final SystemInfoService systemInfoService;

    /**
     * 获取系统信息
     */
    @GetMapping("/info")
    public Map<String, Object> getSystemInfo() {
        SystemInfoService.SystemInfo info = systemInfoService.getSystemInfo();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("os", info.getOs());
        response.put("osVersion", info.getOsVersion());
        response.put("architecture", info.getArchitecture());
        response.put("javaVersion", info.getJavaVersion());
        response.put("javaVendor", info.getJavaVendor());
        response.put("userHome", info.getUserHome());
        response.put("userName", info.getUserName());
        response.put("totalMemory", info.getTotalMemory());
        response.put("freeMemory", info.getFreeMemory());
        response.put("maxMemory", info.getMaxMemory());
        response.put("availableProcessors", info.getAvailableProcessors());

        return response;
    }

    /**
     * 检测环境
     */
    @GetMapping("/environment")
    public Map<String, Object> checkEnvironment() {
        List<SystemInfoService.EnvironmentCheck> checks = systemInfoService.checkEnvironments();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("environments", checks);

        return response;
    }
}
