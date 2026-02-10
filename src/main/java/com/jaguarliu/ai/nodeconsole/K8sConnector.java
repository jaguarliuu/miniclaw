package com.jaguarliu.ai.nodeconsole;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;

import java.io.StringReader;

/**
 * Kubernetes 连接器
 * 通过 kubeconfig 凭据连接 K8s 集群，执行 kubectl 子命令
 */
@Slf4j
@Component
@ConditionalOnClass(name = "io.kubernetes.client.openapi.ApiClient")
public class K8sConnector implements Connector {

    @Override
    public String getType() {
        return "k8s";
    }

    @Override
    public ExecResult execute(String credential, NodeEntity node, String command, ExecOptions options) {
        try {
            ApiClient client = buildClient(credential, options.getTimeoutSeconds());
            String output = dispatchCommand(client, command);

            // 检查是否超过输出限制
            boolean truncated = false;
            long originalLength = output.length();
            if (output.length() > options.getMaxOutputBytes()) {
                output = output.substring(0, options.getMaxOutputBytes());
                truncated = true;
            }

            return new ExecResult.Builder()
                .stdout(output)
                .exitCode(0)
                .truncated(truncated)
                .originalLength(originalLength)
                .build();

        } catch (Exception e) {
            log.error("K8s execute failed on node {}: {}", node.getAlias(), e.getClass().getSimpleName());
            return new ExecResult.Builder()
                .stderr("K8s execution failed: " + e.getClass().getSimpleName())
                .exitCode(-1)
                .build();
        }
    }

    @Override
    public boolean testConnection(String credential, NodeEntity node) {
        try {
            ApiClient client = buildClient(credential, 10);
            CoreV1Api api = new CoreV1Api(client);
            api.listNamespace().execute();
            return true;
        } catch (Exception e) {
            log.debug("K8s test connection failed for node {}: {}", node.getAlias(), e.getClass().getSimpleName());
            return false;
        }
    }

    private ApiClient buildClient(String kubeconfig, int timeoutSeconds) throws Exception {
        KubeConfig config = KubeConfig.loadKubeConfig(new StringReader(kubeconfig));
        ApiClient client = ClientBuilder.kubeconfig(config).build();
        client.setReadTimeout(timeoutSeconds * 1000);
        client.setConnectTimeout(timeoutSeconds * 1000);
        return client;
    }

    private String dispatchCommand(ApiClient client, String command) throws ApiException {
        String[] parts = command.trim().split("\\s+", 3);
        if (parts.length == 0) {
            throw new IllegalArgumentException("Empty kubectl command");
        }

        String verb = parts[0].toLowerCase();
        String resource = parts.length > 1 ? parts[1].toLowerCase() : "";

        // 解析 namespace 标志
        String namespace = "default";
        if (command.contains("-n ")) {
            int idx = command.indexOf("-n ");
            String rest = command.substring(idx + 3).trim();
            namespace = rest.split("\\s+")[0];
        } else if (command.contains("--namespace ")) {
            int idx = command.indexOf("--namespace ");
            String rest = command.substring(idx + 12).trim();
            namespace = rest.split("\\s+")[0];
        } else if (command.contains("--all-namespaces") || command.contains("-A")) {
            namespace = null; // 所有命名空间
        }

        CoreV1Api coreApi = new CoreV1Api(client);
        AppsV1Api appsApi = new AppsV1Api(client);

        return switch (verb) {
            case "get" -> handleGet(coreApi, appsApi, resource, namespace);
            case "describe" -> handleDescribe(coreApi, resource, namespace, parts);
            case "logs" -> handleLogs(coreApi, namespace, parts);
            default -> throw new IllegalArgumentException(
                    "Unsupported kubectl verb: " + verb + ". Supported: get, describe, logs");
        };
    }

