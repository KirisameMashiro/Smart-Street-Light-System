package com.smartlight.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartlight.backend.common.Result;
import com.smartlight.backend.entity.DeviceType;
import com.smartlight.backend.entity.District;
import com.smartlight.backend.entity.Road;
import com.smartlight.backend.entity.SystemConfig;
import com.smartlight.backend.service.DeviceTypeService;
import com.smartlight.backend.service.DistrictService;
import com.smartlight.backend.service.RoadService;
import com.smartlight.backend.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/system")
public class SystemConfigController {

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private DistrictService districtService;

    @Autowired
    private RoadService roadService;

    @Autowired
    private DeviceTypeService deviceTypeService;

    @GetMapping("/config")
    public Result<List<SystemConfig>> getConfig() {
        return Result.success(systemConfigService.list());
    }

    @PutMapping("/config")
    public Result<Boolean> updateConfig(@RequestBody SystemConfig config) {
        return Result.success(systemConfigService.updateById(config));
    }

    // ========== 行政区管理 ==========

    @GetMapping("/districts")
    public Result<List<District>> listDistricts() {
        LambdaQueryWrapper<District> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(District::getSortOrder).orderByAsc(District::getId);
        return Result.success(districtService.list(wrapper));
    }

    @PostMapping("/districts")
    public Result<Boolean> addDistrict(@RequestBody District district) {
        return Result.success(districtService.save(district));
    }

    @PutMapping("/districts")
    public Result<Boolean> updateDistrict(@RequestBody District district) {
        return Result.success(districtService.updateById(district));
    }

    @DeleteMapping("/districts/{id}")
    public Result<Boolean> deleteDistrict(@PathVariable Long id) {
        return Result.success(districtService.removeById(id));
    }

    // ========== 路段管理 ==========

    @GetMapping("/roads")
    public Result<List<Road>> listRoads(@RequestParam(required = false) Long districtId) {
        if (districtId != null) {
            return Result.success(roadService.listByDistrictId(districtId));
        }
        LambdaQueryWrapper<Road> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Road::getDistrictId).orderByAsc(Road::getSortOrder).orderByAsc(Road::getId);
        List<Road> list = roadService.list(wrapper);
        // 填充行政区名称
        fillDistrictName(list);
        return Result.success(list);
    }

    private void fillDistrictName(List<Road> list) {
        if (list == null || list.isEmpty()) return;
        List<District> districts = districtService.list();
        for (Road road : list) {
            if (road.getDistrictId() != null) {
                districts.stream()
                        .filter(d -> d.getId().equals(road.getDistrictId()))
                        .findFirst()
                        .ifPresent(d -> road.setDistrictName(d.getDistrictName()));
            }
        }
    }

    @PostMapping("/roads")
    public Result<Boolean> addRoad(@RequestBody Road road) {
        return Result.success(roadService.save(road));
    }

    @PutMapping("/roads")
    public Result<Boolean> updateRoad(@RequestBody Road road) {
        return Result.success(roadService.updateById(road));
    }

    @DeleteMapping("/roads/{id}")
    public Result<Boolean> deleteRoad(@PathVariable Long id) {
        return Result.success(roadService.removeById(id));
    }

    // ========== 设备类型管理 ==========

    @GetMapping("/device-types")
    public Result<List<DeviceType>> listDeviceTypes() {
        LambdaQueryWrapper<DeviceType> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(DeviceType::getTypeCode).orderByAsc(DeviceType::getId);
        return Result.success(deviceTypeService.list(wrapper));
    }

    @PostMapping("/device-types")
    public Result<Boolean> addDeviceType(@RequestBody DeviceType deviceType) {
        return Result.success(deviceTypeService.save(deviceType));
    }

    @PutMapping("/device-types")
    public Result<Boolean> updateDeviceType(@RequestBody DeviceType deviceType) {
        return Result.success(deviceTypeService.updateById(deviceType));
    }

    @DeleteMapping("/device-types/{id}")
    public Result<Boolean> deleteDeviceType(@PathVariable Long id) {
        return Result.success(deviceTypeService.removeById(id));
    }
}
