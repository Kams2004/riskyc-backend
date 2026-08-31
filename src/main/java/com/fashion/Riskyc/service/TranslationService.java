package com.fashion.Riskyc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Machine-translates admin-authored catalog text (product/category names,
 * product descriptions) into French via the Google Cloud Translation API,
 * so the storefront can show French content without an admin ever typing
 * it twice. Called synchronously from ProductService/CategoryService on
 * create/update, only when the source text actually changed.
 */
@Service
@Slf4j
public class TranslationService {

    private final RestClient restClient = RestClient.create();

    @Value("${app.google.translate-api-key:}")
    private String apiKey;

    /**
     * Translates {@code text} into French, auto-detecting the source
     * language (a no-op if it's already French). Returns null — never
     * throws — when no key is configured, the text is blank, or the call
     * fails for any reason; callers fall back to the original text in that
     * case rather than showing a blank field.
     */
    @SuppressWarnings("unchecked")
    public String translateToFrench(String text) {
        if (text == null || text.isBlank() || apiKey == null || apiKey.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> requestBody = Map.of(
                    "q", text,
                    "target", "fr",
                    "format", "text"
            );
            Map<String, Object> response = restClient.post()
                    .uri("https://translation.googleapis.com/language/translate/v2?key={key}", apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);
            Map<String, Object> data = (Map<String, Object>) response.get("data");
            List<Map<String, Object>> translations = (List<Map<String, Object>>) data.get("translations");
            return (String) translations.get(0).get("translatedText");
        } catch (RestClientException | NullPointerException | ClassCastException | IndexOutOfBoundsException e) {
            log.warn("Google Translate call failed — caller will fall back to the untranslated text: {}", e.getMessage());
            return null;
        }
    }
}
