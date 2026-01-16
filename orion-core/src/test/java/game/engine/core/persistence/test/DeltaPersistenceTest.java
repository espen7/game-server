package game.engine.core.persistence.test;

import game.engine.core.persistence.mybatis.DeltaSqlProvider;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DeltaPersistenceTest {

    @Test
    public void testSqlGeneration() {
        TestPlayer player = new TestPlayer(1001L);
        DeltaSqlProvider provider = new DeltaSqlProvider();

        // 1. 初始状态，无 Dirty
        assertFalse(player.isDirty());

        // 2. 修改 HP
        player.setHp(99);
        assertTrue(player.isFieldDirty(TestPlayerFields.HP));

        String sql1 = provider.updateDelta(player);
        System.out.println("SQL1: " + sql1);
        assertTrue(sql1.contains("UPDATE testplayer"));
        assertTrue(sql1.contains("SET hp = #{hp}"));
        assertFalse(sql1.contains("player_name"));
        assertTrue(sql1.contains("WHERE (id = #{id})"));

        // 3. 修改 Name
        player.setName("Hero");
        String sql2 = provider.updateDelta(player);
        System.out.println("SQL2: " + sql2);
        assertTrue(sql2.contains("hp = #{hp}"));
        assertTrue(sql2.contains("player_name = #{name}"));

        // 4. 清除 Dirty
        player.clearDirty();
        assertFalse(player.isDirty());
    }

    @Test
    public void testInsertGeneration() {
        TestPlayer player = new TestPlayer(2002L);
        player.setHp(100);
        player.setName("Newbie");
        player.setLevel(1);

        // Insert 不应该关心 dirty flag，但通常创建对象时 setter 会触发 dirty
        // 即使 clearDirty，insert 也应该包含所有字段
        player.clearDirty();

        DeltaSqlProvider provider = new DeltaSqlProvider();
        String sql = provider.insert(player);
        System.out.println("INSERT SQL: " + sql);

        assertTrue(sql.contains("INSERT INTO testplayer"));
        // MyBatis SQL 生成的 VALUES 可能是换行的，或者格式不同，放宽检查条件
        assertTrue(sql.contains("VALUES"));

        // 检查列名
        assertTrue(sql.contains("hp"));
        assertTrue(sql.contains("player_name"));
        assertTrue(sql.contains("lvl"));
        // 检查占位符
        assertTrue(sql.contains("#{id}"));
        assertTrue(sql.contains("#{hp}"));
        assertTrue(sql.contains("#{name}"));
        assertTrue(sql.contains("#{level}"));
    }

    @Test
    public void testDeleteGeneration() {
        TestPlayer player = new TestPlayer(3003L);
        DeltaSqlProvider provider = new DeltaSqlProvider();
        String sql = provider.delete(player);
        System.out.println("DELETE SQL: " + sql);

        assertTrue(sql.contains("DELETE FROM testplayer"));
        assertTrue(sql.contains("WHERE (id = #{id})"));
    }
}
