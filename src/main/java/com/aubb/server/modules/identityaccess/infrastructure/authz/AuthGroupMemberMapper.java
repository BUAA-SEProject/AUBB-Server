package com.aubb.server.modules.identityaccess.infrastructure.authz;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthGroupMemberMapper extends BaseMapper<AuthGroupMemberEntity> {}
