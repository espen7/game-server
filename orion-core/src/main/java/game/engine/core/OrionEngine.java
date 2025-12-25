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

    private String role;
    private String configResource = "application.conf";
    private final List<String> seedNodes = new ArrayList<>();
    private int port = -1;
    private String hostname = null;

    private OrionEngine() {}

    public static OrionEngine create() {
        return new OrionEngine();
    }

    public OrionEngine withRole(String role) {
        this.role = role;
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

    public OrionEngine withPort(int port) {
        this.port = port;
        return this;
    }

    public OrionEngine withHostname(String hostname) {
        this.hostname = hostname;
        return this;
    }

    public ActorSystem start() {
        logger.info("Starting Orion Server with role: {}", role);

        // 加载基础配置
        Config baseConfig = ConfigFactory.load(configResource);

        // 覆盖配置
        Map<String, Object> overrides = new HashMap<>();
        if (role != null) {
            overrides.put("pekko.cluster.roles", Arrays.asList(role));
        }
        if (port != -1) {
            overrides.put("pekko.remote.artery.canonical.port", port);
        }
        if (hostname != null) {
            overrides.put("pekko.remote.artery.canonical.hostname", hostname);
        } else {
            // 如果未指定，自动检测本�?IP
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

        logger.info("Orion Server started on port: {}", finalConfig.getInt("pekko.remote.artery.canonical.port"));
        return system;
    }
}
