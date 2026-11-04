package com.newssystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 新闻实体类（实体关系网络）
 * 对应表 news_entity
 *
 * 说明：以 url_hash 关联新闻，保证同一 URL 的实体记录唯一。
 *
 * @author NewsSystem Dev
 */
@TableName("news_entity")
public class NewsEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String urlHash;
    private String name;
    private String type;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUrlHash() { return urlHash; }
    public void setUrlHash(String urlHash) { this.urlHash = urlHash; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    @Override
    public String toString() {
        return "NewsEntity{name='" + name + "', type='" + type + "'}";
    }
}
