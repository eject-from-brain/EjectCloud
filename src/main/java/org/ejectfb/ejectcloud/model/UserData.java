package org.ejectfb.ejectcloud.model;

import jakarta.xml.bind.annotation.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "user")
@XmlAccessorType(XmlAccessType.FIELD)
public class UserData {
    
    @XmlElement
    private String id;
    
    @XmlElement
    private String email;
    
    @XmlElement
    private String displayName;
    
    @XmlElement
    private String telegramId;
    
    @XmlElement
    private String passwordHash;
    
    @XmlElement
    private String salt;
    
    @XmlElement
    private long quotaBytes;
    
    @XmlElement
    private String createdAt;
    
    @XmlElement
    private boolean isAdmin;
    
    @XmlElement
    private boolean mustChangePassword;
    
    @XmlElementWrapper(name = "shares")
    @XmlElement(name = "share")
    private List<ShareData> shares = new ArrayList<>();
    
    public UserData() {}
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    
    public String getTelegramId() { return telegramId; }
    public void setTelegramId(String telegramId) { this.telegramId = telegramId; }
    
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    
    public String getSalt() { return salt; }
    public void setSalt(String salt) { this.salt = salt; }
    
    public long getQuotaBytes() { return quotaBytes; }
    public void setQuotaBytes(long quotaBytes) { this.quotaBytes = quotaBytes; }
    
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    
    public boolean isAdmin() { return isAdmin; }
    public void setAdmin(boolean admin) { isAdmin = admin; }
    
    public boolean isMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(boolean mustChangePassword) { this.mustChangePassword = mustChangePassword; }
    
    public List<ShareData> getShares() { return shares; }
    public void setShares(List<ShareData> shares) { this.shares = shares; }
    
    public String getUsername() { return displayName; }
    public void setUsername(String username) { this.displayName = username; }
}