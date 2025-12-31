package game.engine.player.persistence;

import com.alibaba.druid.pool.DruidDataSource;
import game.engine.player.persistence.mapper.PlayerMapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MyBatisUtil {
    private static final Logger log = LoggerFactory.getLogger(MyBatisUtil.class);
    private static SqlSessionFactory sqlSessionFactory;

    public static synchronized void init() {
        if (sqlSessionFactory != null)
            return;

        try {
            DruidDataSource dataSource = new DruidDataSource();

            if ("true".equals(System.getProperty("test.env"))) {
                dataSource.setDriverClassName("org.h2.Driver");
                dataSource.setUrl("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=MySQL");
                dataSource.setUsername("sa");
                dataSource.setPassword("");
                // Create table for test
                // This is a bit hacky, usually done via migration script
            } else {
                dataSource.setDriverClassName("com.mysql.cj.jdbc.Driver");
                dataSource.setUrl(
                        "jdbc:mysql://localhost:3306/orion_game?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true");
                dataSource.setUsername("root");
                dataSource.setPassword("root");
            }

            dataSource.setInitialSize(1);
            dataSource.setMinIdle(1);
            dataSource.setMaxActive(10);

            TransactionFactory transactionFactory = new JdbcTransactionFactory();
            Environment environment = new Environment("development", transactionFactory, dataSource);
            Configuration configuration = new Configuration(environment);

            // Register Mappers
            configuration.addMapper(PlayerMapper.class);

            // Add other settings if needed
            configuration.setMapUnderscoreToCamelCase(true);

            sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);

            if ("true".equals(System.getProperty("test.env"))) {
                try (org.apache.ibatis.session.SqlSession session = sqlSessionFactory.openSession()) {
                    session.getConnection().createStatement().execute(
                            "CREATE TABLE IF NOT EXISTS player (" +
                                    "id BIGINT PRIMARY KEY AUTO_INCREMENT, " +
                                    "account_id BIGINT, " +
                                    "nickname VARCHAR(255), " +
                                    "lvl INT)");
                }
            }

            log.info("MyBatis initialized successfully.");
        } catch (Exception e) {
            log.error("Failed to initialize MyBatis", e);
            throw new RuntimeException(e);
        }
    }

    public static SqlSessionFactory getSqlSessionFactory() {
        if (sqlSessionFactory == null) {
            init();
        }
        return sqlSessionFactory;
    }
}
