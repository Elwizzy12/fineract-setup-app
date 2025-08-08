package com.example.fineractsetup.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Collections;

/**
 * Service for communicating with the Fineract API
 */
@Service
public class FineractApiService {
    private static final Logger logger = LoggerFactory.getLogger(FineractApiService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${fineract.api.url}")
    private String fineractUrl;

    @Value("${fineract.api.tenant}")
    private String tenantId;

    @Value("${fineract.api.username}")
    private String username;

    @Value("${fineract.api.password}")
    private String password;
    @Value("${keycloak.url}")
    private String keycloakUrl;

    @Value("${keycloak.grant-type}")
    private String grantType;

    @Value("${keycloak.client-id}")
    private String clientId;
    
    @Value("${keycloak.client-secret}")
    private String clientSecret;

    @Value("${fineract.api.locale:en}")
    private String locale;

    @Value("${fineract.api.dateFormat:dd MMMM yyyy}")
    private String dateFormat;

    @Value("${retry.max-attempts:3}")
    private int maxRetryAttempts;

    @Value("${retry.initial-interval:1000}")
    private long initialRetryInterval;

    @Value("${retry.multiplier:2.0}")
    private double retryMultiplier;

    @Value("${retry.max-interval:10000}")
    private long maxRetryInterval;

    private String accessToken;

    public FineractApiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Authenticates with Keycloak and retrieves an access token
     *
     * @return true if authentication is successful, false otherwise
     */
    private boolean authenticate() {
        logger.info("Authenticating with Keycloak...");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", grantType);
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("username", username);
        body.add("password", password);

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    keycloakUrl, HttpMethod.POST, requestEntity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode responseBody = objectMapper.readTree(response.getBody());
                this.accessToken = responseBody.get("access_token").asText();
                logger.info("Access Token: {}", this.accessToken);
                logger.info("Successfully authenticated with Keycloak");
                return true;
            } else {
                logger.error("Failed to authenticate with Keycloak. Status: {}", response.getStatusCode());
                return false;
            }
        } catch (IOException e) {
            logger.error("Error parsing Keycloak response: {}", e.getMessage());
            return false;
        } catch (HttpClientErrorException e) {
            logger.error("HTTP Client Error during authentication: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Uploads a template file to the Fineract API
     *
     * @param fileBytes the file content as a byte array
     * @param endpoint  the API endpoint to upload to
     * @param fileName  the name of the file
     * @return true if the upload was successful, false otherwise
     */
    public boolean uploadTemplate(byte[] fileBytes, String endpoint, String fileName) {
        if (accessToken == null && !authenticate()) {
            logger.error("Authentication failed. Cannot upload template.");
            return false;
        }

        logger.info("Uploading template: {} to endpoint: {}", fileName, endpoint);

        // Implement retry logic
        int attempts = 0;
        long retryInterval = initialRetryInterval;

        while (attempts < maxRetryAttempts) {
            if (attempts > 0) {
                logger.info("Retry attempt {} of {} for file: {}",
                        attempts, maxRetryAttempts, fileName);
                try {
                    Thread.sleep(retryInterval);
                    // Calculate next retry interval with exponential backoff
                    retryInterval = Math.min(
                            (long) (retryInterval * retryMultiplier),
                            maxRetryInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    logger.warn("Retry interrupted for file: {}", fileName);
                    return false;
                }
            }
            attempts++;

            try {
                // Set up headers with proper content type and authentication
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.MULTIPART_FORM_DATA);
                headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
                headers.set("Fineract-Platform-TenantId", tenantId);
                headers.set("Authorization", "Bearer " + accessToken);

                // Use the correct content type for Excel xls files (BIFF8 format)
                String contentType = "application/vnd.ms-excel";
                HttpHeaders fileHeaders = new HttpHeaders();
                fileHeaders.setContentType(MediaType.parseMediaType(contentType));
                fileHeaders.add("Content-Type", contentType);

                // Set the filename in Content-Disposition header
                String cleanFileName = fileName.contains("/") ? 
                        fileName.substring(fileName.lastIndexOf('/') + 1) : fileName;

                fileHeaders.set("Content-Disposition", 
                        "form-data; name=\"file\"; filename=\"" + cleanFileName + "\"");

                // Create file resource
                ByteArrayResource fileResource = new ByteArrayResource(fileBytes) {
                    @Override
                    public String getFilename() {
                        return cleanFileName;
                    }
                };

                // Create the request parts
                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("file", new HttpEntity<>(fileResource, fileHeaders));
                body.add("locale", locale);
                body.add("dateFormat", dateFormat);

                // Add additional parameters that might be required
                if (endpoint.contains("clients")) {
                    body.add("entityType", "clients");
                }

                // Create the HTTP entity with headers and body
                HttpEntity<MultiValueMap<String, Object>> requestEntity = 
                        new HttpEntity<>(body, headers);
                
                // Build the URL
                String url = fineractUrl + "/" + endpoint;

                // Add query parameters for specific endpoints
                if (endpoint.contains("clients/uploadtemplate")) {
                    url += "?legalFormType=CLIENTS_PERSON";
                    logger.info("Adding legalFormType parameter for client template upload");
                }

                logger.info("Sending request to: {}", url);
                
                // Make the request
                ResponseEntity<String> response = restTemplate.exchange(
                        url, HttpMethod.POST, requestEntity, String.class);
                
                if (response.getStatusCode().is2xxSuccessful()) {
                    logger.info("Successfully uploaded! {}", fileName);
                    return true;
                } else {
                    logger.error("Failed to upload {}", fileName);
                    logger.error("Status: {} ({})", response.getStatusCodeValue(), 
                            response.getStatusCode().getReasonPhrase());
                }
            } catch (HttpClientErrorException e) {
                logger.error("HTTP Client Error while uploading {}: {}", fileName, e.getMessage());
                logger.error("Response body: {}", e.getResponseBodyAsString());
                
                // Don't retry client errors (4xx) except for specific cases
                if (e.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS &&
                        e.getStatusCode() != HttpStatus.REQUEST_TIMEOUT) {
                    return false;
                }
            } catch (HttpServerErrorException e) {
                logger.error("Server Error while uploading {}: {}", fileName, e.getMessage());
                // Continue with retry for server errors (5xx)
            } catch (ResourceAccessException e) {
                logger.error("Network error while uploading {}: {}", fileName, e.getMessage());
                // Continue with retry for network errors
            } catch (Exception e) {
                logger.error("Unexpected error while uploading {}: {}", fileName, e.getMessage(), e);
            }
        }
        
        logger.error("Failed to upload {} after {} attempts", fileName, attempts);
        return false;
    }
}