package org.ejectfb.ejectcloud.model;

import jakarta.xml.bind.annotation.*;
import java.time.Instant;

@XmlAccessorType(XmlAccessType.FIELD)
public class ShareData {
    
    @XmlElement
    private String shareId;
    
    @XmlElement
    private String fileId;
    
    @XmlElement
    private String createdAt;
    
    @XmlElement
    private String expiresAt;
    
    public ShareData() {}
    
    public String getShareId() { return shareId; }
    public void setShareId(String shareId) { this.shareId = shareId; }
    
    public String getFileId() { return fileId; }
    public void setFileId(String fileId) { this.fileId = fileId; }
    
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
}