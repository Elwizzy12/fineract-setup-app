# Professional Design: Integrating Keycloak with Fineract Setup

## 1. The Goal: A Clean and Professional Design

The initial integration of Keycloak authentication worked, but it mixed the responsibility of authentication directly into the `FineractApiService`. This violated the **Single Responsibility Principle** and made the code harder to read and maintain.

This document outlines the refactoring of the `fineract-setup-app` to a cleaner, more professional design by separating the authentication logic into its own dedicated service.

---

## 2. The New Architecture: Separation of Concerns

We've introduced a new service, `KeycloakAuthService`, whose sole purpose is to handle Keycloak authentication. This leads to a much cleaner architecture:

*   **`KeycloakAuthService`**: Responsible for communicating with Keycloak, retrieving the access token, and caching it.
*   **`FineractApiService`**: Responsible only for communicating with the Fineract API. It is no longer aware of the details of Keycloak; it simply asks the `KeycloakAuthService` for a token when it needs one.

This separation makes the code more modular, easier to test, and more aligned with professional design principles.

### The Refined Authentication Flow

1.  The application starts.
2.  `FineractApiService` needs to make a request, so it asks `KeycloakAuthService` for an access token.
3.  `KeycloakAuthService` checks if it has a cached token.
    *   **If a token exists**, it returns it immediately.
    *   **If no token exists**, it performs the one-time authentication with Keycloak, caches the new token, and then returns it.
4.  `FineractApiService` receives the token and uses it to make the request to the Fineract API.

---

## 3. Detailed Code Changes

### Change 1: Creating the `KeycloakAuthService`

We created a new class to encapsulate all authentication logic.

**File Created:** `src/main/java/com/example/fineractsetup/service/KeycloakAuthService.java`

**Code Added:**
```java
package com.example.fineractsetup.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

/**
 * Service dedicated to handling authentication with Keycloak.
 */
@Service
public class KeycloakAuthService {
    private static final Logger logger = LoggerFactory.getLogger(KeycloakAuthService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

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

    private String accessToken;

    public KeycloakAuthService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Retrieves a valid access token, fetching a new one if necessary.
     *
     * @return The access token, or null if authentication fails.
     */
    public String getAccessToken() {
        if (accessToken == null) {
            if (!authenticate()) {
                logger.error("Authentication failed. Unable to retrieve access token.");
                return null;
            }
        }
        return accessToken;
    }

    /**
     * Authenticates with Keycloak and stores the access token.
     *
     * @return true if authentication is successful, false otherwise.
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
                logger.info("Access Token retrieved successfully.");
                logger.debug("Access Token: {}", this.accessToken); // Log token in debug
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
            logger.error("Response body: {}", e.getResponseBodyAsString());
            return false;
        }
    }
}
```

### Change 2: Refactoring `FineractApiService`

This service was simplified to focus solely on Fineract API communication.

**File:** `src/main/java/com/example/fineractsetup/service/FineractApiService.java`

**Code Changed:**
```java
package com.example.fineractsetup.service;

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

import java.util.Collections;

/**
 * Service for communicating with the Fineract API
 */
@Service
public class FineractApiService {
    private static final Logger logger = LoggerFactory.getLogger(FineractApiService.class);

    private final RestTemplate restTemplate;
    private final KeycloakAuthService keycloakAuthService;

    @Value("${fineract.api.url}")
    private String fineractUrl;

    // ... other @Value fields ...

    public FineractApiService(RestTemplate restTemplate, KeycloakAuthService keycloakAuthService) {
        this.restTemplate = restTemplate;
        this.keycloakAuthService = keycloakAuthService;
    }

    public boolean uploadTemplate(byte[] fileBytes, String endpoint, String fileName) {
        String accessToken = keycloakAuthService.getAccessToken();
        if (accessToken == null) {
            return false;
        }

        // ... (rest of the upload logic is the same)
    }
}
```

**Key improvements:**

*   The `authenticate()` method and all Keycloak-related `@Value` fields have been removed.
*   The `KeycloakAuthService` is now injected into the constructor.
*   The `uploadTemplate` method now starts by simply calling `keycloakAuthService.getAccessToken()` to get the token.

This new design is significantly cleaner and more maintainable.