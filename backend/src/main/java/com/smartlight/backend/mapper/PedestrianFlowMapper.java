package com.smartlight.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlight.backend.entity.PedestrianFlow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface PedestrianFlowMapper extends BaseMapper<PedestrianFlow> {

    /** 按路灯ID查最新一条人流量数据 */
    @Select("SELECT * FROM pedestrian_flow WHERE light_id = #{lightId} ORDER BY collect_time DESC LIMIT 1")
    PedestrianFlow selectLatestByLightId(@Param("lightId") Long lightId);

    /** 从小时聚合表分页查询 */
    @Select("<script>"
            + "SELECT light_id AS lightId, hour_start AS collectTime, "
            + "avg_flow AS flowCount, max_flow AS maxFlow, min_flow AS minFlow, "
            + "total_flow AS totalFlow, data_count AS dataCount "
            + "FROM pedestrian_flow_hourly "
            + "<where>"
            + "<if test='lightId != null'> AND light_id = #{lightId} </if>"
            + "<if test='startTime != null'> AND hour_start >= #{startTime} </if>"
            + "<if test='endTime != null'> AND hour_start <= #{endTime} </if>"
            + "</where>"
            + "ORDER BY hour_start DESC"
            + "</script>")
    List<Map<String, Object>> selectFromHourlyPage(@Param("lightId") Long lightId,
                                                   @Param("startTime") String startTime,
                                                   @Param("endTime") String endTime);
}