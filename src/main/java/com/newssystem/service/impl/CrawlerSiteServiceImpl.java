package com.newssystem.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.newssystem.entity.CrawlerSite;
import com.newssystem.mapper.CrawlerSiteMapper;
import com.newssystem.service.CrawlerSiteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 爬取站点配置业务实现。
 * 新增站点时对名称与URL做严格校验（非空/长度/协议/重名/重复URL），
 * 防止管理员误填导致爬虫任务拿到非法种子。
 *
 * @author NewsSystem Dev
 */
@Service
public class CrawlerSiteServiceImpl implements CrawlerSiteService {

    private static final Logger log = LoggerFactory.getLogger(CrawlerSiteServiceImpl.class);

    /** 站点名称最大长度 */
    private static final int MAX_NAME_LEN = 50;
    /** 种子URL最大长度 */
    private static final int MAX_URL_LEN = 255;

    @Autowired
    private CrawlerSiteMapper crawlerSiteMapper;

    @Override
    public List<CrawlerSite> listAll() {
        List<CrawlerSite> list = crawlerSiteMapper.selectList(
                new QueryWrapper<CrawlerSite>().orderByAsc("id"));
        return list == null ? new ArrayList<>() : list;
    }

    @Override
    public List<CrawlerSite> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return listAll();
        }
        List<CrawlerSite> list = crawlerSiteMapper.selectList(
                new QueryWrapper<CrawlerSite>().in("id", ids).orderByAsc("id"));
        return list == null ? Collections.emptyList() : list;
    }

    @Override
    public CrawlerSite addSite(String name, String url) {
        // ---- 名称校验 ----
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("站点名称不能为空");
        }
        String finalName = name.trim();
        if (finalName.length() > MAX_NAME_LEN) {
            throw new IllegalArgumentException("站点名称不能超过" + MAX_NAME_LEN + "个字符");
        }
        Long nameCount = crawlerSiteMapper.selectCount(
                new QueryWrapper<CrawlerSite>().eq("name", finalName));
        if (nameCount != null && nameCount > 0) {
            throw new IllegalArgumentException("站点名称「" + finalName + "」已存在，请更换名称");
        }

        // ---- URL校验 ----
        String finalUrl = normalizeAndValidateUrl(url);
        Long urlCount = crawlerSiteMapper.selectCount(
                new QueryWrapper<CrawlerSite>().eq("url", finalUrl));
        if (urlCount != null && urlCount > 0) {
            throw new IllegalArgumentException("该网址已存在，请勿重复添加");
        }

        CrawlerSite site = new CrawlerSite();
        site.setName(finalName);
        site.setUrl(finalUrl);
        site.setIsBuiltin(0);
        site.setCreateTime(LocalDateTime.now());
        site.setUpdateTime(LocalDateTime.now());
        crawlerSiteMapper.insert(site);
        log.info("新增自定义爬取站点：id={}, name={}, url={}", site.getId(), finalName, finalUrl);
        return site;
    }

    @Override
    public boolean deleteSite(Long id) {
        if (id == null) {
            return false;
        }
        CrawlerSite site = crawlerSiteMapper.selectById(id);
        if (site == null) {
            return false;
        }
        int rows = crawlerSiteMapper.deleteById(id);
        log.info("删除爬取站点：id={}, name={}, rows={}", id, site.getName(), rows);
        return rows > 0;
    }

    /**
     * 规范化并校验种子URL：
     * 1) 去除首尾空白；2) 必须带http/https协议；3) 必须含有效主机名；
     * 4) 统一去掉URL末尾多余斜杠之外不做改写，保留管理员原始输入。
     */
    private String normalizeAndValidateUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("站点网址不能为空");
        }
        String u = rawUrl.trim();
        if (u.length() > MAX_URL_LEN) {
            throw new IllegalArgumentException("站点网址不能超过" + MAX_URL_LEN + "个字符");
        }
        // 未填写协议时自动补 https://（降低管理员使用门槛）
        if (!u.startsWith("http://") && !u.startsWith("https://")) {
            u = "https://" + u;
        }
        // 不允许URL中出现空白字符等明显非法形态
        if (u.contains(" ")) {
            throw new IllegalArgumentException("站点网址不能包含空格，示例：https://news.sina.com.cn/");
        }
        String host;
        try {
            host = URI.create(u).getHost();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("站点网址格式不合法，示例：https://news.sina.com.cn/");
        }
        if (host == null || host.trim().isEmpty() || !host.contains(".")) {
            throw new IllegalArgumentException("站点网址主机名不合法，示例：https://news.sina.com.cn/");
        }
        return u;
    }
}
