package game.engine.player.persistence.mapper;

import game.engine.core.persistence.mybatis.DeltaSqlProvider;
import game.engine.player.entity.Player;
import org.apache.ibatis.annotations.*;

public interface PlayerMapper {

    @Select("SELECT * FROM player WHERE id = #{id}")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "accountId", column = "account_id"),
            @Result(property = "nickname", column = "nickname"),
            @Result(property = "level", column = "lvl")
    })
    Player selectById(long id);

    @Select("SELECT * FROM player WHERE account_id = #{accountId}")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "accountId", column = "account_id"),
            @Result(property = "nickname", column = "nickname"),
            @Result(property = "level", column = "lvl")
    })
    Player selectByAccountId(long accountId);

    @InsertProvider(type = DeltaSqlProvider.class, method = "insert")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void insert(Player player);

    @UpdateProvider(type = DeltaSqlProvider.class, method = "updateDelta")
    void update(Player player);
}
