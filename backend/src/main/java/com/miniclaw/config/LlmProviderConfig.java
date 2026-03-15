package com.miniclaw.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmProviderConfig {

    private String id;

    private String endpoint;

    private String apiKey;

    @Builder.Default
    private List<String> models = new ArrayList<>();

    @Builder.Default
    private List<String> multimodalModels = new ArrayList<>();

    public String getDefaultModel() {
        return firstNonBlank(models);
    }

    public String getDefaultMultimodalModel() {
        return firstNonBlank(multimodalModels);
    }

    public boolean supportsMultimodal(String modelName) {
        if (modelName == null || modelName.isBlank() || multimodalModels == null) {
            return false;
        }
        return multimodalModels.stream().anyMatch(modelName::equals);
    }

    private String firstNonBlank(List<String> values) {
        if (values == null) {
            return null;
        }

        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }
}