    private String handleGet(CoreV1Api coreApi, AppsV1Api appsApi, String resource, String namespace) throws ApiException {
        return switch (resource) {
            case "pods", "pod", "po" -> {
                var list = namespace != null
                        ? coreApi.listNamespacedPod(namespace).execute()
                        : coreApi.listPodForAllNamespaces().execute();
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("%-50s %-12s %-10s %-8s%n", "NAME", "NAMESPACE", "STATUS", "RESTARTS"));
                for (var pod : list.getItems()) {
                    String name = pod.getMetadata().getName();
                    String ns = pod.getMetadata().getNamespace();
                    String status = pod.getStatus().getPhase();
                    int restarts = pod.getStatus().getContainerStatuses() != null
                            ? pod.getStatus().getContainerStatuses().stream()
                                .mapToInt(cs -> cs.getRestartCount()).sum()
                            : 0;
                    sb.append(String.format("%-50s %-12s %-10s %-8d%n", name, ns, status, restarts));
                }
                yield sb.toString();
            }
            case "deployments", "deployment", "deploy" -> {
                var list = namespace != null
                        ? appsApi.listNamespacedDeployment(namespace).execute()
                        : appsApi.listDeploymentForAllNamespaces().execute();
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("%-50s %-12s %-10s %-10s%n", "NAME", "NAMESPACE", "READY", "REPLICAS"));
                for (var dep : list.getItems()) {
                    String name = dep.getMetadata().getName();
                    String ns = dep.getMetadata().getNamespace();
                    int ready = dep.getStatus().getReadyReplicas() != null ? dep.getStatus().getReadyReplicas() : 0;
                    int replicas = dep.getStatus().getReplicas() != null ? dep.getStatus().getReplicas() : 0;
                    sb.append(String.format("%-50s %-12s %-10s %-10d%n", name, ns, ready + "/" + replicas, replicas));
                }
                yield sb.toString();
            }
            case "services", "service", "svc" -> {
                var list = namespace != null
                        ? coreApi.listNamespacedService(namespace).execute()
                        : coreApi.listServiceForAllNamespaces().execute();
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("%-40s %-12s %-15s %-10s%n", "NAME", "NAMESPACE", "CLUSTER-IP", "TYPE"));
                for (var svc : list.getItems()) {
                    String name = svc.getMetadata().getName();
                    String ns = svc.getMetadata().getNamespace();
                    String clusterIp = svc.getSpec().getClusterIP();
                    String type = svc.getSpec().getType();
                    sb.append(String.format("%-40s %-12s %-15s %-10s%n", name, ns, clusterIp, type));
                }
                yield sb.toString();
            }
            case "namespaces", "namespace", "ns" -> {
                var list = coreApi.listNamespace().execute();
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("%-40s %-10s%n", "NAME", "STATUS"));
                for (var ns : list.getItems()) {
                    sb.append(String.format("%-40s %-10s%n", ns.getMetadata().getName(), ns.getStatus().getPhase()));
                }
                yield sb.toString();
            }
            case "nodes", "node" -> {
                var list = coreApi.listNode().execute();
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("%-40s %-10s%n", "NAME", "STATUS"));
                for (var n : list.getItems()) {
                    String name = n.getMetadata().getName();
                    String status = n.getStatus().getConditions().stream()
                            .filter(c -> "Ready".equals(c.getType()))
                            .map(c -> "True".equals(c.getStatus()) ? "Ready" : "NotReady")
                            .findFirst().orElse("Unknown");
                    sb.append(String.format("%-40s %-10s%n", name, status));
                }
                yield sb.toString();
            }
            default -> throw new IllegalArgumentException("Unsupported resource type: " + resource);
        };
    }

    private String handleDescribe(CoreV1Api coreApi, String resource, String namespace, String[] parts) throws ApiException {
        if (parts.length < 3) {
            throw new IllegalArgumentException("describe requires resource name");
        }
        // 从完整命令的第三部分获取资源名
        String name = parts[2].split("\\s+")[0];
        if ("pod".equals(resource) || "pods".equals(resource) || "po".equals(resource)) {
            var pod = coreApi.readNamespacedPod(name, namespace != null ? namespace : "default").execute();
            return pod.toString();
        }
        throw new IllegalArgumentException("describe not supported for resource: " + resource);
    }

    private String handleLogs(CoreV1Api coreApi, String namespace, String[] parts) throws ApiException {
        if (parts.length < 2) {
            throw new IllegalArgumentException("logs requires pod name");
        }
        String podName = parts[1].split("\\s+")[0];
        String logs = coreApi.readNamespacedPodLog(podName, namespace != null ? namespace : "default")
                .execute();
        return logs != null ? logs : "(empty logs)";
    }
}
