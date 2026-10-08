package com.sprint.mission.discodeit.storage.s3;

import com.sprint.mission.discodeit.config.S3Properties;
import com.sprint.mission.discodeit.dto.binarycontent.BinaryContentDto;
import com.sprint.mission.discodeit.entity.Role;
import com.sprint.mission.discodeit.entity.User;
import com.sprint.mission.discodeit.exception.storage.StorageException;
import com.sprint.mission.discodeit.repository.UserRepository;
import com.sprint.mission.discodeit.service.NotificationService;
import com.sprint.mission.discodeit.storage.BinaryContentStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;


@Component
@Slf4j
@ConditionalOnProperty(name = "discodeit.storage.type", havingValue = "s3")
@RequiredArgsConstructor
public class S3BinaryContentStorage implements BinaryContentStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties props;
    private final UserRepository userRepository;
    private final NotificationService  notificationService;

    @Override
    @Retryable(
            retryFor = StorageException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000)
    )
    public UUID put(UUID id, byte[] bytes) {
        try{
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(props.getBucket())
                            .key(id.toString())
                            .build(),
                    RequestBody.fromBytes(bytes)
            );
            log.info("S3 업로드 완료 - key: {}", id);
             return id;
        }catch (Exception e){
            log.warn("S3 업로드 시도 실패 - id: {}", id, e);
            throw StorageException.putFailed(id, e);
        }
    }

    @Recover
    public UUID recoverPut(StorageException exception, UUID id, byte[] bytes) {
        String requestId = MDC.get("requestId");

        Throwable cause = exception.getCause()!= null
                ? exception.getCause()
                  : exception;

        String content = """
                Operation: S3파일 업로드
                RequestId: %s
                BinaryContentId: %s
                Error: %s
                """.formatted(
                        requestId != null ? requestId : "없음",
                id,
                cause.getMessage()
        );

        log.error(
                "S3 업로드 최종 실패 - requestId: {}, binaryContentId: {}",
                requestId, id, exception
        );

        try {
            for (User admin : userRepository.findAllByRole(Role.ADMIN)) {
                try {
                    notificationService.create(
                            admin.getId(),
                            "S3 파일 업로드 실패",
                            content
                    );
                } catch (RuntimeException notificationException) {
                    log.error(
                            "관리자 실패 알림 저장 실패 - adminId: {}, binaryContentId: {}",
                            admin.getId(), id, notificationException
                    );
                }
            }
        } catch (RuntimeException lookupException) {
            log.error("관리자 조회 실패 - binaryContentId: {}", id, lookupException);
        }

        throw exception;
    }





    @Override
    public InputStream get(UUID id) {
        try{
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(props.getBucket())
                            .key(id.toString())
                            .build()
            );
            return response;
        }catch (Exception e){
            log.error("S3 조회 실패 - id: {}", id, e);
            throw StorageException.getFailed(id);
        }
    }


    @Override
    public ResponseEntity<Resource> download(BinaryContentDto binaryContentDto) {
        log.info("S3 다운로드 - id: {}, fileName: {}",
                binaryContentDto.id(), binaryContentDto.fileName());

        String presignedUrl = generatePresignedUrl(
                binaryContentDto.id().toString(),
                binaryContentDto.fileName());
        return ResponseEntity
                .status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, presignedUrl)
                .build();
    }


    private String generatePresignedUrl(String key, String fileName) {
        // 한글 파일명 URL 인코딩
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(props.getBucket())
                .key(key)
                .responseContentDisposition(
                        "attachment; filename=\"file\"; filename*=UTF-8''" + encodedFileName)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(props.getPresignedUrlExpiration()))
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignRequest);
        return presigned.url().toString();
    }
}
