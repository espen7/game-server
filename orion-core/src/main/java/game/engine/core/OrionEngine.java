package game.engine.core;

import org.apache.pekko.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OrionEngine {
    private static final Logger logger = LoggerFactory.getLogger(OrionEngine.class);
    private static final String CLUSTER_NAME = "OrionCluster";

    private ProcessType processType;
    private String configResource = "application.conf";
    private final List<String> seedNodes = new ArrayList<>();
    private int port = -1;
    private String hostname = null;

    private OrionEngine() {}

    public static OrionEngine create() {
        return new OrionEngine();
    }

    public OrionEngine withProcessType(ProcessType processType) {
        this.processType = processType;
        return this;
    }

    @Deprecated
    public OrionEngine withRole(String role) {
        ProcessType.fromRole(role).ifPresent(type -> this.processType = type);
        return this;
    }

    public OrionEngine withConfig(String resource) {
        this.configResource = resource;
        return this;
    }

    public OrionEngine withSeedNodes(String... nodes) {
        this.seedNodes.addAll(Arrays.asList(nodes));
        return this;
    }

    /**
     * 使用默认的 seed node（Gateway 实例0 的地址）
     * 
     * @param hostname seed node 的主机名或IP（默认 "127.0.0.1"）
     * @return OrionEngine 实例
     */
    public OrionEngine withDefaultSeedNode(String hostname) {
        String seedNode = hostname + ":" + ProcessType.GATEWAY.getBasePort();
        this.seedNodes.add(seedNode);
        logger.debug("Using default seed node: {}", seedNode);
        return this;
    }

    /**
     * 使用本地默认 seed node（127.0.0.1:2551）
     */
    public OrionEngine withDefaultSeedNode() {
        return withDefaultSeedNode("127.0.0.1");
    }

    public OrionEngine withPort(int port) {
        this.port = port;
        return this;
    }

    public OrionEngine withHostname(String hostname) {
        this.hostname = hostname;
        return this;
    }

    public ActorSystem start() {
        if (processType == null) {
            throw new IllegalArgumentException("ProcessType must be specified via withProcessType()");
        }
        
        logger.info("Starting Orion Server with process type: {} [{}]", processType.name(), processType.getDescription());

        // 加载基础配置
        Config baseConfig = ConfigFactory.load(configResource);

        // 覆盖配置
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("pekko.cluster.roles", Arrays.asList(processType.getRole()));
        if (port != -1) {
            overrides.put("pekko.remote.artery.canonical.port", port);
        }
        if (hostname != null) {
            overrides.put("pekko.remote.artery.canonical.hostname", hostname);
        } else {
            // 如果未指定，自动检测本机IP
            try {
                String localIp = java.net.InetAddress.getLocalHost().getHostAddress();
                logger.info("Hostname not specified, auto-detected local IP: {}", localIp);
                overrides.put("pekko.remote.artery.canonical.hostname", localIp);
            } catch (java.net.UnknownHostException e) {
                logger.warn("Failed to auto-detect local IP, falling back to configuration default", e);
            }
        }
        if (!seedNodes.isEmpty()) {
            List<String> fullSeedNodes = new ArrayList<>();
            for (String node : seedNodes) {
                if (!node.startsWith("pekko://")) {
                    fullSeedNodes.add("pekko://" + CLUSTER_NAME + "@" + node);
                } else {
                    fullSeedNodes.add(node);
                }
            }
            overrides.put("pekko.cluster.seed-nodes", fullSeedNodes);
        }

        Config finalConfig = ConfigFactory.parseMap(overrides).withFallback(baseConfig);

        // 创建 Classic ActorSystem
        ActorSystem system = ActorSystem.create(CLUSTER_NAME, finalConfig);
        
        // 初始化上下文
        OrionContext.setSystem(system);
        OrionContext.setProcessType(processType);

        logger.info("Orion Server started - ProcessType: {}, Port: {}", 
                processType, finalConfig.getInt("pekko.remote.artery.canonical.port"));
        return system;
    }
}
