package game.engine.core.persistence.mybatis;

import com.alibaba.druid.pool.DruidDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import javax.sql.DataSource;

/**
 * MyBatis 配置类 (单例)。
 * 负责初始化 DataSource 和 SqlSessionFactory。
 */
public class MyBatisConfig {

    private static final MyBatisConfig INSTANCE = new MyBatisConfig();
    private SqlSessionFactory sqlSessionFactory;

    private MyBatisConfig() {
        init();
    }

    public static MyBatisConfig getInstance() {
        return INSTANCE;
    }

    private void init() {
        // 1. 创建数据源 (Druid)
        DruidDataSource dataSource = new DruidDataSource();
        // TODO: 从配置文件读取
        dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource.setUrl("jdbc:mysql://localhost:3306/orion_game?useSSL=false&serverTimezone=UTC");
        dataSource.setUsername("root");
        dataSource.setPassword("root");

        // 2. 创建 MyBatis Configuration
        TransactionFactory transactionFactory = new JdbcTransactionFactory();
        Environment environment = new Environment("development", transactionFactory, dataSource);
        Configuration configuration = new Configuration(environment);

        // 注册 Mapper (如果使用 XML，这里需要 addMappers)
        // configuration.addMapper(PlayerMapper.class);

        // 3. 构建 SqlSessionFactory
        this.sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    public SqlSessionFactory getSqlSessionFactory() {
        return sqlSessionFactory;
    }
}
