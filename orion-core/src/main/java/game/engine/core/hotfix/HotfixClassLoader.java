package game.engine.core.hotfix;

import java.net.URL;
import java.net.URLClassLoader;

/**
 * 热更类加载器�?
 * 每次热更时都会创建一个新的实例，以加载新的类定义�?
 */
public class HotfixClassLoader extends URLClassLoader {

    public HotfixClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    /**
     * 简单的工厂方法，用于从指定路径创建加载�?
     */
    public static HotfixClassLoader create(String path, ClassLoader parent) {
        try {
            URL url = java.nio.file.Paths.get(path).toUri().toURL();
            return new HotfixClassLoader(new URL[]{url}, parent);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create HotfixClassLoader", e);
        }
    }
    
    // 在实际生产中，可能需要重�?loadClass 来打破双亲委派，
    // 以便优先加载 hotfix 目录下的类，即使父加载器中已经存在�?
    // 这里为了简单演示，假设我们加载的是全新的类或者通过接口调用的实现类�?
    // 如果要替换已有的类，通常需要确保新类不在父加载器的 classpath 中，
    // 或者在这里强制优先加载�?
    
    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        // 简单实现：先尝试自己加载，失败再找父类 (打破双亲委派的一种简单方式，仅针对特定包)
        // 注意：java.* 等核心类必须由父加载器加�?
        if (name.startsWith("game.engine.logic") || name.startsWith("game.scripts")) {
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    try {
                        c = findClass(name);
                    } catch (ClassNotFoundException e) {
                        // ignore
                    }
                }
                if (c != null) {
                    return c;
                }
            }
        }
        return super.loadClass(name);
    }
}
