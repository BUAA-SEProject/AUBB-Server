package com.aubb.server.modules.identityaccess.domain.authz;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PermissionCodeTests {

    @Test
    void fromCodeShouldResolveLegacyQuestionManageAliasToQuestionBankManage() {
        assertThat(PermissionCode.fromCode("question.manage")).isEqualTo(PermissionCode.QUESTION_BANK_MANAGE);
        assertThat(PermissionCode.fromCode("question_bank.manage")).isEqualTo(PermissionCode.QUESTION_BANK_MANAGE);
    }
}
