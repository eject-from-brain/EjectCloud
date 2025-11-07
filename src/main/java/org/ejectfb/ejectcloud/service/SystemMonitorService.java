package org.ejectfb.ejectcloud.service;

import org.ejectfb.ejectcloud.model.SystemStats;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class SystemMonitorService {
    
    @Value("${ejectcloud.data-dir:./Data}")
    private String dataDir;
    
    private final String statsFile = "stats.xml";
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    
    @Scheduled(fixedRate = 5000) // каждые 5 секунд
    public void collectStats() {
        try {
            SystemStats stats = loadStats();
            SystemStats.StatsEntry entry = new SystemStats.StatsEntry();
            
            entry.setTimestamp(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            
            // Дисковое пространство
            FileStore store = Files.getFileStore(Paths.get(dataDir));
            entry.setTotalDiskSpace(store.getTotalSpace());
            entry.setFreeDiskSpace(store.getUsableSpace());
            
            // CPU и память
            double cpuLoad = osBean.getSystemLoadAverage();
            entry.setCpuUsage(cpuLoad >= 0 ? Math.min(cpuLoad * 100, 100) : 0);
            entry.setMemoryUsed(memoryBean.getHeapMemoryUsage().getUsed());
            entry.setMemoryTotal(memoryBean.getHeapMemoryUsage().getMax());
            
            // Простая имитация сетевой нагрузки (в реальности нужны более сложные метрики)
            entry.setNetworkBytesIn(0);
            entry.setNetworkBytesOut(0);
            
            stats.getEntries().add(entry);
            
            // Оставляем только записи за последние 30 дней
            LocalDateTime monthAgo = LocalDateTime.now().minusDays(30);
            stats.getEntries().removeIf(e -> {
                try {
                    return LocalDateTime.parse(e.getTimestamp(), DateTimeFormatter.ISO_LOCAL_DATE_TIME).isBefore(monthAgo);
                } catch (Exception ex) {
                    return true;
                }
            });
            
            saveStats(stats);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public SystemStats loadStats() {
        try {
            File file = new File(statsFile);
            if (!file.exists()) {
                return new SystemStats();
            }
            
            JAXBContext context = JAXBContext.newInstance(SystemStats.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            return (SystemStats) unmarshaller.unmarshal(file);
        } catch (Exception e) {
            return new SystemStats();
        }
    }
    
    private void saveStats(SystemStats stats) {
        try {
            JAXBContext context = JAXBContext.newInstance(SystemStats.class);
            Marshaller marshaller = context.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, true);
            marshaller.marshal(stats, new File(statsFile));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public SystemStats.StatsEntry getCurrentStats() {
        try {
            SystemStats.StatsEntry entry = new SystemStats.StatsEntry();
            
            FileStore store = Files.getFileStore(Paths.get(dataDir));
            entry.setTotalDiskSpace(store.getTotalSpace());
            entry.setFreeDiskSpace(store.getUsableSpace());
            double cpuLoad = osBean.getSystemLoadAverage();
            entry.setCpuUsage(cpuLoad >= 0 ? Math.min(cpuLoad * 100, 100) : 0);
            entry.setMemoryUsed(memoryBean.getHeapMemoryUsage().getUsed());
            entry.setMemoryTotal(memoryBean.getHeapMemoryUsage().getMax());
            
            return entry;
        } catch (Exception e) {
            return new SystemStats.StatsEntry();
        }
    }
    
    public SystemStats getFilteredStats(String period) {
        SystemStats allStats = loadStats();
        SystemStats filtered = new SystemStats();
        
        LocalDateTime cutoff = LocalDateTime.now();
        switch (period) {
            case "5m": cutoff = cutoff.minusMinutes(5); break;
            case "30m": cutoff = cutoff.minusMinutes(30); break;
            case "1h": cutoff = cutoff.minusHours(1); break;
            case "3h": cutoff = cutoff.minusHours(3); break;
            case "6h": cutoff = cutoff.minusHours(6); break;
            case "12h": cutoff = cutoff.minusHours(12); break;
            case "24h": cutoff = cutoff.minusHours(24); break;
            case "3d": cutoff = cutoff.minusDays(3); break;
            case "10d": cutoff = cutoff.minusDays(10); break;
            case "30d": cutoff = cutoff.minusDays(30); break;
        }
        
        for (SystemStats.StatsEntry entry : allStats.getEntries()) {
            try {
                LocalDateTime entryTime = LocalDateTime.parse(entry.getTimestamp(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                if (entryTime.isAfter(cutoff)) {
                    filtered.getEntries().add(entry);
                }
            } catch (Exception e) {
                // игнорируем некорректные записи
            }
        }
        
        return filtered;
    }
}