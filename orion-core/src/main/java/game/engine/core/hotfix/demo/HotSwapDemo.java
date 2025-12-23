package game.engine.core.hotfix.demo;

import org.apache.pekko.actor.ActorRef;
import org.apache.pekko.actor.ActorSystem;
import game.engine.core.OrionEngine;
import game.engine.core.hotfix.FileWatcherActor;

import java.io.File;
import java.util.Scanner;

public class HotSwapDemo {
    public static void main(String[] args) throws Exception {
        // 1. 准备热更目录
        String hotfixPath = "hotfix";
        File hotfixDir = new File(hotfixPath);
        if (!hotfixDir.exists()) {
            hotfixDir.mkdirs();
        }
        System.out.println("Hotfix directory: " + hotfixDir.getAbsolutePath());

        // 2. 启动 ActorSystem
        ActorSystem system = OrionEngine.create()
                .withRole("demo")
                .withPort(2555)
                .start();

        // 3. 启动文件监听 Actor
        system.actorOf(FileWatcherActor.props(hotfixPath), "fileWatcher");

        // 4. 启动演示 Actor
        ActorRef demoActor = system.actorOf(HotSwapActor.props(hotfixPath), "hotSwapActor");

        // 5. 交互循环
        System.out.println("Type something to test logic, or 'exit' to quit.");
        Scanner scanner = new Scanner(System.in);
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if ("exit".equalsIgnoreCase(line)) {
                break;
            }
            demoActor.tell(line, ActorRef.noSender());
        }

        system.terminate();
    }
}
