package com.aubb.server.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan(basePackages = "com.aubb.server.modules", markerInterface = BaseMapper.class)
public class PersistenceConfig {}
