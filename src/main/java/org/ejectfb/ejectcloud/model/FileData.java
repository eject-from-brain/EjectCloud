package org.ejectfb.ejectcloud.model;

import jakarta.xml.bind.annotation.*;
import java.time.Instant;

@XmlAccessorType(XmlAccessType.FIELD)
public class FileData {
    
    @XmlElement
    private String id;
    
    @XmlElement
    private String filename;
    
    @XmlElement
    private long sizeBytes;
    
    @XmlElement
    private String uploadedAt;
    
    @XmlElement
    private boolean shared;
    
    @XmlElement
    private String shareExpiresAt;
    
    @XmlElement
    private String telegramId;
    
    public FileData() {}
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
    
    public long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(long sizeBytes) { this.sizeBytes = sizeBytes; }
    
    public String getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(String uploadedAt) { this.uploadedAt = uploadedAt; }
    
    public boolean isShared() { return shared; }
    public void setShared(boolean shared) { this.shared = shared; }
    
    public String getShareExpiresAt() { return shareExpiresAt; }
    public void setShareExpiresAt(String shareExpiresAt) { this.shareExpiresAt = shareExpiresAt; }
    
    public String getTelegramId() { return telegramId; }
    public void setTelegramId(String telegramId) { this.telegramId = telegramId; }
}