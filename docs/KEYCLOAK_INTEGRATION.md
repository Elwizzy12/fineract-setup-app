# Detailed Guide: Integrating Keycloak with Fineract Setup

## 1. The Problem

The Fineract instance is secured using Keycloak, meaning it requires a JWT Bearer Token for API requests (`Authorization: Bearer <token>`). However, this `fineract-setup-app` was initially designed to use Basic Authentication (`Authorization: Basic <base64-credentials>`).

When the setup app tries to communicate with the secured Fineract API, its requests are rejected with a `401 Unauthorized` error because it's not providing the required OAuth2 token.

This document details the exact code changes made to solve this by teaching the `fineract-setup-app` how to speak OAuth2 with Keycloak.

---

## 2. The Solution: The Authentication Flow

Before we look at the code, it's important to understand the new process. The application now follows the **OAuth2 Password Credentials Grant** flow. This is a good choice for a trusted, internal command-line application like this one.

Here is the new step-by-step logic:

1.  The application starts.
2.  It attempts to upload the first Excel template.
3.  The `FineractApiService` checks if it already has an access token. On the first run, it doesn't.
4.  It triggers a **one-time authentication** process with Keycloak:
    a. It reads the Keycloak server URL and client credentials from `application.yml`.
    b. It makes a `POST` request directly to the Keycloak token endpoint.
    c. This request contains the `username`, `password`, `client_id`, `client_secret`, and `grant_type: password`.
5.  Keycloak validates these credentials and, if successful, returns a JSON object containing the `access_token`.
6.  The application parses this JSON, extracts the access token, and **stores it in memory** for future use.
7.  Now, with a valid token, the application proceeds with the original template upload request to the Fineract API, but this time it adds the `Authorization: Bearer <the-new-token>` header.
8.  For all subsequent template uploads, the application **reuses the stored token**, skipping the Keycloak authentication step entirely.

---

## 3. Detailed Code Changes

### Change 1: Updating the Configuration (`application.yml`)

We first needed to tell the application where Keycloak is and what credentials to use. We added a new `keycloak` section to the configuration file.

**File:** `src/main/resources/application.yml`

**Code Added:**
```yaml
keycloak:
  url: http://localhost:9000/realms/fineract/protocol/openid-connect/token
  grant-type: password
  client-id: community-app
  client-secret: "" # <-- IMPORTANT: This must be configured manually
```

**Explanation of each field:**

*   `url`: This is the specific, full URL to Keycloak's token endpoint for our `fineract` realm. This is where the application will send the POST request to get a token.
*   `grant-type`: This is set to `password`, telling Keycloak we are using the "Password Credentials Grant". This means we will be sending the user's username and password directly to get a token.
*   `client-id`: This identifies our `fineract-setup-app` to Keycloak. It must match a client ID configured in the Keycloak realm.
*   `client-secret`: This is the password for our client application. **It is critical that you get this value from the Keycloak Admin Console and paste it here before running the application.**

### Change 2: Creating the `ObjectMapper` Bean

To parse the JSON response from Keycloak, we need a library that can handle JSON. We use Jackson, which is already a dependency. To make it available for use in our service, we need to configure it as a Spring `@Bean`.

**File Created:** `src/main/java/com/example/fineractsetup/config/ObjectMapperConfig.java`

**Code Added:**
```java
package com.example.fineractsetup.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ObjectMapperConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
```

**Explanation:**

*   `@Configuration`: This tells Spring that this class contains configuration settings.
*   `@Bean`: This tells Spring that the `objectMapper()` method produces a bean that should be managed by the Spring container. This allows us to later inject the `ObjectMapper` into our service using `@Autowired` or constructor injection.

### Change 3: Refactoring the API Service (`FineractApiService.java`)

This is where the core logic was implemented.

**File:** `src/main/java/com/example/fineractsetup/service/FineractApiService.java`

**Detailed Changes:**

1.  **New Instance Variables and Constructor:** We added fields to hold the Keycloak configuration and the retrieved access token. The constructor was updated to accept the `ObjectMapper` we configured in the previous step.

    ```java
    // ... imports
    import com.fasterxml.jackson.databind.JsonNode;
    import com.fasterxml.jackson.databind.ObjectMapper;
    
    // ... inside the class
    private final ObjectMapper objectMapper;
    
    @Value("${keycloak.url}")
    private String keycloakUrl;

    @Value("${keycloak.grant-type}")
    private String grantType;

    @Value("${keycloak.client-id}")
    private String clientId;
    
    @Value("${keycloak.client-secret}")
    private String clientSecret;

    private String accessToken; // Used to cache the token

    // Updated constructor
    public FineractApiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }
    ```

2.  **New `authenticate()` Method:** This private method contains the logic for communicating with Keycloak.

    ```java
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
    ```

3.  **Updated `uploadTemplate()` Method:** The original upload method was modified to call `authenticate()` and use the Bearer token.

    **Code Changed (before the `while` loop):**
    ```java
    public boolean uploadTemplate(byte[] fileBytes, String endpoint, String fileName) {
        if (accessToken == null && !authenticate()) {
            logger.error("Authentication failed. Cannot upload template.");
            return false;
        }

        logger.info("Uploading template: {} to endpoint: {}", fileName, endpoint);
    ```
    *   **Explanation:** This is the new gatekeeper. It checks if the `accessToken` is null. If it is, it calls `authenticate()`. If `authenticate()` returns `false`, the entire upload process for the current file is aborted.

    **Code Changed (inside the `try` block):**
    ```java
    // The old Basic Auth header creation was REMOVED.
    // String auth = username + ":" + password;
    // String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    
    // It was REPLACED with this line:
    headers.set("Authorization", "Bearer " + accessToken);
    ```
    *   **Explanation:** This is the most critical change for Fineract communication. We are now setting the `Authorization` header to use the `Bearer` scheme with the token we received from Keycloak.

---

## 4. How to Use

1.  **Get Client Secret:** In the Keycloak Admin Console, navigate to `fineract` realm -> `Clients` -> `community-app` -> `Credentials` tab and copy the secret.
2.  **Update Config:** Paste the secret into the `keycloak.client-secret` field in `src/main/resources/application.yml`.
3.  **Build & Run:**
    ```bash
    # Build the application
    mvn clean install -f /home/jude/jan/fineract-setup-app/pom.xml

    # Run the application
    java -jar /home/jude/jan/fineract-setup-app/target/microfinance-init-1.0-SNAPSHOT.jar
    ```
4.  **Observe Logs:** You will see the "Authenticating with Keycloak..." message once, followed by the access token being printed. All subsequent API calls will reuse this token.
