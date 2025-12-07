package com.netfliz.encoder.utils;

import org.apache.logging.log4j.util.Strings;

public class CommonUtils {
    public static String getFileExtension(String fileName) {
        if (Strings.isBlank(fileName)) {
            return "";
        }

        return fileName.substring(fileName.lastIndexOf("."));
    }
}
