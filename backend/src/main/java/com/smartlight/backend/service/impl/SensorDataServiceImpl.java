package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlight.backend.entity.SensorData;
import com.smartlight.backend.mapper.SensorDataMapper;
import com.smartlight.backend.service.SensorDataService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SensorDataServiceImpl extends ServiceImpl<SensorDataMapper, SensorData> implements SensorDataService {

    @Override
    public IPage<SensorData> getPage(int pageNum, int pageSize, Long lightId,
                                     LocalDateTime startTime, LocalDateTime endTime) {
        Page<SensorData> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<SensorData> wrapper = new LambdaQueryWrapper<>();

        if (lightId != null) {
            wrapper.eq(SensorData::getLightId, lightId);
        }
        if (startTime != null) {
            wrapper.ge(SensorData::getCollectTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(SensorData::getCollectTime, endTime);
        }

        wrapper.orderByDesc(SensorData::getCollectTime);
        return this.page(page, wrapper);
    }

    @Override
    public SensorData getLatestByLightId(Long lightId) {
        LambdaQueryWrapper<SensorData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SensorData::getLightId, lightId)
               .orderByDesc(SensorData::getCollectTime)
               .last("LIMIT 1");
        return this.getOne(wrapper);
    }

    @Override
    public SensorData getAverageData(Long lightId, LocalDateTime startTime, LocalDateTime endTime) {
        // 获取时间范围内的所有数据用于计算平均值
        LambdaQueryWrapper<SensorData> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SensorData::getLightId, lightId)
               .ge(SensorData::getCollectTime, startTime)
               .le(SensorData::getCollectTime, endTime);
        List<SensorData> list = this.list(wrapper);

        if (list.isEmpty()) {
            return null;
        }

        SensorData avg = new SensorData();
        avg.setLightId(lightId);
        avg.setIlluminance(list.stream().mapToDouble(SensorData::getIlluminance).average().orElse(0));
        avg.setPower(list.stream().mapToDouble(SensorData::getPower).average().orElse(0));
        avg.setVoltage(list.stream().mapToDouble(SensorData::getVoltage).average().orElse(0));
        avg.setCurrent(list.stream().mapToDouble(SensorData::getCurrent).average().orElse(0));
        avg.setTemperature(list.stream().mapToDouble(SensorData::getTemperature).average().orElse(0));
        avg.setHumidity(list.stream().mapToDouble(SensorData::getHumidity).average().orElse(0));
        avg.setTotalEnergy(list.stream()
                .filter(d -> d.getTotalEnergy() != null)
                .mapToDouble(SensorData::getTotalEnergy).average().orElse(0));
        return avg;
    }
}