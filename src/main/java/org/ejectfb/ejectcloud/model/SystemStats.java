package org.ejectfb.ejectcloud.model;

import jakarta.xml.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@XmlRootElement(name = "systemStats")
@XmlAccessorType(XmlAccessType.FIELD)
public class SystemStats {
    
    @XmlElementWrapper(name = "entries")
    @XmlElement(name = "entry")
    private List<StatsEntry> entries = new ArrayList<>();
    
    public SystemStats() {}
    
    public List<StatsEntry> getEntries() { return entries; }
    public void setEntries(List<StatsEntry> entries) { this.entries = entries; }
    
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class StatsEntry {
        @XmlElement
        private String timestamp;
        @XmlElement
        private long totalDiskSpace;
        @XmlElement
        private long freeDiskSpace;
        @XmlElement
        private double cpuUsage;
        @XmlElement
        private long memoryUsed;
        @XmlElement
        private long memoryTotal;
        @XmlElement
        private long networkBytesIn;
        @XmlElement
        private long networkBytesOut;
        
        public StatsEntry() {}
        
        public String getTimestamp() { return timestamp; }
        public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
        
        public long getTotalDiskSpace() { return totalDiskSpace; }
        public void setTotalDiskSpace(long totalDiskSpace) { this.totalDiskSpace = totalDiskSpace; }
        
        public long getFreeDiskSpace() { return freeDiskSpace; }
        public void setFreeDiskSpace(long freeDiskSpace) { this.freeDiskSpace = freeDiskSpace; }
        
        public double getCpuUsage() { return cpuUsage; }
        public void setCpuUsage(double cpuUsage) { this.cpuUsage = cpuUsage; }
        
        public long getMemoryUsed() { return memoryUsed; }
        public void setMemoryUsed(long memoryUsed) { this.memoryUsed = memoryUsed; }
        
        public long getMemoryTotal() { return memoryTotal; }
        public void setMemoryTotal(long memoryTotal) { this.memoryTotal = memoryTotal; }
        
        public long getNetworkBytesIn() { return networkBytesIn; }
        public void setNetworkBytesIn(long networkBytesIn) { this.networkBytesIn = networkBytesIn; }
        
        public long getNetworkBytesOut() { return networkBytesOut; }
        public void setNetworkBytesOut(long networkBytesOut) { this.networkBytesOut = networkBytesOut; }
    }
}