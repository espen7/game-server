package game.engine.core.persistence.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记实体字段与数据库列的映射关系，用于 Delta 增量更新。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface DeltaColumn {

    /**
     * 数据库列名
     */
    String name();

    /**
     * DeltaEntity 中的字段索引 (用于检查 dirty)
     */
    int index();
}
