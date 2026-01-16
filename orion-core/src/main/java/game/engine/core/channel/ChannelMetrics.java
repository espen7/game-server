package game.engine.core.channel;

/**
 * 通道监控指标
 */
public class ChannelMetrics {
    private final String channelName;
    private final long processedCount;
    private final long failedCount;
    private final long retryCount;
    private final int queueSize;
    
    public ChannelMetrics(String channelName, long processedCount, long failedCount, 
                         long retryCount, int queueSize) {
        this.channelName = channelName;
        this.processedCount = processedCount;
        this.failedCount = failedCount;
        this.retryCount = retryCount;
        this.queueSize = queueSize;
    }
    
    public String getChannelName() {
        return channelName;
    }
    
    public long getProcessedCount() {
        return processedCount;
    }
    
    public long getFailedCount() {
        return failedCount;
    }
    
    public long getRetryCount() {
        return retryCount;
    }
    
    public int getQueueSize() {
        return queueSize;
    }
    
    public double getSuccessRate() {
        long total = processedCount + failedCount;
        return total == 0 ? 100.0 : (processedCount * 100.0 / total);
    }
    
    @Override
    public String toString() {
        return String.format("ChannelMetrics[channel=%s, processed=%d, failed=%d, retry=%d, queue=%d, successRate=%.2f%%]",
            channelName, processedCount, failedCount, retryCount, queueSize, getSuccessRate());
    }
}
