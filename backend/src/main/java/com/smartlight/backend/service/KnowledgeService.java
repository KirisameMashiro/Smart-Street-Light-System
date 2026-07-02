package com.smartlight.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.smartlight.backend.entity.Knowledge;

public interface KnowledgeService extends IService<Knowledge> {
    IPage<Knowledge> getPage(int pageNum, int pageSize, String category);
}
