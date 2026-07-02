package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlight.backend.entity.AlertRule;
import com.smartlight.backend.mapper.AlertRuleMapper;
import com.smartlight.backend.service.AlertRuleService;
import org.springframework.stereotype.Service;

@Service
public class AlertRuleServiceImpl extends ServiceImpl<AlertRuleMapper, AlertRule> implements AlertRuleService {
}
