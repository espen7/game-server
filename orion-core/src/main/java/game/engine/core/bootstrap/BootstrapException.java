package game.engine.core.bootstrap;

/**
 * Bootstrap 异常。
 * 
 * <p>表示 Bootstrap 初始化或关闭过程中的错误。
 * 
 * @since 1.0
 */
public class BootstrapException extends Exception {
    
    public BootstrapException(String message) {
        super(message);
    }
    
    public BootstrapException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public BootstrapException(Throwable cause) {
        super(cause);
    }
}
