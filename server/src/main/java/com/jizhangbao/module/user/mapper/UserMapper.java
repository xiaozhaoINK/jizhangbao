package com.jizhangbao.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jizhangbao.module.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
