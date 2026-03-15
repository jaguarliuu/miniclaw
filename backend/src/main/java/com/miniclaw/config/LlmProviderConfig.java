package com.miniclaw.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmProviderConfig {

    private String id;

    private String endpoint;

    private String apiKey;

    private List<String> models;
}
