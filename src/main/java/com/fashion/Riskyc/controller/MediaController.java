package com.fashion.Riskyc.controller;

import com.fashion.Riskyc.dto.response.DownloadUrlResponse;
import com.fashion.Riskyc.dto.response.MediaResponse;
import com.fashion.Riskyc.entity.ProductMedia;
import com.fashion.Riskyc.exception.ResourceNotFoundException;
import com.fashion.Riskyc.repository.ProductMediaRepository;
import com.fashion.Riskyc.service.MediaStream;
import com.fashion.Riskyc.service.ProductService;
import com.fashion.Riskyc.service.S3MediaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Serves product media by id. Two endpoints, two very different jobs:
 *
 * <ul>
 *   <li>{@code /download-url} hands back a presigned MinIO URL the client
 *       can fetch directly (this is what {@code ProductResponse} already
 *       embeds inline for catalog pages — this endpoint exists for the rare
 *       case a client only has a media id, e.g. a lazy-loaded viewer).</li>
 *   <li>{@code /content} proxies the bytes through this server instead,
 *       honoring {@code Range} requests so large videos can be scrubbed
 *       without downloading the whole file — useful when the client
 *       shouldn't talk to MinIO directly (no CORS on the bucket, etc).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {

    private final ProductMediaRepository productMediaRepository;
    private final S3MediaService s3MediaService;
    private final ProductService productService;

    @GetMapping("/{id}/download-url")
    public ResponseEntity<DownloadUrlResponse> downloadUrl(@PathVariable UUID id) {
        ProductMedia media = getOrThrow(id);
        String url = s3MediaService.getPresignedUrl(media.getStorageKey());
        DownloadUrlResponse body = new DownloadUrlResponse(url, Instant.now().plus(Duration.ofMinutes(55)));

        return ResponseEntity.ok()
                // The browser caches the presigned URL itself for 50 minutes,
                // so revisiting the page serves it instantly with zero
                // server round-trip at all.
                .cacheControl(CacheControl.maxAge(Duration.ofSeconds(3000)).cachePrivate())
                .body(body);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<StreamingResponseBody> content(
            @PathVariable UUID id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
    ) {
        ProductMedia media = getOrThrow(id);
        MediaStream mediaStream = s3MediaService.openStream(media.getStorageKey(), rangeHeader);

        StreamingResponseBody body = outputStream -> {
            try (var in = mediaStream.inputStream()) {
                in.transferTo(outputStream);
            }
        };

        HttpStatus status = mediaStream.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK;
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(mediaStream.contentLength()))
                .contentType(mediaStream.contentType() != null
                        ? MediaType.parseMediaType(mediaStream.contentType())
                        : MediaType.APPLICATION_OCTET_STREAM);

        if (mediaStream.partial()) {
            response.header(HttpHeaders.CONTENT_RANGE, "bytes %d-%d/%d"
                    .formatted(mediaStream.rangeStart(), mediaStream.rangeEnd(), mediaStream.totalSize()));
        }

        return response.body(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        productService.deleteMedia(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/promo-label")
    public MediaResponse updatePromoLabel(@PathVariable UUID id, @RequestBody Map<String, String> body) {
        return productService.updateMediaPromoLabel(id, body.get("text"));
    }

    private ProductMedia getOrThrow(UUID id) {
        return productMediaRepository.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Media", id));
    }
}
