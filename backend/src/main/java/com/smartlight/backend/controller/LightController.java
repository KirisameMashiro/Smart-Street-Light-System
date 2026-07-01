package com.smartlight.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartlight.backend.common.Result;
import com.smartlight.backend.dto.LightBatchDTO;
import com.smartlight.backend.dto.LightQueryDTO;
import com.smartlight.backend.entity.Light;
import com.smartlight.backend.service.LightService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 路灯设备管理 API
 */
@RestController
@RequestMapping("/api/lights")
public class LightController {

    @Autowired
    private LightService lightService;

    /**
     * 分页查询路灯列表
     */
    @GetMapping("/page")
    public Result<IPage<Light>> getPage(LightQueryDTO queryDTO) {
        IPage<Light> page = lightService.getPage(
                queryDTO.getPageNum(),
                queryDTO.getPageSize(),
                queryDTO.getKeyword(),
                queryDTO.getStatus()
        );
        return Result.success(page);
    }

    /**
     * 获取所有路灯
     */
    @GetMapping
    public Result<List<Light>> getAll() {
        return Result.success(lightService.list());
    }

    /**
     * 根据ID获取路灯详情
     */
    @GetMapping("/{id}")
    public Result<Light> getById(@PathVariable Long id) {
        Light light = lightService.getById(id);
        if (light == null) {
            return Result.notFound("路灯不存在");
        }
        return Result.success(light);
    }

    /**
     * 新增路灯
     */
    @PostMapping
    public Result<Boolean> add(@RequestBody Light light) {
        return Result.success(lightService.save(light));
    }

    /**
     * 更新路灯
     */
    @PutMapping
    public Result<Boolean> update(@RequestBody Light light) {
        return Result.success(lightService.updateById(light));
    }

    /**
     * 删除路灯
     */
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(lightService.removeById(id));
    }

    /**
     * 批量开关灯
     */
    @PostMapping("/batch-switch")
    public Result<Boolean> batchSwitch(@RequestBody LightBatchDTO batchDTO) {
        return Result.success(lightService.batchSwitchStatus(batchDTO.getIds(), batchDTO.getStatus()));
    }

    /**
     * 设置路灯亮度
     */
    @PutMapping("/{id}/brightness")
    public Result<Boolean> setBrightness(@PathVariable Long id, @RequestParam Integer brightness) {
        if (brightness < 0 || brightness > 100) {
            return Result.error(400, "亮度值必须在 0-100 之间");
        }
        return Result.success(lightService.setBrightness(id, brightness));
    }

    /**
     * 统计各状态路灯数量
     */
    @GetMapping("/stats")
    public Result<?> getStats() {
        long online = lightService.countByStatus(1);
        long offline = lightService.countByStatus(0);
        long fault = lightService.countByStatus(2);
        return Result.success(java.util.Map.of(
                "online", online,
                "offline", offline,
                "fault", fault,
                "total", online + offline + fault
        ));
    }
}
