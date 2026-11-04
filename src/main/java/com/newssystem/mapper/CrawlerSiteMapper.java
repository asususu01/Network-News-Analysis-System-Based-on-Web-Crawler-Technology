package com.newssystem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.newssystem.entity.CrawlerSite;
import org.apache.ibatis.annotations.Mapper;

/**
 * 爬取站点配置 Mapper 接口
 *
 * @author NewsSystem Dev
 */
@Mapper
public interface CrawlerSiteMapper extends BaseMapper<CrawlerSite> {
}
