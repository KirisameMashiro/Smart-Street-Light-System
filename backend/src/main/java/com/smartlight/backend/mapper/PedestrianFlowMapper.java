package com.smartlight.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlight.backend.entity.PedestrianFlow;
import com.smartlight.backend.entity.PedestrianFlowHourlyVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface PedestrianFlowMapper extends BaseMapper<PedestrianFlow> {

    /** 按路灯ID查最新一条人流量数据 */
    @Select("SELECT * FROM pedestrian_flow WHERE light_id = #{lightId} ORDER BY collect_time DESC LIMIT 1")
    PedestrianFlow selectLatestByLightId(@Param("lightId") Long lightId);

    /** 批量 UPSERT 小时聚合数据（实现在 XML 中） */
    void upsertHourlyBatch(@Param("list") List<PedestrianFlowHourlyVO> list);

    /** 从小时聚合表分页查询（动态 SQL 实现在 XML 中） */
    List<Map<String, Object>> selectFromHourlyPage(@Param("lightId") Long lightId,
                                                   @Param("startTime") String startTime,
                                                   @Param("endTime") String endTime);

    /** 从小时聚合表查询某路灯最新的一条记录 */
    @Select("SELECT light_id   AS lightId, "
            + "       hour_start AS collectTime, "
            + "       avg_flow   AS flowCount, "
            + "       max_flow   AS maxFlow, "
            + "       min_flow   AS minFlow, "
            + "       total_flow AS totalFlow, "
            + "       data_count AS dataCount "
            + "FROM pedestrian_flow_hourly "
            + "WHERE light_id = #{lightId} "
            + "ORDER BY hour_start DESC LIMIT 1")
    Map<String, Object> selectLatestFromHourly(@Param("lightId") Long lightId);
}
