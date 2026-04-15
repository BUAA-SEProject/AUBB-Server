package com.aubb.server.infrastructure.persistence;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.postgresql.util.PGobject;

@MappedJdbcTypes(JdbcType.OTHER)
public class PostgreSqlJsonbTypeHandler extends JacksonTypeHandler {

    public PostgreSqlJsonbTypeHandler(Class<?> type) {
        super(type);
    }

    public PostgreSqlJsonbTypeHandler(Class<?> type, Field field) {
        super(type, field);
    }

    @Override
    public void setNonNullParameter(PreparedStatement preparedStatement, int index, Object parameter, JdbcType jdbcType)
            throws SQLException {
        String json = toJson(parameter);
        String databaseProductName =
                preparedStatement.getConnection().getMetaData().getDatabaseProductName();
        if (databaseProductName != null && databaseProductName.toLowerCase().contains("postgres")) {
            PGobject jsonObject = new PGobject();
            jsonObject.setType("jsonb");
            jsonObject.setValue(json);
            preparedStatement.setObject(index, jsonObject);
            return;
        }
        preparedStatement.setObject(index, json, Types.VARCHAR);
    }
}
