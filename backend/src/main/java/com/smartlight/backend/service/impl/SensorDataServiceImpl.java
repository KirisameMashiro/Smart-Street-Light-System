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
        
        avg.setIlluminance(list.stream()
                .filter(d -> d.getIlluminance() != null)
                .mapToDouble(SensorData::getIlluminance).average().orElse(0));
        avg.setPower(list.stream()
                .filter(d -> d.getPower() != null)
                .mapToDouble(SensorData::getPower).average().orElse(0));
        avg.setVoltage(list.stream()
                .filter(d -> d.getVoltage() != null)
                .mapToDouble(SensorData::getVoltage).average().orElse(0));
        avg.setCurrent(list.stream()
                .filter(d -> d.getCurrent() != null)
                .mapToDouble(SensorData::getCurrent).average().orElse(0));
        avg.setTemperature(list.stream()
                .filter(d -> d.getTemperature() != null)
                .mapToDouble(SensorData::getTemperature).average().orElse(0));
        avg.setHumidity(list.stream()
                .filter(d -> d.getHumidity() != null)
                .mapToDouble(SensorData::getHumidity).average().orElse(0));
        avg.setSamplingEnergy(list.stream()
                .filter(d -> d.getSamplingEnergy() != null)
                .mapToDouble(SensorData::getSamplingEnergy).average().orElse(0));
        return avg;
    }
}