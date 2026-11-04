package com.newssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.newssystem.entity.Admin;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理员 Mapper 接口
 *
 * @author NewsSystem Dev
 */
@Mapper
public interface AdminMapper extends BaseMapper<Admin> {

}
