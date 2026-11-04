package com.newssystem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 新闻实体类
 * 对应表 news
 *
 * @author NewsSystem Dev
 */
@TableName("news")
public class News implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String url;
    private String urlHash;
    private String source;
    /** 新闻分类：财经/体育/科技/娱乐/军事/教育/健康/汽车/国际/社会，爬虫入库时自动判定 */
    private String category;
    private LocalDateTime publishTime;
    private String content;
    private Integer sentiment;
    /** 情感强度(-100~100)：词典加权 + 否定词翻转 + 程度副词修饰后的连续值 */
    private Integer sentimentScore;
    /** 热度得分(0-100)：爬虫入库时按页面位置/标题特征/正文质量计算，用于热度排序 */
    private Integer hotScore;
    /** SimHash 64位指纹（事件聚类用） */
    private Long simHash;
    /** 指纹第1段（bit0-15），分桶召回用 */
    private Integer simSeg0;
    /** 指纹第2段（bit16-31） */
    private Integer simSeg1;
    /** 指纹第3段（bit32-47） */
    private Integer simSeg2;
    /** 指纹第4段（bit48-63） */
    private Integer simSeg3;
    /** 事件簇ID（同一事件的报道共享该ID） */
    private Long clusterId;
    /** TextRank 抽取式摘要 */
    private String summary;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getUrlHash() { return urlHash; }
    public void setUrlHash(String urlHash) { this.urlHash = urlHash; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public LocalDateTime getPublishTime() { return publishTime; }
    public void setPublishTime(LocalDateTime publishTime) { this.publishTime = publishTime; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Integer getSentiment() { return sentiment; }
    public void setSentiment(Integer sentiment) { this.sentiment = sentiment; }
    public Integer getSentimentScore() { return sentimentScore; }
    public void setSentimentScore(Integer sentimentScore) { this.sentimentScore = sentimentScore; }
    public Integer getHotScore() { return hotScore; }
    public void setHotScore(Integer hotScore) { this.hotScore = hotScore; }
    public Long getSimHash() { return simHash; }
    public void setSimHash(Long simHash) { this.simHash = simHash; }
    public Integer getSimSeg0() { return simSeg0; }
    public void setSimSeg0(Integer simSeg0) { this.simSeg0 = simSeg0; }
    public Integer getSimSeg1() { return simSeg1; }
    public void setSimSeg1(Integer simSeg1) { this.simSeg1 = simSeg1; }
    public Integer getSimSeg2() { return simSeg2; }
    public void setSimSeg2(Integer simSeg2) { this.simSeg2 = simSeg2; }
    public Integer getSimSeg3() { return simSeg3; }
    public void setSimSeg3(Integer simSeg3) { this.simSeg3 = simSeg3; }
    public Long getClusterId() { return clusterId; }
    public void setClusterId(Long clusterId) { this.clusterId = clusterId; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    @Override
    public String toString() {
        return "News{id=" + id + ", title='" + title + "', source='" + source + "'}";
    }
}
