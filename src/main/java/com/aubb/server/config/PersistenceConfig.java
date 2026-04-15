package com.aubb.server.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan("com.aubb.server.infrastructure")
public class PersistenceConfig {}
