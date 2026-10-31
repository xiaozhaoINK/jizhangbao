package com.jizhangbao.module.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jizhangbao.module.ai.entity.AiUsageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * AI 调用日志 Mapper。
 */
@Mapper
public interface AiUsageLogMapper extends BaseMapper<AiUsageLog> {

    @Select("SELECT scene, engine, COUNT(*) AS cnt FROM t_ai_usage_log GROUP BY scene, engine")
    List<Map<String, Object>> groupByScene();

    @Select("SELECT COUNT(*) FROM t_ai_usage_log WHERE created_at >= #{from}")
    long countSince(java.time.LocalDateTime from);
}
