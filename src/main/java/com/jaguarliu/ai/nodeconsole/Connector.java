package com.jaguarliu.ai.nodeconsole;

/**
 * 远程连接器接口
 * SSH / K8s / DB 各自实现
 */
public interface Connector {

    /**
     * 连接器类型标识
     */
    String getType();

    /**
     * 执行远程命令
     *
     * @param credential     解密后的凭据（密码、私钥、kubeconfig 等）
     * @param node           节点实体
     * @param command        要执行的命令
     * @param timeoutSeconds 超时秒数
     * @return 命令输出
     */
    String execute(String credential, NodeEntity node, String command, int timeoutSeconds);

    /**
     * 测试连接
     *
     * @param credential 解密后的凭据
     * @param node       节点实体
     * @return 是否连接成功
     */
    boolean testConnection(String credential, NodeEntity node);
}
