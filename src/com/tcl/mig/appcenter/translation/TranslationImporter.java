package com.tcl.mig.appcenter.translation;

import com.alibaba.fastjson.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
    private final static Logger statLog = LoggerFactory.getLogger("TranslationImporterStat");


    private static String CONF_FILE_NAME = "range.conf";
    private static String DATA_FILE_NAME = "package.data.txt";
    private static String DATA_FILE_NAME_LOG = "package.data.log";

    private static String acDbUrl;
    private static String acDbUsername;
    private static String acDbPassword;
    private static String ucDbUrl;
    private static String ucDbUsername;
    private static String ucDbPassword;
    private static Connection acConn; // App翻译
    private static PreparedStatement idStmt;
    private static PreparedStatement acStmt;
    private static Connection ucConn; // 用户评论
    private static Map<Integer, PreparedStatement> ucStmtMap = new HashMap<>();

    private static String getFile(String fileName) {
        URL url = TranslationImporter.class.getResource("/" + fileName);
        if (url != null) {
            return url.getFile();
        }
        return null;
    }

    // 读取配置文件
    private static boolean loadConfig() {
        Map<String, String> configMap = new HashMap<>();
        BufferedReader buffer = null;
        try {

            String fullPath = getFile(CONF_FILE_NAME);
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

        createConn(); // 打开连接

        return true;
    }

    // 加载所有待提取的App
    private static int loadOffset() {
        BufferedReader buffer = null;
        try {

            String fullPath = getFile(DATA_FILE_NAME_LOG);
            if (fullPath == null) {
                throw new RuntimeException("获取数据文件路径错误！");
            }

            buffer = new BufferedReader(new InputStreamReader(new FileInputStream(fullPath)));
            String line;
            while ((line = buffer.readLine()) != null) {
                if (!line.trim().startsWith("#")) {
                    return Integer.valueOf(line.trim());
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

        return 0;
    }

    private static int writeOffset(int offset) {
        PrintWriter writer = null;
        try {

            String fullPath = getFile(DATA_FILE_NAME_LOG);
            if (fullPath == null) {
                throw new RuntimeException("获取数据文件路径错误！");
            }

            writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(fullPath)));
            writer.println(offset);
        } catch (Exception e) {
            messLog.error("读取范围ID配置异常！", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception e) {
                    messLog.error("关闭配置读取流异常！", e);
                }
            }
        }

        return 0;
    }

    // 加载提取成功的App
    private static void loadTranslations() {
        int offset = loadOffset();

        BufferedReader reader = null;
        try {
            String fullPath = getFile(DATA_FILE_NAME);
            if (fullPath == null) {
                throw new RuntimeException("获取数据文件路径错误！");
            }

            int aCount = 0;
            int cCount = 0;
            String line;
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(fullPath)));
            for (int i = 0; (line = reader.readLine()) != null; i++) {
                if (i <= offset) {
                    continue;
                }

                if (!line.trim().startsWith("#")) {
                    AppI18nInfo appI18nInfo = parseAppI18nInfo(line);
                    if (appI18nInfo == null) {
                        messLog.error("Parse app info by json error, json: {}", line);
                        continue;
                    }

                    aCount += updateAppTrans(appI18nInfo);
                    cCount += insertComments(appI18nInfo);
                }

                offset = i;
                if (i % 50 == 0) {
                    writeOffset(i);
                    messLog.info("截止现在，导入 {} 个App描述，{}个App评论", aCount, cCount);
                }
            }
        } catch (Exception e) {
            messLog.error("读取范围ID配置异常！", e);
        }

        writeOffset(offset);
    }

    private static int updateAppTrans(AppI18nInfo appI18nInfo) {
        int row = 0;
        if (appI18nInfo == null) {
            return row;
        }

        // app翻译表已做分表处理
        int tabNo = appI18nInfo.getAppId() % 20;
        String sql = "INSERT INTO ostore_app_translation.os_b_translation_app_" + tabNo + "(app_id, app_name, app_summary, description, `language`)" +
                " VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE app_name = ?, app_summary = ?, description = ?";

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
            row = acStmt.executeUpdate();

        } catch (Exception e) {
            messLog.error("发生未知异常:", e);
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
                ucStmtMap.put(tabNo, ucStmt);
            }

            ucStmt.setInt(1, -1);//字段值为-1
            ucStmt.setInt(2, commentInfo.getAppId());//应用id
            ucStmt.setString(3, commentInfo.getPackageName());//App包名
            ucStmt.setString(4, "Google");//机型号
            ucStmt.setString(5, "5.4.5");//商店版本
            ucStmt.setInt(6, commentInfo.getStarRating().intValue());//App得分
            ucStmt.setString(7, truncateContent(commentInfo.getContent()));//评论内容
            ucStmt.setTimestamp(8, new Timestamp(new Date().getTime()));//评论时间
            ucStmt.setInt(9, 1);//1审核通过
            ucStmt.setString(10, commentInfo.getLanguage());//语言
            ucStmt.setString(11, "AUTO");//评论者类型：机器

            ucStmt.executeUpdate();

            return 1;

        } catch (Exception e) {
            messLog.error("发生未知异常:", e);
        }

        return 0;
    }

    // 截短字符串，评论实体中content字段可能过长，需要截短
    private static String truncateContent(String content) {
        if(content != null && !content.isEmpty()) {
            if(content.length() >= 500) {
                return content.substring(0,500);
            } else {
                return content;
            }
        }
        return content;
    }

    // 从数据库中查询需要验证的App
    private static int queryAppId(String packageName) {
        String sql = "SELECT id FROM os_app_entity WHERE app_package=?";
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
            for (Map.Entry<Integer, PreparedStatement> entry : ucStmtMap.entrySet()) {
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

    private static AppI18nInfo parseAppI18nInfo(String line) {
        String items[] = null;
        int idx = line.indexOf("=", 0);
        if (idx > 0) {
            items = line.substring(0, idx).split(":");
        }
        if (items == null || items.length == 0) {
            return null;
        }

        String packageName = items[0];
        String language = items[1];
        int id = queryAppId(packageName);
        AppI18nInfo appI18nInfo = JSON.parseObject(line.substring(idx+1), AppI18nInfo.class);
        appI18nInfo.setAppId(id);
        appI18nInfo.setLanguage(language);
        if (appI18nInfo.getDescription() != null && appI18nInfo.getDescription().length() > 128) {
            appI18nInfo.setSummary(appI18nInfo.getDescription().substring(0, 128));
        } else {
            appI18nInfo.setSummary(appI18nInfo.getDescription());
        }

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

    private static int toInt(Object obj, int defaults) {
        if (obj == null) {
            return defaults;
        }

        if (obj instanceof Integer) {
            return Integer.valueOf(obj.toString());
        } else if (obj.toString().matches("\\d+")) {
            return Integer.valueOf(obj.toString());
        }

        return defaults;
    }

    private static String toKey(String... items) {
        StringBuffer key = new StringBuffer();
        for (String item : items) {
            key.append(item).append(":");
        }
        if (items.length > 0) {
            key.deleteCharAt(key.length() - 1);
        }
        return key.toString();
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
