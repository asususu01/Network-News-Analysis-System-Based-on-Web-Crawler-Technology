package com.newssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.newssystem.entity.UserLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户行为日志 Mapper 接口
 *
 * @author NewsSystem Dev
 */
@Mapper
public interface UserLogMapper extends BaseMapper<UserLog> {
}
