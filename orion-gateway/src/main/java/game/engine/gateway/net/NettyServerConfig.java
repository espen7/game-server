package game.engine.gateway.net;

public class NettyServerConfig {
    private int port;
    private int bossThreads;
    private int workerThreads;
    private boolean useEpoll;

    public NettyServerConfig(int port) {
        this.port = port;
        this.bossThreads = 1;
        this.workerThreads = Runtime.getRuntime().availableProcessors() * 2;
        this.useEpoll = true; // Default to try epoll
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getBossThreads() {
        return bossThreads;
    }

    public void setBossThreads(int bossThreads) {
        this.bossThreads = bossThreads;
    }

    public int getWorkerThreads() {
        return workerThreads;
    }

    public void setWorkerThreads(int workerThreads) {
        this.workerThreads = workerThreads;
    }

    public boolean isUseEpoll() {
        return useEpoll;
    }

    public void setUseEpoll(boolean useEpoll) {
        this.useEpoll = useEpoll;
    }
}
