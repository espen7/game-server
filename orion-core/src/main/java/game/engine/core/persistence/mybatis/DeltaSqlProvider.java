package game.engine.core.persistence.mybatis;

import game.engine.core.persistence.annotation.DeltaColumn;
import game.engine.core.sync.DeltaEntity;
import org.apache.ibatis.jdbc.SQL;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyBatis 动态 SQL 提供者。
 * 根据 DeltaEntity 的脏状态生成 UPDATE 语句。
 */
public class DeltaSqlProvider {

    // 缓存类字段信息，避免频繁反射
    private static final Map<Class<?>, Field[]> CACHED_FIELDS = new ConcurrentHashMap<>();

    public String updateDelta(DeltaEntity entity) {
        Class<?> clazz = entity.getClass();
        Field[] fields = CACHED_FIELDS.computeIfAbsent(clazz, Class::getDeclaredFields);

        return new SQL() {
            {
                UPDATE(getTableName(clazz));

                boolean hasChange = false;
                for (Field field : fields) {
                    DeltaColumn anno = field.getAnnotation(DeltaColumn.class);
                    if (anno != null) {
                        // 检查该字段是否 Dirty
                        if (entity.isFieldDirty(anno.index())) {
                            // 使用 #{fieldName} 占位符
                            SET(anno.name() + " = #{" + field.getName() + "}");
                            hasChange = true;
                        }
                    }
                }

                // 如果没有任何字段变更 (理论上不应发生，因为调用前会检查 isDirty)，
                // 但为了 SQL 语法正确性，可以加一个 dummy set 或直接抛错。
                // 这里假设调用方保证 isDirty() 为 true。
                if (!hasChange) {
                    // 防御性编程：如果没有变更，更新 ID (无操作)
                    SET("id = id");
                }

                WHERE("id = #{id}"); // 假设实体都有 id 字段
            }
        }.toString();
    }

    /**
     * 生成 INSERT 语句 (忽略脏标记，插入所有映射字段)
     */
    public String insert(DeltaEntity entity) {
        Class<?> clazz = entity.getClass();
        Field[] fields = CACHED_FIELDS.computeIfAbsent(clazz, Class::getDeclaredFields);

        return new SQL() {
            {
                INSERT_INTO(getTableName(clazz));

                // 插入 ID (假设 ID 不是自增，或者实体已设置 ID)
                VALUES("id", "#{id}");

                for (Field field : fields) {
                    DeltaColumn anno = field.getAnnotation(DeltaColumn.class);
                    if (anno != null) {
                        VALUES(anno.name(), "#{" + field.getName() + "}");
                    }
                }
            }
        }.toString();
    }

    /**
     * 生成 DELETE 语句
     */
    public String delete(DeltaEntity entity) {
        Class<?> clazz = entity.getClass();
        return new SQL() {
            {
                DELETE_FROM(getTableName(clazz));
                WHERE("id = #{id}");
            }
        }.toString();
    }

    // 简单的表名映射策略：类名转下划线，或者读取 @Table 注解 (如果有)
    private String getTableName(Class<?> clazz) {
        // 简单示例：Player -> player
        return clazz.getSimpleName().toLowerCase();
    }
}
