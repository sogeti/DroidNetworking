package com.sogeti.droidnetworking.external;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class MD5 {
    private static final int BIT_MASK = 0xFF;
    
    private MD5() {
    }

    public static String encodeString(final String s) {
        try {
            // Create MD5 Hash
            MessageDigest digest = MessageDigest.getInstance("MD5");
            digest.update(s.getBytes());
            byte[] messageDigest = digest.digest();

            // Create Hex String
            StringBuffer hexString = new StringBuffer();

            for (int i = 0; i < messageDigest.length; i++) {
            	hexString.append(Integer.toString((messageDigest[i] & BIT_MASK) + 0x100, 16).substring(1));
            }

            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
           return null;
        }
    }
}
