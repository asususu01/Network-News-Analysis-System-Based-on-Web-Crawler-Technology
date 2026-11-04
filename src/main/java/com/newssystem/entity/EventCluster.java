package com.newssystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 事件簇实体类
 * 对应表 event_cluster
 *
 * @author NewsSystem Dev
 */
@TableName("event_cluster")
public class EventCluster implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String keywords;
    private Integer newsCount;
    private Integer sourceCount;
    private Integer sentimentAvg;
    private LocalDateTime firstTime;
    private LocalDateTime lastTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getKeywords() { return keywords; }
    public void setKeywords(String keywords) { this.keywords = keywords; }
    public Integer getNewsCount() { return newsCount; }
    public void setNewsCount(Integer newsCount) { this.newsCount = newsCount; }
    public Integer getSourceCount() { return sourceCount; }
    public void setSourceCount(Integer sourceCount) { this.sourceCount = sourceCount; }
    public Integer getSentimentAvg() { return sentimentAvg; }
    public void setSentimentAvg(Integer sentimentAvg) { this.sentimentAvg = sentimentAvg; }
    public LocalDateTime getFirstTime() { return firstTime; }
    public void setFirstTime(LocalDateTime firstTime) { this.firstTime = firstTime; }
    public LocalDateTime getLastTime() { return lastTime; }
    public void setLastTime(LocalDateTime lastTime) { this.lastTime = lastTime; }

    @Override
    public String toString() {
        return "EventCluster{id=" + id + ", title='" + title + "', newsCount=" + newsCount + "}";
    }
}
