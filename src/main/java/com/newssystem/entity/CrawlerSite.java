package com.newssystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 爬取站点配置实体类（管理端"爬虫管理"中可选择/添加/删除的爬取网站）。
 * 对应表 crawler_site。
 *
 * @author NewsSystem Dev
 */
@TableName("crawler_site")
public class CrawlerSite implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 站点名称（如：新浪新闻），全站唯一 */
    private String name;
    /** 种子网址（如：https://news.sina.com.cn/），全站唯一 */
    private String url;
    /** 是否内置站点：1=系统内置（来自application.yml），0=管理员自定义 */
    private Integer isBuiltin;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public Integer getIsBuiltin() { return isBuiltin; }
    public void setIsBuiltin(Integer isBuiltin) { this.isBuiltin = isBuiltin; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
