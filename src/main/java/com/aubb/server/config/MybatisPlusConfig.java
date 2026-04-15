package com.aubb.server.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import java.time.OffsetDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MybatisPlusConfig {

    @Bean
    MetaObjectHandler metaObjectHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                OffsetDateTime now = OffsetDateTime.now();
                strictInsertFill(metaObject, "createdAt", OffsetDateTime.class, now);
                setFieldValByName("updatedAt", now, metaObject);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                setFieldValByName("updatedAt", OffsetDateTime.now(), metaObject);
            }
        };
    }
}
