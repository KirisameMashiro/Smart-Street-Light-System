package com.smartlight.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartlight.backend.entity.Road;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoadMapper extends BaseMapper<Road> {

    @Select("SELECT r.*, d.district_name as districtName " +
            "FROM road r LEFT JOIN district d ON r.district_id = d.id " +
            "WHERE r.district_id = #{districtId} " +
            "ORDER BY r.sort_order ASC, r.id ASC")
    List<Road> selectByDistrictId(@Param("districtId") Long districtId);
}
