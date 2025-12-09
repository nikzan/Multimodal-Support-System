package com.nova.support.service;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Сервис для работы с MinIO (S3-совместимое хранилище)
 * Управляет загрузкой, скачиванием и удалением файлов
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    /**
     * Создаёт bucket при старте приложения, если его нет
     */
    @PostConstruct
    public void init() {
        try {
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucketName)
                            .build()
            );

            if (!bucketExists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucketName)
                                .build()
                );
                log.info("Created MinIO bucket: {}", bucketName);
            } else {
                log.info("MinIO bucket already exists: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("Failed to initialize MinIO bucket", e);
            throw new RuntimeException("Failed to initialize MinIO bucket", e);
        }
    }

    /**
     * Загружает файл в MinIO
     * 
     * @param file MultipartFile из HTTP запроса
     * @param folder папка внутри bucket (например, "audio" или "images")
     * @return путь к загруженному файлу в формате "folder/uuid-filename"
     */
    public String uploadFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        try {
            // Генерируем уникальное имя файла
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".") 
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : "";
            String fileName = folder + "/" + UUID.randomUUID() + extension;

            // Загружаем файл
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(fileName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );

            log.info("Uploaded file to MinIO: {}", fileName);
            return fileName;

        } catch (Exception e) {
            log.error("Failed to upload file to MinIO", e);
            throw new RuntimeException("Failed to upload file", e);
        }
    }

    /**
     * Загружает файл из byte array в MinIO
     * 
     * @param data данные файла
     * @param filename имя файла
     * @param contentType MIME тип
     * @return путь к загруженному файлу
     */
    public String uploadFile(byte[] data, String filename, String contentType) {
        try {
            String objectName = UUID.randomUUID().toString() + "-" + filename;
            
            ByteArrayInputStream stream = new ByteArrayInputStream(data);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .stream(stream, data.length, -1)
                            .contentType(contentType)
                            .build()
            );
            
            log.info("Uploaded byte array to MinIO: {}", objectName);
            return objectName;
        } catch (Exception e) {
            log.error("Failed to upload byte array to MinIO", e);
            throw new RuntimeException("Failed to upload byte array", e);
        }
    }

    /**
     * Получает временный URL для скачивания файла (действителен 24 часа)
     * 
     * @param objectName имя объекта в MinIO
     * @return presigned URL для скачивания
     */
    public String getPresignedUrl(String objectName) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(objectName)
                            .expiry(24, TimeUnit.HOURS)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to generate presigned URL for: {}", objectName, e);
            throw new RuntimeException("Failed to generate download URL", e);
        }
    }

    /**
     * Получает файл как InputStream (для обработки AI)
     * 
     * @param objectName имя объекта в MinIO
     * @return InputStream файла
     */
    public InputStream getFileAsStream(String objectName) {
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to get file from MinIO: {}", objectName, e);
            throw new RuntimeException("Failed to get file", e);
        }
    }

    /**
     * Удаляет файл из MinIO
     * 
     * @param objectName имя объекта для удаления
     */
    public void deleteFile(String objectName) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            log.info("Deleted file from MinIO: {}", objectName);
        } catch (Exception e) {
            log.error("Failed to delete file from MinIO: {}", objectName, e);
            throw new RuntimeException("Failed to delete file", e);
        }
    }

    /**
     * Проверяет существование файла
     * 
     * @param objectName имя объекта
     * @return true если файл существует
     */
    public boolean fileExists(String objectName) {
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build()
            );
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
