package com.example.fineractsetup.service;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import org.apache.poi.ss.usermodel.*;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class MicrofinanceInit implements CommandLineRunner {
    private static final Logger logger = LoggerFactory.getLogger(MicrofinanceInit.class);

    @Value("${fineract.api.url}")
    private String fineractUrl;

    @Value("${fineract.api.tenant}")
    private String tenantId;

    @Value("${fineract.api.username}")
    private String username;

    @Value("${fineract.api.password}")
    private String password;

    private final RestTemplate restTemplate;

    public MicrofinanceInit(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    private static final Map<String, String> FILE_TO_ENDPOINT;

    static {
        FILE_TO_ENDPOINT = new HashMap<>();
        FILE_TO_ENDPOINT.put("data/Offices.xls", "offices/uploadtemplate");
        FILE_TO_ENDPOINT.put("data/Staff.xls", "staff/uploadtemplate");
        FILE_TO_ENDPOINT.put("data/Users.xls", "users/uploadtemplate");
        FILE_TO_ENDPOINT.put("data/ChartOfAccounts.xls", "glaccounts/uploadtemplate");
        FILE_TO_ENDPOINT.put("data/Clients.xls", "clients/uploadtemplate");
        FILE_TO_ENDPOINT.put("data/SavingsProducts.xls", "savingsproducts");
        FILE_TO_ENDPOINT.put("data/LoanProducts.xls", "loanproducts");
        FILE_TO_ENDPOINT.put("data/SavingsTransactions.xls", "savingsaccounts/transactions/uploadtemplate");
        FILE_TO_ENDPOINT.put("data/LoanRepayments.xls", "loans/repayments/uploadtemplate");

    }

    private static final String[] FILES = FILE_TO_ENDPOINT.keySet().toArray(new String[0]);

    @Override
    public void run(String... args) throws Exception {
        logger.info("Starting microfinance data import process...");
        logger.info("Using Fineract URL: {}", fineractUrl);
        for (String fileName : FILES) {
            String endpoint = FILE_TO_ENDPOINT.get(fileName);
            try {
                logger.info("Processing file: {} with endpoint: {}", fileName, endpoint);
                try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName)) {
                    if (inputStream == null) {
                        logger.error("File not found in classpath: {}", fileName);
                        continue;
                    }

                    // Create a temporary file with .xlsx extension
                    File tempFile = File.createTempFile("import-", ".xlsx");
                    tempFile.deleteOnExit();

                    try (FileOutputStream out = new FileOutputStream(tempFile)) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }

                    // No conversion needed for .xls
                    logger.info("Processing file: {}", tempFile.getName());
                    uploadFile(tempFile, endpoint, fileName);
                }
            } catch (Exception e) {
                logger.error("Error processing file: {}", fileName, e);
            }
        }
        logger.info("Microfinance setup completed. Application will now exit.");
        System.exit(0);
    }

    private void uploadFile(File file, String endpoint, String originalFileName) throws IOException {
        logger.info("Uploading file: {} (original: {}) to endpoint: {}", file.getName(), originalFileName, endpoint);

        // Verify file exists and is readable
        if (!file.exists() || !file.canRead()) {
            throw new IOException("File does not exist or is not readable: " + file.getAbsolutePath());
        }

        try {
            // Use Apache POI to validate and process the Excel file before uploading
            logger.info("Validating Excel file using Apache POI");
            try (InputStream is = Files.newInputStream(file.toPath());
                 Workbook workbook = WorkbookFactory.create(is)) {
                
                // Log basic information about the workbook
                int numberOfSheets = workbook.getNumberOfSheets();
                logger.info("Excel file contains {} sheets", numberOfSheets);
                
                for (int i = 0; i < numberOfSheets; i++) {
                    Sheet sheet = workbook.getSheetAt(i);
                    logger.info("Sheet {}: '{}' contains {} rows", 
                               i, sheet.getSheetName(), sheet.getLastRowNum() + 1);
                }
                
                logger.info("Excel file validation successful");
            } catch (Exception e) {
                logger.error("Failed to process Excel file with Apache POI: {}", e.getMessage(), e);
                throw new IOException("Invalid Excel file format: " + e.getMessage(), e);
            }

            // Set up headers with proper content type and authentication
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
            headers.set("Fineract-Platform-TenantId", tenantId);

            // Create Basic Auth header
            String auth = username + ":" + password;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + encodedAuth);

            // Log the request details for debugging
            logger.debug("Request headers: {}", headers);
            logger.debug("File size: {} bytes, path: {}", file.length(), file.getAbsolutePath());

            // Set content type for .xlsx files
            String contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            HttpHeaders fileHeaders = new HttpHeaders();
            fileHeaders.setContentType(MediaType.parseMediaType(contentType));
            
            // Ensure filename has .xlsx extension
            String fileName = originalFileName.replace("data/", "");
            if (fileName.endsWith(".xls") && !fileName.endsWith(".xlsx")) {
                fileName = fileName.substring(0, fileName.length() - 4) + ".xlsx";
            }
            fileHeaders.setContentDispositionFormData("file", fileName);

            // Create file resource
            FileSystemResource fileResource = new FileSystemResource(file) {
                @Override
                public String getFilename() {
                    // Use the same filename with .xlsx extension
                    String name = originalFileName.replace("data/", "");
                    if (name.endsWith(".xls") && !name.endsWith(".xlsx")) {
                        name = name.substring(0, name.length() - 4) + ".xlsx";
                    }
                    return name;
                }
            };

            // Create the request parts
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new HttpEntity<>(fileResource, fileHeaders));
            body.add("locale", "en");
            body.add("dateFormat", "dd MMMM yyyy");

            // Log the request details
            logger.debug("Request body parts: {}", body.keySet());

            // Create the HTTP entity with headers and body
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

            // Build the URL
            String url = fineractUrl + "/" + endpoint;
            logger.info("Sending request to: {}", url);

            // Make the request with detailed error handling
            ResponseEntity<String> response;
            if (endpoint.equals("savingsproducts") || endpoint.equals("loanproducts")) {
                response = restTemplate.exchange(url, HttpMethod.PUT, requestEntity, String.class);
            } else {
                response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
            }

            if (response.getStatusCode().is2xxSuccessful()) {
                logger.info("✅ Successfully imported {}: {}", originalFileName,
                        response.getBody() != null ? response.getBody().substring(0, Math.min(100, response.getBody().length())) : "No response body");
            } else {
                logger.error("Failed to import {}", originalFileName);
                logger.error("Status: {} ({})", response.getStatusCodeValue(), response.getStatusCode().getReasonPhrase());
                logger.error("Response: {}", response.getBody());
                if (response.getStatusCode() == HttpStatus.FORBIDDEN) {
                    logger.error("🔒 Authentication failed. Please check your credentials and tenant ID.");
                    logger.error("Username: {}, Tenant: {}", username, tenantId);
                }
            }
        } catch (HttpClientErrorException e) {
            logger.error("HTTP Client Error while uploading {}: {}", originalFileName, e.getMessage());

            throw e;
        } catch (HttpServerErrorException e) {
            logger.error("Server Error while uploading {}: {}", originalFileName, e.getMessage());
            logger.error("Status: {}", e.getRawStatusCode());

            throw e;
        } catch (Exception e) {
            logger.error("Unexpected error while uploading {}: {}", originalFileName, e.getMessage(), e);
            throw e;
        }
    }
}