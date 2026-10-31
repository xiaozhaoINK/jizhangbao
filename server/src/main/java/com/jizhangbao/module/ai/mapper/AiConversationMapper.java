package com.jizhangbao.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jizhangbao.module.ai.entity.AiConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

/**
 * AI 会话 Mapper。
 */
@Mapper
public interface AiConversationMapper extends BaseMapper<AiConversation> {

    @Select("SELECT COUNT(*) AS total, COALESCE(SUM(1), 0) AS today FROM t_ai_conversation WHERE deleted = 0")
    Map<String, Object> statistics();
}
