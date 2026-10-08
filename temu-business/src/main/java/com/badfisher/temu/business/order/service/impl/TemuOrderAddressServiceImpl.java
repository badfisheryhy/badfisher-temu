package com.badfisher.temu.business.order.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.badfisher.temu.business.order.mapper.TemuOrderAddressMapper;
import com.badfisher.temu.business.order.model.TemuOrderAddress;
import com.badfisher.temu.business.order.service.TemuOrderAddressService;
import org.springframework.stereotype.Service;

@Service
public class TemuOrderAddressServiceImpl extends ServiceImpl<TemuOrderAddressMapper, TemuOrderAddress>
		implements TemuOrderAddressService {

}
