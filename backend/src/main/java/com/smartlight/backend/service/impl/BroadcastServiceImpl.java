package com.smartlight.backend.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartlight.backend.entity.Broadcast;
import com.smartlight.backend.mapper.BroadcastMapper;
import com.smartlight.backend.service.BroadcastService;
import org.springframework.stereotype.Service;

@Service
public class BroadcastServiceImpl extends ServiceImpl<BroadcastMapper, Broadcast> implements BroadcastService {
}