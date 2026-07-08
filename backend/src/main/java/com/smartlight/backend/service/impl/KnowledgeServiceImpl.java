package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlight.backend.entity.Knowledge;
import com.smartlight.backend.mapper.KnowledgeMapper;
import com.smartlight.backend.service.KnowledgeService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeServiceImpl extends ServiceImpl<KnowledgeMapper, Knowledge> implements KnowledgeService {

    @Override
    public IPage<Knowledge> getPage(int pageNum, int pageSize, String category, String keyword) {
        Page<Knowledge> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Knowledge> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(category)) wrapper.eq(Knowledge::getCategory, category);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                .like(Knowledge::getTitle, keyword)
                .or()
                .like(Knowledge::getKeywords, keyword)
                .or()
                .like(Knowledge::getContent, keyword)
            );
        }
        wrapper.orderByDesc(Knowledge::getCreateTime);
        return this.page(page, wrapper);
    }
}
