package com.tcl.mig.appcenter.translation;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 *
 *
 * @author: long.hua
 * @date: 2016-07-05 15:42
 * @since 1.0
 */
public class TranslationConverter {
    private final static Logger messLog = LoggerFactory.getLogger(TranslationConverter.class);

    private static String dataFile = "convert.data.txt";
    private static String offsetFile = "convert.offset.txt";


    // 加载提取成功的App
    private static void loadTranslations(String importDataFile) {
        int okCount = 0;
        int offset = IOUtil.loadOffsetLog(offsetFile); // 首次获取为-1
        List<String> descriptionList = new ArrayList<>();

        BufferedReader reader = null;
        try {
            String fullPath = IOUtil.getFile(importDataFile);
            if (fullPath == null) {
                throw new RuntimeException("获取数据文件路径错误！");
            }

            String line = null;
            String nextLine = null; // 下一个包名的第一条App信息
            String packageName = null;
            boolean isOtherPackageName = false;

            reader = new BufferedReader(new InputStreamReader(new FileInputStream(fullPath)));
            for (int i = 0; (line = reader.readLine()) != null; i++) {
                if (line.trim().startsWith("#")) {
                    continue;
                }

                String[] items = parseJson(line);
                if (items == null) {
                    messLog.error("Parse app info by json error, json: {}", line);
                    continue;
                }

                if (i <= offset) {
                    continue;
                }
                offset = i;


                // 保存上次轮询到的下一个包名的App信息
                if(nextLine != null){
                    descriptionList.add(nextLine);
                    nextLine = null; // 注：防重复保存
                }


                // 判断和提取同一个包名的翻译列表
                if(packageName == null){
                    // 首次启动时
                    packageName = items[0];
                    isOtherPackageName = false;
                    descriptionList.add(line);
                } else {

                    if(packageName.equals(items[0])){
                        isOtherPackageName = false;
                        descriptionList.add(line);
                    } else {
                        isOtherPackageName = true;
                        packageName = items[0];
                        nextLine = line;
                    }
                }

                // 替换同一个包名的翻译列表中的重复翻译
                if(isOtherPackageName){
                    // 记录offset
                    offset = offset - 1;// 因为要运行到下一行才知道之前的包名是否为同一个
                    IOUtil.writeOffsetLog(offset, offsetFile);

                    // 记录被替换重复翻译的app描述
                    List<String> jsonList = replace(descriptionList);
                    okCount += jsonList.size();
                    IOUtil.writeData(jsonList, dataFile);
                    descriptionList.clear(); // 注：需要清空
                    messLog.error("截止现在，替换了：{} 个App描述，循环offset：{} 行", okCount, offset);
                }
            }

        } catch (Exception e) {
            messLog.error("读取翻译文件异常！", e);
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                messLog.error("读取翻译文件流关闭异常！", e);
            }
        }


        // 记录offset
        IOUtil.writeOffsetLog(offset, offsetFile);

        // 记录被替换重复翻译的app描述
        List<String> jsonList = replace(descriptionList);
        okCount += jsonList.size();
        IOUtil.writeData(jsonList, dataFile);
        descriptionList.clear(); // 注：需要清空
        messLog.error("截止现在，替换了：{} 个App描述，循环offset：{} 行", okCount, offset);
    }


    // 同一个包名的翻译出现了两种语言，需要所有翻译自己相互替换
    private static List<String> replace(List<String> jsonList) {
        List<AppI18nInfo> appI18nInfos = new ArrayList<>();
        for (String json : jsonList) {
           String [] items = parseJson(json);
            AppI18nInfo appI18nInfo = JSON.parseObject(items[2], AppI18nInfo.class); // 转换App翻译
            if ("en".equals(appI18nInfo.getLanguage().toLowerCase())) {
                appI18nInfos.add(0, appI18nInfo); // 优先替换英语翻译
            } else if ("fr".equals(appI18nInfo.getLanguage().toLowerCase())) {
                appI18nInfos.add(0, appI18nInfo);
            } else if ("es".equals(appI18nInfo.getLanguage().toLowerCase())) {
                appI18nInfos.add(0, appI18nInfo);
            } else if ("pt".equals(appI18nInfo.getLanguage().toLowerCase())) {
                appI18nInfos.add(0, appI18nInfo);
            } else if ("lt".equals(appI18nInfo.getLanguage().toLowerCase())) {
                appI18nInfos.add(0, appI18nInfo);
            } else if ("de".equals(appI18nInfo.getLanguage().toLowerCase())) {
                appI18nInfos.add(0, appI18nInfo);
            } else {
                appI18nInfos.add(appI18nInfo);
            }
        }

        // 同一个App中所有语言可能为默认语言，优先为英语。所以要相互替换一遍
        List<AppI18nInfo> newAppI18nInfos = null;
        for (AppI18nInfo appI18nInfo : appI18nInfos) {
            newAppI18nInfos = replace(appI18nInfos, appI18nInfo.getDescription());
            if (newAppI18nInfos != null && newAppI18nInfos.size() > 0) {
                messLog.info("PackageName: {}, 默认语言: {}, 共替换了: {} 个翻译", appI18nInfo.getPackageName(), appI18nInfo.getLanguage(), newAppI18nInfos.size());
                break; // 某个App中，所有语言中只有一种默认语言
            }
        }

        // 重新格式化为json
        jsonList.clear();
        if(newAppI18nInfos != null && newAppI18nInfos.size() > 0){
            for (AppI18nInfo newAppI18nInfo : newAppI18nInfos) {
                String json = JSON.toJSONString(newAppI18nInfo);
                String key = newAppI18nInfo.getPackageName() + ":" + newAppI18nInfo.getLanguage();
                jsonList.add(key + "=" + json); // 重新格式化为json
            }
        }

        return jsonList;
    }


    private static List<AppI18nInfo> replace(List<AppI18nInfo> appI18nInfos, String description) {
        List<AppI18nInfo> newAppI18nInfos = new ArrayList<>();
        for (AppI18nInfo appI18nInfo : appI18nInfos) {
            if (appI18nInfo.getDescription().equals(description)) {
                continue; // 自己不能替换自己；翻译完全相同不能替换
            }

            if (appI18nInfo.getDescription().contains(description)) {
                String desc = appI18nInfo.getDescription().replace(description, "");
                appI18nInfo.setDescription(desc);
                appI18nInfo.setTransed(1); // 当前描述是翻译过来的
                appI18nInfo.setCommentInfoList(null); // 不用保存评论，减少数据体积
                appI18nInfo.setLanguages(null);// 不用保存语言列表，减少数据体积

                newAppI18nInfos.add(appI18nInfo);
            }
        }

        return newAppI18nInfos;
    }

    private static String[] parseJson(String json) {
        String items[] = null;

        int idx = json.indexOf("=", 0);
        if (idx > 0) {

            items = new String[3];
            String[] keyItems = json.substring(0, idx).split(":");
            items[0] = keyItems[0];
            items[1] = keyItems[1];
            items[2] = json.substring(idx + 1);
        }

        return items;
    }

    public static void main(String[] args) {
        messLog.error("开始替换...");
        if (args == null || args.length < 1) {
            messLog.error("输入数据文件路径错误");
        } else{
            loadTranslations(args[0]);
        }
        messLog.error("替换结束...");
    }
}
