package org.ejectfb.ejectcloud.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.Instant;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ArchiveService {
    private static final long MAX_JOB_AGE_MS = 2L * 60L * 60L * 1000L;
    private static final long DONE_JOB_TTL_MS = 15L * 60L * 1000L;
    private static final long MAX_ARCHIVE_BYTES = 10L * 1024L * 1024L * 1024L; // 10GB
    private static final long MIN_FREE_BYTES = 20L * 1024L * 1024L * 1024L; // 20GB

    private static final Logger log = LoggerFactory.getLogger(ArchiveService.class);

    private final FileStorageService storageService;
    private final ExecutorService executor;
    private final ConcurrentHashMap<String, Job> jobs = new ConcurrentHashMap<>();

    public ArchiveService(FileStorageService storageService) {
        this.storageService = storageService;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "archive-job");
            t.setDaemon(true);
            return t;
        });
    }

    public String startJob(String userId, String folderPath, String fileName) {
        cleanupOld();
        String jobId = UUID.randomUUID().toString();
        Job job = new Job(jobId, userId, folderPath == null ? "" : folderPath, fileName);
        jobs.put(jobId, job);
        log.info("[archive] job start jobId={} userId={} pathEnc='{}' fileNameEnc='{}'",
            jobId,
            userId,
            safe(job.folderPath),
            safe(fileName));
        executor.submit(() -> runJob(job));
        return jobId;
    }

    private static String safe(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }

    public Map<String, Object> getStatus(String userId, String jobId) {
        cleanupOld();
        Job job = jobs.get(jobId);
        if (job == null || !job.userId.equals(userId)) {
            throw new IllegalStateException("Задача не найдена");
        }
        return Map.of(
            "state", job.state,
            "percent", job.percent,
            "message", job.message == null ? "" : job.message,
            "fileName", job.fileName == null ? "" : job.fileName
        );
    }

    public Path getZipPathForDownload(String userId, String jobId) {
        cleanupOld();
        Job job = jobs.get(jobId);
        if (job == null || !job.userId.equals(userId)) {
            throw new IllegalStateException("Задача не найдена");
        }
        if (!"done".equals(job.state) || job.zipPath == null) {
            throw new IllegalStateException("Архив еще не готов");
        }
        return job.zipPath;
    }

    public String getFileName(String userId, String jobId) {
        Job job = jobs.get(jobId);
        if (job == null || !job.userId.equals(userId)) {
            return "archive.zip";
        }
        return (job.fileName == null || job.fileName.isBlank()) ? "archive.zip" : job.fileName;
    }

    public void deleteJob(String userId, String jobId) {
        Job job = jobs.get(jobId);
        if (job == null || !job.userId.equals(userId)) {
            return;
        }
        jobs.remove(jobId);
        if (job.zipPath != null) {
            try {
                Files.deleteIfExists(job.zipPath);
            } catch (IOException ignored) {
            }
        }
    }

    private void runJob(Job job) {
        job.state = "running";
        job.percent = 0;

        Path dataDir = storageService.getDataDir(job.userId).toAbsolutePath().normalize();
        Path source = job.folderPath.isBlank() ? dataDir : dataDir.resolve(job.folderPath).normalize();
        if (!source.startsWith(dataDir)) {
            job.state = "error";
            job.message = "Некорректный путь";
            job.percent = 0;
            log.warn("[archive] invalid path jobId={} dataDir={} source={} raw='{}'", job.jobId, dataDir, source, job.folderPath);
            return;
        }
        if (!Files.exists(source) || !Files.isDirectory(source)) {
            job.state = "error";
            job.message = "Папка не найдена";
            job.percent = 0;
            return;
        }

        Path relBase = job.folderPath.isBlank() ? dataDir : source.getParent();
        if (relBase == null) relBase = dataDir;
        final Path relBaseFinal = relBase;

        try {
            AtomicLong totalBytes = new AtomicLong(0);
            try (var walk = Files.walk(source)) {
                walk.filter(Files::isRegularFile).forEach(p -> {
                    try {
                        totalBytes.addAndGet(Files.size(p));
                    } catch (IOException ignored) {
                    }
                });
            }

            long total = totalBytes.get();
            if (total <= 0) total = 1;
            final long totalFinal = total;

            if (totalFinal > MAX_ARCHIVE_BYTES) {
                job.state = "error";
                job.message = "Архив больше 10GB - нельзя создать";
                job.percent = 0;
                log.warn("[archive] job rejected: archive too large jobId={} bytes={} path='{}'", job.jobId, totalFinal, job.folderPath);
                return;
            }

            Path archivesDir = storageService.getArchivesDir(job.userId).toAbsolutePath().normalize();
            try {
                Files.createDirectories(archivesDir);
            } catch (IOException e) {
                job.state = "error";
                job.message = "Не удалось создать папку для архивов";
                job.percent = 0;
                log.error("[archive] failed to create archivesDir jobId={} dir={} msg={}", job.jobId, archivesDir, e.getMessage());
                return;
            }
            long free;
            try {
                free = Files.getFileStore(archivesDir).getUsableSpace();
            } catch (IOException e) {
                free = -1;
            }

            if (free >= 0 && free < MIN_FREE_BYTES) {
                job.state = "error";
                job.message = "Недостаточно места на диске (нужно минимум 20GB свободно)";
                job.percent = 0;
                log.warn("[archive] job rejected: not enough free space jobId={} freeBytes={} minBytes={} path='{}'", job.jobId, free, MIN_FREE_BYTES, job.folderPath);
                return;
            }

            // Safety: ensure there is room for temp zip roughly equal to total size
            if (free >= 0 && free < (totalFinal + (2L * 1024L * 1024L * 1024L))) {
                job.state = "error";
                job.message = "Недостаточно места на диске для архива";
                job.percent = 0;
                log.warn("[archive] job rejected: insufficient space for archive jobId={} freeBytes={} folderBytes={} path='{}'", job.jobId, free, totalFinal, job.folderPath);
                return;
            }

            Path tmp = archivesDir.resolve("job-" + job.jobId + ".zip");
            job.zipPath = tmp;
            log.info("[archive] job building jobId={} source={} out={} folderBytes={}", job.jobId, source, tmp, totalFinal);

            try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(tmp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)))) {
                byte[] buffer = new byte[64 * 1024];
                AtomicLong doneBytes = new AtomicLong(0);

                try (var walk = Files.walk(source)) {
                    walk.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString)).forEach(file -> {
                        if (!"running".equals(job.state)) {
                            return;
                        }
                        String entryName;
                        try {
                            entryName = relBaseFinal.relativize(file).toString().replace("\\", "/");
                        } catch (Exception e) {
                            return;
                        }
                        job.message = entryName;
                        try {
                            ZipEntry entry = new ZipEntry(entryName);
                            entry.setTime(Files.getLastModifiedTime(file).toMillis());
                            zos.putNextEntry(entry);

                            try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
                                int read;
                                while ((read = in.read(buffer)) != -1) {
                                    zos.write(buffer, 0, read);
                                    long d = doneBytes.addAndGet(read);
                                    int pct = (int) Math.min(99, Math.max(0, (d * 100) / totalFinal));
                                    job.percent = pct;
                                }
                            }

                            zos.closeEntry();
                        } catch (IOException ignored) {
                        }
                    });
                }
            }

            job.percent = 100;
            job.state = "done";
            job.message = "Готово";
            job.completedAtMs = System.currentTimeMillis();
            log.info("[archive] job done jobId={} out={} bytes={}", job.jobId, job.zipPath, totalFinal);
        } catch (Exception e) {
            job.state = "error";
            job.message = e.getMessage() == null ? "Ошибка архивации" : e.getMessage();
            job.percent = 0;
            log.error("[archive] job error jobId={} path='{}'", job.jobId, job.folderPath, e);
            if (job.zipPath != null) {
                try {
                    Files.deleteIfExists(job.zipPath);
                } catch (IOException ignored) {
                }
                job.zipPath = null;
            }
        }
    }

    private void cleanupOld() {
        long cutoff = System.currentTimeMillis() - MAX_JOB_AGE_MS;
        jobs.values().removeIf(job -> {
            long ttl = cutoff;
            if ("done".equals(job.state) && job.completedAtMs > 0) {
                ttl = System.currentTimeMillis() - DONE_JOB_TTL_MS;
                if (job.completedAtMs >= ttl) {
                    return false;
                }
            }

            if (job.createdAtMs < cutoff || ("done".equals(job.state) && job.completedAtMs > 0 && job.completedAtMs < (System.currentTimeMillis() - DONE_JOB_TTL_MS))) {
                if (job.zipPath != null) {
                    try {
                        Files.deleteIfExists(job.zipPath);
                    } catch (IOException ignored) {
                    }
                }
                return true;
            }
            return false;
        });
    }

    private static class Job {
        final String jobId;
        final String userId;
        final String folderPath;
        final String fileName;
        final long createdAtMs;
        volatile long completedAtMs = 0;

        volatile String state = "queued";
        volatile int percent = 0;
        volatile String message = "";

        volatile Path zipPath;

        Job(String jobId, String userId, String folderPath, String fileName) {
            this.jobId = jobId;
            this.userId = userId;
            this.folderPath = folderPath;
            this.fileName = fileName;
            this.createdAtMs = System.currentTimeMillis();
        }
    }
}
