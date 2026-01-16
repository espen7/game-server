package game.engine.core.persistence.mybatis;

import game.engine.core.persistence.annotation.DeltaColumn;
import game.engine.core.sync.DeltaEntity;
import game.engine.core.sync.DeltaSnapshot;
import org.apache.ibatis.jdbc.SQL;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MyBatis 动态 SQL 提供者（改进版）。
 * 根据 DeltaEntity 的脏状态生成 UPDATE 语句。
 * 
 * 改进点：
 * 1. 自动判断 INSERT/UPDATE
 * 2. 支持乐观锁（版本号）
 * 3. 缓存优化
 * 4. 直接使用注解的 index 参数，零运行时开销
 */
public class DeltaSqlProvider {

    // 缓存类字段信息，避免频繁反射
    private static final Map<Class<?>, Field[]> CACHED_FIELDS = new ConcurrentHashMap<>();
    private static final Map<Class<?>, String> TABLE_NAME_CACHE = new ConcurrentHashMap<>();

    /**
     * 生成 UPDATE 语句（改进版）
     */
    public String updateDelta(DeltaEntity entity) {
        // 如果是 TRANSIENT 状态，自动转为 INSERT
        if (entity.getState() == DeltaEntity.State.TRANSIENT) {
            return insert(entity);
        }
        
        Class<?> clazz = entity.getClass();
        Field[] fields = CACHED_FIELDS.computeIfAbsent(clazz, Class::getDeclaredFields);

        return new SQL() {
            {
                UPDATE(getTableName(clazz));

                boolean hasChange = false;
                for (Field field : fields) {
                    DeltaColumn anno = field.getAnnotation(DeltaColumn.class);
                    if (anno != null) {
                        // 直接使用注解的 index 参数，零运行时开销
                        if (entity.isFieldDirty(anno.index())) {
                            SET(anno.name() + " = #{" + field.getName() + "}");
                            hasChange = true;
                        }
                    }
                }

                // 如果没有任何字段变更，抛出异常
                if (!hasChange) {
                    throw new IllegalStateException("No dirty fields for entity: " + clazz.getSimpleName() + "#" + getEntityId(entity));
                }

                WHERE("id = #{id}");
                
                // 乐观锁支持：如果版本号大于0，添加版本检查
                if (entity.getVersion() > 0) {
                    WHERE("version = #{version}");
                    SET("version = version + 1");
                }
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
                
                // 如果有版本号字段，初始化为1
                if (entity.getVersion() == 0) {
                    VALUES("version", "1");
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
        return TABLE_NAME_CACHE.computeIfAbsent(clazz, c -> {
            // 简单示例：Player -> player
            // TODO: 可以扩展为驼峰转下划线，或读取 @Table 注解
            return c.getSimpleName().toLowerCase();
        });
    }
    
    /**
     * 获取实体ID（用于日志）
     */
    private Object getEntityId(DeltaEntity entity) {
        try {
            Field idField = entity.getClass().getDeclaredField("id");
            idField.setAccessible(true);
            return idField.get(entity);
        } catch (Exception e) {
            return "unknown";
        }
    }
}
