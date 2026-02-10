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
     * 执行远程命令并返回结构化结果
     *
     * @param credential     解密后的凭据（密码、私钥、kubeconfig 等）
     * @param node           节点实体
     * @param command        要执行的命令
     * @param options        执行选项（超时、输出限制、环境变量等）
     * @return 结构化执行结果
     */
    ExecResult execute(String credential, NodeEntity node, String command, ExecOptions options);

    /**
     * 测试连接
     *
     * @param credential 解密后的凭据
     * @param node       节点实体
     * @return 是否连接成功
     */
    boolean testConnection(String credential, NodeEntity node);
}
