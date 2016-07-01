package com.tcl.mig.appcenter.translation;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author: long.hua
 * @date: 2016-06-20 16:24
 * @since 1.0
 */
public class TranslationImporter {
    private final static Logger messLog = LoggerFactory.getLogger(TranslationImporter.class);

    private static int STEP = 10;
    private static int REPEAT = 3;
    private static String CONF_FILE_NAME = "range.conf";


    private static String importDataFile;
    private static String importDataLog;
    private static String importOffsetLog;
    private static String importNewlineFlag[];
    private static String importEndlineFlag[];

    private static String acDbUrl;
    private static String acDbUsername;
    private static String acDbPassword;
    private static String ucDbUrl;
    private static String ucDbUsername;
    private static String ucDbPassword;

    private static Connection acConn; // App翻译
    private static Connection ucConn; // 用户评论
    private static PreparedStatement idStmt;
    private static PreparedStatement acStmt;
    private static Map<String, PreparedStatement> ucStmtMap = new HashMap<>();


    // 读取配置文件
    private static boolean loadConfig() {
        Map<String, String> configMap = new HashMap<>();
        BufferedReader buffer = null;
        try {

            String fullPath = IOUtil.getFile(CONF_FILE_NAME);
            if (fullPath == null) {
                throw new Exception("获取配置文件路径错误！");
            }

            buffer = new BufferedReader(new InputStreamReader(new FileInputStream(fullPath)));
            String line;
            while ((line = buffer.readLine()) != null) {
                if (!line.trim().startsWith("#")) {
                    int idx = line.trim().indexOf("=");
                    if (idx > 0) {
                        configMap.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
                    }
                }
            }
        } catch (Exception e) {
            messLog.error("读取范围ID配置异常！", e);
        } finally {
            if (buffer != null) {
                try {
                    buffer.close();
                } catch (IOException e) {
                    messLog.error("关闭配置读取流异常！", e);
                }
            }
        }

        acDbUrl = configMap.get("ac.db.url");
        acDbUsername = configMap.get("ac.db.username");
        acDbPassword = configMap.get("ac.db.password");

        ucDbUrl = configMap.get("uc.db.url");
        ucDbUsername = configMap.get("uc.db.username");
        ucDbPassword = configMap.get("uc.db.password");

        importDataFile = configMap.get("import.data.file");
        importDataLog = configMap.get("import.data.log");
        importOffsetLog = configMap.get("import.offset.log");
        String nlflags = configMap.get("import.newline.flag");
        if (nlflags != null && nlflags.length() > 0) {
            importNewlineFlag = nlflags.split("\\|");
        }
        String elflags = configMap.get("import.endline.flag");
        if (elflags != null && elflags.length() > 0) {
            importEndlineFlag = elflags.split("\\|");
        }

        createConn(); // 打开连接

        return true;
    }


