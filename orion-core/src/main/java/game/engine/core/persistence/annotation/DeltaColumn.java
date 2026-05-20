package game.engine.core.persistence.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记实体字段与数据库列的映射关系，用于 Delta 增量更新。
 * 
 * <p>注：index 参数应使用编译期生成的 XXXFields 常量，确保类型安全。
 * <p>编译后会自动生成 XXXFields.java 类，包含所有字段的索引常量。
 * 
 * <p>使用示例：
 * <pre>
 * public class Player extends DeltaEntity {
 *     &#64;DeltaColumn(name = "nickname", index = PlayerFields.NICKNAME)
 *     private String nickname;
 *     
 *     public void setNickname(String nickname) {
 *         this.nickname = nickname;
 *         markDirty(PlayerFields.NICKNAME);
 *     }
 * }
 * </pre>
 */
@Retention(RetentionPolicy.RUNTIME)  // 运行时需要通过反射读取列映射信息
@Target(ElementType.FIELD)
public @interface DeltaColumn {

    /**
     * 数据库列名
     */
    String name();
    
    /**
     * 字段索引（应使用编译期生成的 XXXFields 常量）
     * 
     * <p>示例：PlayerFields.NICKNAME
     */
    int index();
}
