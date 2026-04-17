package com.aubb.server.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AuthzSchemaIntegrationTests extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void authzSchemaShouldExposeBuiltInTemplatesAndPermissions() {
        assertThat(jdbcTemplate.queryForObject("select count(*) from auth_permission_defs", Integer.class))
                .isGreaterThan(20);
        assertThat(jdbcTemplate.queryForObject(
                        "select count(*) from auth_group_templates where built_in = true", Integer.class))
                .isGreaterThanOrEqualTo(8);
    }
}
