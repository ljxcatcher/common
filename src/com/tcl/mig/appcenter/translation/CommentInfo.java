package com.tcl.mig.appcenter.translation;

/**
 * 评论
 */
public class CommentInfo {

    private int appId;
    private String packageName;
    private String language;
    private String author;
    private String title;
    private String content;
    private String dateString;
    /** 评星等级 */
    private Float starRating;


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

    public String getAuthor() {
        return author;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Float getStarRating() {
        return starRating;
    }

    public void setStarRating(Float starRating) {
        this.starRating = starRating;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getDateString() {
        return dateString;
    }

    public void setDateString(String dateString) {
        this.dateString = dateString;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("CommentInfo{");
        sb.append("packageName='").append(packageName).append('\'');
        sb.append(", language='").append(language).append('\'');
        sb.append(", author='").append(author).append('\'');
        sb.append(", title='").append(title).append('\'');
        sb.append(", content='").append(content).append('\'');
        sb.append(", dateString='").append(dateString).append('\'');
        sb.append(", starRating=").append(starRating);
        sb.append('}');
        return sb.toString();
    }
}
