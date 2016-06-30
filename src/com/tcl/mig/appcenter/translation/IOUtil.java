package com.tcl.mig.appcenter.translation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URL;
import java.util.List;

/**
 * @author: long.hua
 * @date: 2016-06-30 20:29
 * @since 1.0
 */
public class IOUtil {
    private final static Logger messLog = LoggerFactory.getLogger(TranslationImporter.class);


    public static String getFile(String fileName) {
        if (fileName.startsWith("/") || fileName.matches("^[a-zA-Z]:.*")) {
            return fileName;
        }

        URL url = TranslationImporter.class.getResource("/" + fileName);
        if (url != null) {
            return url.getFile();
        }

        return null;
    }

    // 加载所有待提取的App
    public static int loadOffset(String importOffsetLog) {
        BufferedReader buffer = null;
        try {

            String fullPath = getFile(importOffsetLog);
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

        return -1;
    }

    // 写数据
    public static int writeData(String data, String importDataLog) {
        PrintWriter writer = null;
        try {
            String fullPath = getFile("") + importDataLog;
            writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(fullPath)), true);
            writer.println(data);
        } catch (Exception e) {
            messLog.error("写入包名和语言异常！", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception e) {
                    messLog.error("关闭写入包名和语言流异常！", e);
                }
            }
        }

        return 0;
    }

    public static int writeDataLog(List<String> packageLanguages, String importDataLog) {
        PrintWriter writer = null;
        try {
            String fullPath = getFile("") + importDataLog;
            writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(fullPath)), true);
            for (String pl : packageLanguages) {
                writer.println(pl);
            }
        } catch (Exception e) {
            messLog.error("写入包名和语言异常！", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception e) {
                    messLog.error("关闭写入包名和语言流异常！", e);
                }
            }
        }

        return 0;
    }

    public static int writeOffsetLog(int offset, String importOffsetLog) {
        PrintWriter writer = null;
        try {
            String fullPath = getFile("") + importOffsetLog;
            writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(fullPath)));
            writer.println(offset);
        } catch (Exception e) {
            messLog.error("写入offset异常！", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Exception e) {
                    messLog.error("关闭写入offset流异常！", e);
                }
            }
        }

        return 0;
    }

}
