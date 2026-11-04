package com.newssystem.service;

import com.newssystem.entity.CrawlerSite;

import java.util.List;

/**
 * 爬取站点配置业务接口。
 * 提供站点列表查询、按ID批量查询、新增（含校验）、删除能力。
 *
 * @author NewsSystem Dev
 */
public interface CrawlerSiteService {

    /**
     * 查询全部爬取站点（按ID升序，下拉列表与启动爬虫均使用）
     */
    List<CrawlerSite> listAll();

    /**
     * 按ID列表查询站点（启动爬虫时根据页面选中项加载真实URL）。
     *
     * @param ids 站点ID列表，null或空时返回全部站点
     */
    List<CrawlerSite> listByIds(List<Long> ids);

    /**
     * 新增自定义爬取站点。
     *
     * @param name 站点名称（必填，1~50字，不可与已有站点重名）
     * @param url  种子网址（必填，必须为http/https合法URL，不可与已有URL重复）
     * @return 新增成功的站点（含生成的ID）
     * @throws IllegalArgumentException 名称或URL不合法/重复时抛出，消息可直接展示给管理员
     */
    CrawlerSite addSite(String name, String url);

    /**
     * 删除指定站点配置。
     *
     * @param id 站点ID
     * @return true=删除成功；false=站点不存在
     */
    boolean deleteSite(Long id);
}
