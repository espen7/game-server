package game.engine.core.rpc;

/**
 * 服务提供者接口
 * 定义服务提供方需要实现的方法契约
 */
public interface ServiceProvider {
    
    /**
     * 获取服务名称
     * @return 服务名称
     */
    String getServiceName();
    
    /**
     * 调用服务方法
     * @param methodName 方法名称
     * @param parameters 参数列表
     * @return 调用结果
     * @throws RpcException 调用失败时抛出
     */
    Object invokeMethod(String methodName, Object... parameters) throws RpcException;
    
    /**
     * 检查服务是否可用
     * @return true表示可用
     */
    boolean isAvailable();
    
    /**
     * 获取服务健康状态信息
     * @return 健康状态描述
     */
    String getHealthStatus();
}