    // 加载提取成功的App
    private static void loadTranslations() {
        int aCount = 0;
        int cCount = 0;
        int offset = IOUtil.loadOffset(importOffsetLog); // 首次获取为-1

        BufferedReader reader = null;
        try {
            String fullPath = IOUtil.getFile(importDataFile);
            if (fullPath == null) {
                throw new RuntimeException("获取数据文件路径错误！");
            }

            String line;
            List<String> packageLanguages = new ArrayList<>();
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(fullPath)));
            for (int i = 0; (line = reader.readLine()) != null; i++) {
                if (i <= offset) {
                    continue;
                }
                offset = i;

                if (line.trim().startsWith("#")) {
                    continue;
                }

                String[] items = parseJson(line);
                if (items == null) {
                    messLog.error("Parse app info by json error, json: {}", line);
                    continue;
                }

                AppI18nInfo appI18nInfo = parseAppI18nInfo(items);
                if (appI18nInfo == null || appI18nInfo.getAppId() == -1) {
                    messLog.error("Parse app info by json error, package: {}, language: {}", items[0], items[1]);
                    continue;
                }

                aCount += updateAppTrans(appI18nInfo);
                cCount += insertComments(appI18nInfo);

                if (appI18nInfo.getTransed() == 0 || appI18nInfo.getTransed() == 1) {
                    packageLanguages.add(appI18nInfo.getPackageName() + ":" + appI18nInfo.getLanguage());
                }

                if (offset % 50 == 0) {
                    IOUtil.writeOffsetLog(offset, importOffsetLog);
                    messLog.info("截止现在，导入 {} 个App描述，导入 {} 个App评论，轮询了 {} 行", aCount, cCount, offset);
                    messLog.error("截止现在，导入 {} 个App描述，导入 {} 个App评论，轮询了 {} 行", aCount, cCount, offset);

                    IOUtil.writeDataLog(packageLanguages, importDataLog);
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

        IOUtil.writeOffsetLog(offset, importOffsetLog);
        messLog.info("截止现在，导入 {} 个App描述，导入 {} 个App评论，轮询了 {} 行", aCount, cCount, offset);
    }

    private static int updateAppTrans(AppI18nInfo appI18nInfo) {
        int row = 0;
        if (appI18nInfo == null) {
            return row;
        }

        // app翻译表已做分表处理
        int tabNo = appI18nInfo.getAppId() % 20;
        String sql = "SET NAMES utf8mb4;" +
                " INSERT INTO os_b_translation_app_" + tabNo + "(app_id, app_name, app_summary, description, `language`)" +
                " VALUES (?, ?, ?, ?, ?)" +
                " ON DUPLICATE KEY UPDATE app_name = ?, app_summary = ?, description = ?";
//        IOUtil.writeData("\r\n"+tabNo + " " + appI18nInfo.getDescription(), "package.data.txt"); // 检测代码

        try {
            if (acStmt == null) {
                acStmt = acConn.prepareStatement(sql);
            }

            acStmt.setInt(1, appI18nInfo.getAppId());
            acStmt.setString(2, appI18nInfo.getName());
            acStmt.setString(3, appI18nInfo.getSummary());
            acStmt.setString(4, appI18nInfo.getDescription());
            acStmt.setString(5, appI18nInfo.getLanguage());
            acStmt.setString(6, appI18nInfo.getName());
            acStmt.setString(7, appI18nInfo.getSummary());
            acStmt.setString(8, appI18nInfo.getDescription());

            acStmt.executeUpdate(); // 因增加set子句，执行后返回行数0
            row = 1;

        } catch (Exception e) {
            messLog.error("更新翻译时发生异常:", e);
            row = 0;
        }

        return row;
    }

    private static int insertComments(AppI18nInfo appI18nInfo) {
        int row = 0;

        if (appI18nInfo == null) {
            return row;
        }

        List<CommentInfo> commentInfos = appI18nInfo.getCommentInfoList();
        if (commentInfos == null || commentInfos.isEmpty()) {
            return row;
        }

        int count = 0;
        int tabNo = appI18nInfo.getAppId() % 20;
        String sql = "SELECT id FROM os_user_comment_" + tabNo + " WHERE app_id=? AND language=? LIMIT 1";

        try {
            PreparedStatement ucStmt = ucStmtMap.get(tabNo);
            if (ucStmt == null) {
                ucStmt = ucConn.prepareStatement(sql);
                ucStmtMap.put(tabNo + "_" + "SELECT", ucStmt);
            }

            ucStmt.setInt(1, appI18nInfo.getAppId());
            ucStmt.setString(2, appI18nInfo.getLanguage());
            ResultSet rs = ucStmt.executeQuery();
            if (rs.next()) {
                count = rs.getInt(1);
            }
        } catch (SQLException e) {
            messLog.error("添加评论时发生异常:", e);
        }

        // 如果没有相应app id和language的评论，则添加评论；否则不添加
        if (count > 0) {
            return row;
        }

        for (CommentInfo commentInfo : commentInfos) {
            row += insertComments(commentInfo);
        }

        return row;
    }

    private static int insertComments(CommentInfo commentInfo) {
        int tabNo = commentInfo.getAppId() % 20;
        // 在每次插入评论前需先指定names utf8mb4，否则可能会因为content字段中的二字节、三字节、四字节字符报异常
        String sql = "SET NAMES utf8mb4; INSERT INTO os_user_comment_" + tabNo +
                "(parent_id," +
                " app_id," +
                " app_package," +
                " device_model," +
                " os_version," +
                " score," +
                " content," +
                " comment_time," +
                " censor_status," +
                " `language`," +
                " critic_type)" +
                " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try {

            PreparedStatement ucStmt = ucStmtMap.get(tabNo);
            if (ucStmt == null) {
                ucStmt = ucConn.prepareStatement(sql);
                ucStmtMap.put(tabNo + "_" + "INSERT", ucStmt);
            }

            ucStmt.setInt(1, -1);//字段值为-1
            ucStmt.setInt(2, commentInfo.getAppId());//应用id
            ucStmt.setString(3, commentInfo.getPackageName());//App包名
            ucStmt.setString(4, "Google");//机型号
            ucStmt.setString(5, "5.4.5");//商店版本
            ucStmt.setInt(6, commentInfo.getStarRating().intValue());//App得分
            ucStmt.setString(7, cutContent(commentInfo.getContent()));//评论内容
            ucStmt.setTimestamp(8, new Timestamp(new Date().getTime()));//评论时间
            ucStmt.setInt(9, 1);//1审核通过
            ucStmt.setString(10, commentInfo.getLanguage());//语言
            ucStmt.setString(11, "AUTO");//评论者类型：机器

            ucStmt.executeUpdate(); // 因增加set子句，执行后返回行数0

            return 1;

        } catch (Exception e) {
            messLog.error("发生未知异常:", e);
        }

        return 0;
    }

