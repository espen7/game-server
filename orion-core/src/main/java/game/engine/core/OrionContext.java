package game.engine.core;

import org.apache.pekko.actor.ActorSystem;

/**
 * Orion 引擎全局上下文，提供 ActorSystem 和进程类型的全局访问
 */
public class OrionContext {
    private static volatile ActorSystem system;
    private static volatile ProcessType processType;

    public static void setSystem(ActorSystem system) {
        OrionContext.system = system;
    }

    public static ActorSystem getSystem() {
        if (system == null) {
            throw new IllegalStateException("OrionContext has not been initialized. Call OrionEngine.start() first.");
        }
        return system;
    }

    public static void setProcessType(ProcessType type) {
        OrionContext.processType = type;
    }

    public static ProcessType getProcessType() {
        if (processType == null) {
            throw new IllegalStateException("ProcessType has not been set. Call OrionEngine.start() with a valid ProcessType.");
        }
        return processType;
    }
}
