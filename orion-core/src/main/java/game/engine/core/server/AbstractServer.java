package game.engine.core.server;

import game.engine.core.OrionEngine;
import game.engine.core.ProcessType;
import org.apache.pekko.actor.ActorSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Abstract base class for all game servers.
 * Encapsulates common startup logic.
 */
public abstract class AbstractServer {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected ActorSystem system;
    protected int instanceId;

    /**
     * Entry point for the server.
     * 
     * @param args Command line arguments
     */
    public void boot(String[] args) {
        // 1. Parse Instance ID
        this.instanceId = parseInstanceId(args);

        // 2. Determine Process Type
        ProcessType processType = getProcessType();

        logger.info("Starting {} with instanceId: {}", processType, instanceId);

        // 3. Create OrionEngine
        OrionEngine engine = OrionEngine.create()
                .withProcessType(processType)
                .withPort(processType.getPort(instanceId));

        // 4. Configure Seed Nodes (Gateway 0 is usually the seed)
        // Logic: If I am NOT the first gateway (gateway-0), I should join the cluster
        // via seed.
        // If I am gateway-0, I am the seed (handled by OrionEngine defaults usually,
        // but let's be explicit if needed).
        // For now, we follow existing logic: if instanceId > 0 or not gateway, we join.
        // Actually, existing logic in GatewayServer was: if instanceId > 0,
        // withDefaultSeedNode().
        // Existing logic in Player/World was: always withDefaultSeedNode().
        // Let's standardize: Everyone connects to the seed node(s).
        // If this node IS the seed node, withDefaultSeedNode() usually handles "joining
        // itself" or just binding.
        // Let's check OrionEngine logic if possible, but safely we can call it.
        // However, GatewayServer only called it if instanceId > 0. This implies
        // Gateway-0 starts as a standalone cluster leader.
        // Let's replicate that logic for Gateway, but for others (Player/World), they
        // always join.

        if (shouldJoinCluster()) {
            engine.withDefaultSeedNode();
        }

        // 5. Start ActorSystem
        this.system = engine.start();
        logger.info("ActorSystem created: {}, instance: {}, port: {}",
                system.name(), instanceId, processType.getPort(instanceId));

        // 6. Custom Startup Logic
        try {
            onStart(system, instanceId);
        } catch (Exception e) {
            logger.error("Failed to start server", e);
            System.exit(1);
        }

        // 7. Shutdown Hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Stopping {}...", processType);
            try {
                onStop();
            } catch (Exception e) {
                logger.error("Error during stop", e);
            }
            system.terminate();
            logger.info("{} stopped", processType);
        }));
    }

    private int parseInstanceId(String[] args) {
        if (args.length > 0) {
            try {
                return Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                logger.warn("Invalid instance ID '{}', defaulting to 0", args[0]);
            }
        }
        return 0;
    }

    /**
     * Define the process type for this server.
     */
    protected abstract ProcessType getProcessType();

    /**
     * Custom startup logic (e.g., starting Netty, creating Actors).
     */
    protected abstract void onStart(ActorSystem system, int instanceId);

    /**
     * Custom shutdown logic.
     */
    protected void onStop() {
        // Default empty
    }

    /**
     * Whether this node should join the cluster via default seed nodes.
     * Default is true. Gateway-0 might override this.
     */
    protected boolean shouldJoinCluster() {
        return true;
    }
}
