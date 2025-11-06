package org.ejectfb.ejectcloud.model;

import jakarta.xml.bind.annotation.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "user")
@XmlAccessorType(XmlAccessType.FIELD)
public class UserData {
    
    @XmlElement
    private String telegramId;
    
    @XmlElement
    private String username;
    
    @XmlElement
    private long quotaBytes;
    
    @XmlElement
    private String createdAt;
    
    @XmlElementWrapper(name = "shares")
    @XmlElement(name = "share")
    private List<ShareData> shares = new ArrayList<>();
    
    public UserData() {}
    
    public String getTelegramId() { return telegramId; }
    public void setTelegramId(String telegramId) { this.telegramId = telegramId; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public long getQuotaBytes() { return quotaBytes; }
    public void setQuotaBytes(long quotaBytes) { this.quotaBytes = quotaBytes; }
    
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    
    public List<ShareData> getShares() { return shares; }
    public void setShares(List<ShareData> shares) { this.shares = shares; }
}