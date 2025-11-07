package org.ejectfb.ejectcloud.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class PasswordService {
    
    @Value("${ejectcloud.password.salt:ejectcloud-default-salt}")
    private String globalSalt;
    
    private final SecureRandom random = new SecureRandom();
    
    public String generateSalt() {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }
    
    public String hashPassword(String password, String userSalt) {
        try {
            String combined = password + userSalt + globalSalt;
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(combined.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }
    
    public boolean verifyPassword(String password, String hash, String userSalt) {
        return hashPassword(password, userSalt).equals(hash);
    }
}