package com.tcl.mig.appcenter.translation;

import java.util.List;

/**
 * App多语言翻译
 */
public class AppI18nInfo {

    private int appId; // 包名
    private String packageName; // 包名
    private String language; // 语言
    private String name;// 名称
    private String summary; // 描述
    private String description; // 描述
    private int transed = -1; // -1未分辨，1翻译，0原始
    private String originIconUrl; // 图标
    private String originScreenshots; // 截图

    private List<CommentInfo> commentInfoList; // 网页中第一页的评论列表

    private List<String> languages; // 语言


    public int getAppId() {
        return appId;
    }

    public void setAppId(int appId) {
        this.appId = appId;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getTransed() {
        return transed;
    }

    public void setTransed(int transed) {
        this.transed = transed;
    }

    public String getOriginIconUrl() {
        return originIconUrl;
    }

    public void setOriginIconUrl(String originIconUrl) {
        this.originIconUrl = originIconUrl;
    }

    public String getOriginScreenshots() {
        return originScreenshots;
    }

    public void setOriginScreenshots(String originScreenshots) {
        this.originScreenshots = originScreenshots;
    }

    public List<CommentInfo> getCommentInfoList() {
        return commentInfoList;
    }

    public void setCommentInfoList(List<CommentInfo> commentInfoList) {
        this.commentInfoList = commentInfoList;
    }

    public List<String> getLanguages() {
        return languages;
    }

    public void setLanguages(List<String> languages) {
        this.languages = languages;
    }

    @Override
    public String toString() {
        return "I18NInfo{" +
                "name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", originIconUrl='" + originIconUrl + '\'' +
                ", originScreenshots='" + originScreenshots + '\'' +
                ", commentInfoList=" + commentInfoList +
                '}';
    }
}
