package game.engine.core.rpc;

import java.util.concurrent.CompletableFuture;

/**
 * 服务调用者接口
 * 提供统一的服务调用能力抽象
 */
public interface ServiceInvoker {
    
    /**
     * 异步调用远程服务
     * @param serviceName 服务名称
     * @param methodName 方法名称
     * @param parameters 参数列表
     * @return CompletableFuture<RpcResponse>
     */
    CompletableFuture<RpcResponse> callAsync(String serviceName, String methodName, Object... parameters);
    
    /**
     * 同步调用远程服务（阻塞）
     * @param serviceName 服务名称
     * @param methodName 方法名称
     * @param parameters 参数列表
     * @return 响应结果
     * @throws RpcException 调用失败时抛出
     */
    RpcResponse callSync(String serviceName, String methodName, Object... parameters) throws RpcException;
    
    /**
     * 带超时的同步调用
     * @param serviceName 服务名称
     * @param methodName 方法名称
     * @param timeoutMs 超时时间（毫秒）
     * @param parameters 参数列表
     * @return 响应结果
     * @throws RpcException 调用失败时抛出
     */
    RpcResponse callSyncWithTimeout(String serviceName, String methodName, long timeoutMs, Object... parameters) throws RpcException;
    
    /**
     * 关闭调用者连接
     */
    void close();
}