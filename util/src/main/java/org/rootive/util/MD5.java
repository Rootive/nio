package org.rootive.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MD5 {
    static private MessageDigest md5;
    static {
        try {
            md5 = MessageDigest.getInstance("md5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }
    static private String convertBytes2HexStr(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (byte bt : data) {
            String hex = Integer.toHexString(((int) bt) & 0xff);
            if (hex.length() == 1) {
                hex = "0" + hex;
            }
            sb.append(hex);
        }
        return sb.toString();
    }
    static public String digest(byte[] data) {
        return convertBytes2HexStr(md5.digest(data));
    }
}
