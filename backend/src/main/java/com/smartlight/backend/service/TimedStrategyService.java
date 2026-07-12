package com.smartlight.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.smartlight.backend.entity.TimedStrategy;

import java.util.List;

public interface TimedStrategyService {

    IPage<TimedStrategy> getPage(int pageNum, int pageSize, String type, String name);

    List<TimedStrategy> listAll();

    List<TimedStrategy> listEnabled();

    TimedStrategy getById(Long id);

    boolean save(TimedStrategy strategy);

    boolean update(TimedStrategy strategy);

    boolean delete(Long id);

    boolean toggleEnabled(Long id, boolean enabled);
}