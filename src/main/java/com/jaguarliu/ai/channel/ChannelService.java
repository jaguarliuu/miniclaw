package com.jaguarliu.ai.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jaguarliu.ai.nodeconsole.CredentialCipher;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelService {

    private final ChannelRepository channelRepository;
    private final CredentialCipher credentialCipher;
    private final ObjectMapper objectMapper;

    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();

    private static final int MAX_RESPONSE_LENGTH = 32000;

    /**
     * 按 name 或 type 解析渠道：先精确匹配 name，找不到则按 type 取第一个启用的渠道
     */
    private ChannelEntity resolveChannel(String nameOrType, String expectedType) {
        // 1. 先按 name 精确查找
        Optional<ChannelEntity> byName = channelRepository.findByName(nameOrType);
        if (byName.isPresent()) {
            ChannelEntity ch = byName.get();
            if (!expectedType.equals(ch.getType())) {
                throw new IllegalArgumentException(
                        "Channel '" + nameOrType + "' is type '" + ch.getType() + "', expected '" + expectedType + "'");
            }
            return ch;
        }

        // 2. 按 type 查找第一个启用的渠道（支持传入 "email" / "webhook" 直接匹配类型）
        List<ChannelEntity> byType = channelRepository.findByEnabledTrueAndType(nameOrType);
        if (!byType.isEmpty()) {
            return byType.get(0);
        }

        // 3. 如果传入的既不是 name 也不是 type，再用 expectedType 兜底查一次
        if (!nameOrType.equals(expectedType)) {
            byType = channelRepository.findByEnabledTrueAndType(expectedType);
            if (!byType.isEmpty()) {
                return byType.get(0);
            }
        }

        // 列出可用渠道帮助排错
        List<String> available = channelRepository.findByTypeOrderByCreatedAtDesc(expectedType)
                .stream().map(ChannelEntity::getName).toList();
        String hint = available.isEmpty()
                ? "No " + expectedType + " channels configured. Please create one in /channels first."
                : "Available " + expectedType + " channels: " + String.join(", ", available);
        throw new IllegalArgumentException(
                "Channel not found: '" + nameOrType + "'. " + hint);
    }

    public ChannelEntity create(String name, String type, String configJson, String credential) {
        if (channelRepository.existsByName(name)) {
            throw new IllegalArgumentException("Channel name already exists: " + name);
        }

        var builder = ChannelEntity.builder()
                .name(name)
                .type(type)
                .enabled(true)
                .config(configJson);

        if (credential != null && !credential.isBlank()) {
            CredentialCipher.EncryptedPayload encrypted = credentialCipher.encrypt(credential);
            builder.encryptedCredential(encrypted.ciphertext())
                    .credentialIv(encrypted.iv());
        }

        ChannelEntity saved = channelRepository.save(builder.build());
        log.info("Created channel: name={}, type={}", name, type);
        return saved;
    }

    public void delete(String id) {
        ChannelEntity channel = channelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + id));
        channelRepository.delete(channel);
        log.info("Deleted channel: name={}", channel.getName());
    }

    public List<ChannelEntity> listAll() {
        return channelRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<ChannelEntity> findByName(String name) {
        return channelRepository.findByName(name);
    }

    @SuppressWarnings("unchecked")
    public boolean test(String id) {
        ChannelEntity channel = channelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + id));

        boolean success;
        try {
            if ("email".equals(channel.getType())) {
                success = testEmail(channel);
            } else if ("webhook".equals(channel.getType())) {
                success = testWebhook(channel);
            } else {
                log.warn("Unknown channel type for test: {}", channel.getType());
                success = false;
            }
        } catch (Exception e) {
            log.warn("Channel test failed for {}: {}", channel.getName(), e.getMessage());
            success = false;
        }

        channel.setLastTestedAt(LocalDateTime.now());
        channel.setLastTestSuccess(success);
        channelRepository.save(channel);

        log.info("Channel test for {}: {}", channel.getName(), success ? "success" : "failed");
        return success;
    }

    @SuppressWarnings("unchecked")
    public String sendEmail(String channelNameOrType, String to, String subject, String body, String cc) {
        ChannelEntity channel = resolveChannel(channelNameOrType, "email");
        return doSendEmail(channel, to, subject, body, cc);
    }

    public String sendEmailById(String channelId, String to, String subject, String body, String cc) {
        ChannelEntity channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
        return doSendEmail(channel, to, subject, body, cc);
    }

    @SuppressWarnings("unchecked")
    private String doSendEmail(ChannelEntity channel, String to, String subject, String body, String cc) {
        try {
            Map<String, Object> config = objectMapper.readValue(channel.getConfig(), Map.class);
            String host = (String) config.get("host");
            int port = ((Number) config.get("port")).intValue();
            String username = (String) config.get("username");
            String from = (String) config.get("from");
            boolean tls = Boolean.TRUE.equals(config.get("tls"));

            String password = null;
            if (channel.getEncryptedCredential() != null) {
                password = credentialCipher.decrypt(channel.getEncryptedCredential(), channel.getCredentialIv());
            }

            Properties props = new Properties();
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", String.valueOf(port));
            props.put("mail.smtp.auth", "true");
            if (tls) {
                props.put("mail.smtp.starttls.enable", "true");
            }
            props.put("mail.smtp.connectiontimeout", "15000");
            props.put("mail.smtp.timeout", "30000");

            String smtpPassword = password;
            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username, smtpPassword);
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(from));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));
            if (cc != null && !cc.isBlank()) {
                message.setRecipients(Message.RecipientType.CC, InternetAddress.parse(cc));
            }
            message.setSubject(subject, "UTF-8");
            // 如果 body 已经包含 HTML 标签则直接用，否则把纯文本换行转为 <br>
            boolean looksLikeHtml = body.contains("<") && body.contains(">");
            String htmlBody = looksLikeHtml ? body : body.replace("&", "&amp;")
                    .replace("<", "&lt;").replace(">", "&gt;")
                    .replace("\n", "<br>\n");
            message.setContent(htmlBody, "text/html; charset=UTF-8");

            Transport.send(message);
            log.info("Email sent via channel '{}' to {}", channel.getName(), to);
            return "Email sent successfully to " + to;
        } catch (Exception e) {
            log.error("Failed to send email via channel '{}': {}", channel.getName(), e.getMessage());
            throw new RuntimeException("Failed to send email: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public String sendWebhook(String channelNameOrType, String payload) {
        ChannelEntity channel = resolveChannel(channelNameOrType, "webhook");
        return doSendWebhook(channel, payload);
    }

    public String sendWebhookById(String channelId, String payload) {
        ChannelEntity channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelId));
        return doSendWebhook(channel, payload);
    }

    @SuppressWarnings("unchecked")
    private String doSendWebhook(ChannelEntity channel, String payload) {
        try {
            Map<String, Object> config = objectMapper.readValue(channel.getConfig(), Map.class);
            String url = (String) config.get("url");
            String method = (String) config.getOrDefault("method", "POST");
            Map<String, String> headers = (Map<String, String>) config.getOrDefault("headers", Map.of());

            var requestSpec = "PUT".equalsIgnoreCase(method)
                    ? webClient.put().uri(url)
                    : webClient.post().uri(url);

            for (Map.Entry<String, String> h : headers.entrySet()) {
                requestSpec = requestSpec.header(h.getKey(), h.getValue());
            }

            String responseBody = requestSpec
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();

            if (responseBody != null && responseBody.length() > MAX_RESPONSE_LENGTH) {
                responseBody = responseBody.substring(0, MAX_RESPONSE_LENGTH) + "\n[truncated]";
            }

            log.info("Webhook sent via channel '{}' to {}", channel.getName(), url);
            return "Webhook sent successfully. Response: " + (responseBody != null ? responseBody : "(empty)");
        } catch (Exception e) {
            log.error("Failed to send webhook via channel '{}': {}", channel.getName(), e.getMessage());
            throw new RuntimeException("Failed to send webhook: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean testEmail(ChannelEntity channel) throws Exception {
        Map<String, Object> config = objectMapper.readValue(channel.getConfig(), Map.class);
        String host = (String) config.get("host");
        int port = ((Number) config.get("port")).intValue();
        String username = (String) config.get("username");
        boolean tls = Boolean.TRUE.equals(config.get("tls"));

        String password = null;
        if (channel.getEncryptedCredential() != null) {
            password = credentialCipher.decrypt(channel.getEncryptedCredential(), channel.getCredentialIv());
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        props.put("mail.smtp.auth", "true");
        if (tls) {
            props.put("mail.smtp.starttls.enable", "true");
        }
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");

        Session session = Session.getInstance(props);
        Transport transport = session.getTransport("smtp");
        try {
            transport.connect(host, port, username, password);
            return true;
        } finally {
            transport.close();
        }
    }

    @SuppressWarnings("unchecked")
    private boolean testWebhook(ChannelEntity channel) throws Exception {
        Map<String, Object> config = objectMapper.readValue(channel.getConfig(), Map.class);
        String url = (String) config.get("url");

        webClient.get()
                .uri(url)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(10))
                .block();
        return true;
    }

    public static Map<String, Object> toDto(ChannelEntity channel) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", channel.getId());
        dto.put("name", channel.getName());
        dto.put("type", channel.getType());
        dto.put("enabled", channel.isEnabled());
        try {
            dto.put("config", new ObjectMapper().readValue(channel.getConfig(), Map.class));
        } catch (JsonProcessingException e) {
            dto.put("config", channel.getConfig());
        }
        dto.put("lastTestedAt", channel.getLastTestedAt() != null ? channel.getLastTestedAt().toString() : null);
        dto.put("lastTestSuccess", channel.getLastTestSuccess());
        dto.put("createdAt", channel.getCreatedAt() != null ? channel.getCreatedAt().toString() : null);
        dto.put("updatedAt", channel.getUpdatedAt() != null ? channel.getUpdatedAt().toString() : null);
        return dto;
    }
}
