package com.smartlight.backend.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartlight.backend.common.Result;
import com.smartlight.backend.entity.Knowledge;
import com.smartlight.backend.service.KnowledgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai/assistant/knowledge")
public class KnowledgeController {

    @Autowired
    private KnowledgeService knowledgeService;

    @GetMapping
    public Result<IPage<Knowledge>> getPage(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String category) {
        return Result.success(knowledgeService.getPage(pageNum, pageSize, category));
    }

    @PostMapping
    public Result<Boolean> add(@RequestBody Knowledge knowledge) {
        return Result.success(knowledgeService.save(knowledge));
    }

    @PutMapping
    public Result<Boolean> update(@RequestBody Knowledge knowledge) {
        return Result.success(knowledgeService.updateById(knowledge));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.success(knowledgeService.removeById(id));
    }
}