    // 截短字符串，评论实体中content字段可能过长，需要截短
    private static String cutContent(String content) {
        if (content != null && content.length() >= 500) {
            return content.substring(0, 500);
        } else {
            return content;
        }
    }

    // 从数据库中查询需要验证的App
    private static int queryAppId(String packageName) {
        String sql = "SELECT id FROM ostore.os_app_entity WHERE app_package=?";
        try {

            if (idStmt == null) {
                idStmt = acConn.prepareStatement(sql);
            }

            idStmt.setString(1, packageName);
            ResultSet set = idStmt.executeQuery();
            while (set.next()) {
                return set.getInt("id");
            }

        } catch (Exception e) {
            messLog.error("发生未知异常:", e);
        }
        return -1;
    }

    private static void createConn() {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            acConn = DriverManager.getConnection(acDbUrl, acDbUsername, acDbPassword);
            ucConn = DriverManager.getConnection(ucDbUrl, ucDbUsername, ucDbPassword);
        } catch (Exception e) {
            messLog.error("创建数据库连接异常！", e);
        }
    }

    private static void closeConn() {
        if (idStmt != null) {
            try {
                idStmt.close();
            } catch (SQLException e) {
                messLog.error("关闭idStmt异常！", e);
            }
        }
        if (acStmt != null) {
            try {
                acStmt.close();
            } catch (SQLException e) {
                messLog.error("关闭acStmt异常！", e);
            }
        }
        if (acConn != null) {
            try {
                acConn.close();
            } catch (SQLException e) {
                messLog.error("关闭acConn异常！", e);
            }
        }

        if (!ucStmtMap.isEmpty()) {
            for (Map.Entry<String, PreparedStatement> entry : ucStmtMap.entrySet()) {
                if (entry.getValue() != null) {
                    try {
                        entry.getValue().close();
                    } catch (SQLException e) {
                        messLog.error("关闭ucStmt异常！", e);
                    }
                }
            }
        }
        if (ucConn != null) {
            try {
                ucConn.close();
            } catch (SQLException e) {
                messLog.error("关闭ucConn异常！", e);
            }
        }
    }

    private static AppI18nInfo parseAppI18nInfo(String[] items) {
        String packageName = items[0];
        String language = items[1];

        int id = queryAppId(packageName);
        if (id == -1) {
            messLog.error("query app id by package name error, app package:{}", packageName);
            return null;
        }

        // 转换App翻译
        AppI18nInfo appI18nInfo = JSON.parseObject(items[2], AppI18nInfo.class);
        appI18nInfo.setAppId(id);
        if (language.startsWith("zh")) {
            language = language.replace("-", "_");
        } else if (language.length() > 2) {
            language = language.substring(0, 2);
        }
        appI18nInfo.setLanguage(language);


        // 格式化描述
        appI18nInfo.setDescription(formatDesc(appI18nInfo.getDescription()));

        // 设置App摘要
        if (appI18nInfo.getDescription() != null && appI18nInfo.getDescription().length() > 128) {
            appI18nInfo.setSummary(appI18nInfo.getDescription().substring(0, 128));
        } else {
            appI18nInfo.setSummary(appI18nInfo.getDescription());
        }

        // 设置App评论
        List<CommentInfo> commentInfos = appI18nInfo.getCommentInfoList();
        if (commentInfos != null && commentInfos.size() > 0) {
            for (CommentInfo commentInfo : commentInfos) {
                commentInfo.setAppId(id);
                commentInfo.setPackageName(packageName);
                commentInfo.setLanguage(language);
                if (commentInfo.getStarRating() == null) {
                    commentInfo.setStarRating(0f);
                }
            }
        }

        return appI18nInfo;
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

    //格式化描述：无换行的在有特殊符号前增加换行；多个换行符变成一个换行
    private static String formatDesc(String description) {
        // 替换html符号
        description = replace2Html(description);

        // 替换多余换行，后面采集时增加了换行：可能有多个换行符
        if (description.contains("\n\n")) {
            return description.replaceAll("\n{2,}", "\n");

        } else if(description.contains("\n")){
            // 存在单个换行符则不格式化，后面采集时增加了换行：只能有一个换行符
            return description;
        }

        // 计算需要换行的符号
        List<String> newlineFlags = matchNewlineFlags(description);

        // 添加多个换行，前面采集未增加换行
        StringBuffer sb = new StringBuffer(description);
        for (String flag : newlineFlags) {
            addNewline(sb, flag);
        }

        return sb.toString();
    }

    private static String replace2Html(String description) {
        if (description.contains("&amp; # 8195;")) {
            description = description.replace("&amp; # 8195;", " ");
            description = description.replace("&amp; # 8226;", "•");
        }

        if (description.contains("＆＃8195;")) {
            description = description.replace("＆＃8195;", " ");
            description = description.replace("＆＃8226;", "•");
        }

        if (description.contains("& # 8195;")) {
            description = description.replace("& # 8195;", " ");
            description = description.replace("& # 8226;", "•");
        }
        return description;
    }

    private static void addNewline(StringBuffer description, String newlineFlag) {
        int idx = description.indexOf(newlineFlag, 0);
        while (idx > 0) {
            if (isEndlineFlag(description, idx)) {
                description.insert(idx, "\n");
            }
            // 因新行符号跟前1个新行符号相差距离大于10，再因\n在idx后一位，所以加STEP值
            idx = description.indexOf(newlineFlag, idx + newlineFlag.length() + STEP);
        }
    }

    // 判断前一个非空格字符是否为每行的结尾字符
    private static boolean isEndlineFlag(StringBuffer description, int idx) {
        char c1 = '0';
        char c2 = '0';

        while (true) {
            if (idx > 0) {
                c1 = description.charAt(--idx);
            }

            if (c1 != ' ' && c1 != '\t') {
                break;
            } else {
                c2 = c1; // 有些描述在新行符前面有空格没标点符号
            }
        }

        for (String flag : importEndlineFlag) {
            if (flag.equals(String.valueOf(c1)) || flag.equals(String.valueOf(c2))) {
                return true;
            }
        }

        return false;
    }

    // 匹配新行符
    private static List<String> matchNewlineFlags(String description){
        // 计算需要换行的符号
        List<String> newlineFlags = new ArrayList<>();
        for (String flag : importNewlineFlag) {
            if (ge3RepeatNewlineFlag(description, flag)) {
                newlineFlags.add(flag);
            }
        }

        return newlineFlags;
    }

    // 同一个符号重复三次以上
    private static boolean ge3RepeatNewlineFlag(String description, String flag) {
        int i = 0;

        int idx = description.indexOf(flag, 0);
        while (idx > 0) {
            i++;
            if (i >= REPEAT) {
                return true;
            }
            // 一个标志在描述中出现3次及以上且不连续才可能为新行的开始标志，如：五角星
            idx = description.indexOf(flag, idx + flag.length() + STEP);
        }

        return false;
    }

    public static void execute() {
        // 加载配置
        boolean flag = loadConfig();
        if (!flag) {
            messLog.error("加载配置异常，推出提取程序...");
            return;
        }

        long b = System.currentTimeMillis();
        loadTranslations();
        long e = System.currentTimeMillis();

        closeConn();  // 关闭连接
        messLog.info("导入结束，共耗时：{}秒", (e - b) / 1000);
    }


    public static void main(String[] args) throws Exception {
        messLog.info("开始提取App的翻译和评论....");
        execute();
        messLog.info("....结束提取App的翻译和评论");
    }

}
