package com.example.fineractsetup.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for managing template files and their endpoints
 */
@Service
public class TemplateService {
    private static final Logger logger = LoggerFactory.getLogger(TemplateService.class);
    
    private final FileService fileService;
    private final FineractApiService fineractApiService;
    
    // Map of template files to their corresponding API endpoints
    private static final Map<String, String> TEMPLATE_TO_ENDPOINT;
    
    static {
        Map<String, String> map = new HashMap<>();
        map.put("data/ChartOfAccount.xls", "glaccounts/uploadtemplate");
        map.put("data/Offices.xls", "offices/uploadtemplate");
        map.put("data/Staff.xls", "staff/uploadtemplate");
        map.put("data/Users.xls", "users/uploadtemplate");
        map.put("data/Clients.xls", "clients/uploadtemplate");
        map.put("data/SavingsProducts.xls", "savingsaccounts/uploadtemplate");
        map.put("data/LoanProducts.xls", "loans/uploadtemplate");
        map.put("data/SavingsTransactions.xls", "savingsaccounts/transactions/uploadtemplate");
        map.put("data/LoanRepayments.xls", "loans/repayments/uploadtemplate");
        TEMPLATE_TO_ENDPOINT = Collections.unmodifiableMap(map);
    }
    
    public TemplateService(FileService fileService, FineractApiService fineractApiService) {
        this.fileService = fileService;
        this.fineractApiService = fineractApiService;
    }
    
    /**
     * Gets all template paths
     * 
     * @return array of template paths
     */
    public String[] getAllTemplatePaths() {
        return TEMPLATE_TO_ENDPOINT.keySet().toArray(new String[0]);
    }
    
    /**
     * Gets the API endpoint for a template
     * 
     * @param templatePath the path to the template
     * @return the API endpoint
     */
    public String getEndpointForTemplate(String templatePath) {
        return TEMPLATE_TO_ENDPOINT.get(templatePath);
    }
    
    /**
     * Processes a template file and uploads it to the Fineract API
     * 
     * @param templatePath the path to the template file
     * @return true if the upload was successful, false otherwise
     */
    public boolean processTemplate(String templatePath) {
        logger.info("Processing template: {}", templatePath);
        
        String endpoint = getEndpointForTemplate(templatePath);
        if (endpoint == null) {
            logger.error("No endpoint found for template: {}", templatePath);
            return false;
        }
        
        try {
            // Get the template file from the classpath
            InputStream inputStream = fileService.getTemplateInputStream(templatePath);
            if (inputStream == null) {
                logger.warn("Template file not found: {}", templatePath);
                return false;
            }
            
            // Validate the Excel file
            if (!fileService.validateExcelFile(inputStream)) {
                logger.error("Template file validation failed: {}", templatePath);
                return false;
            }
            
            // Get a fresh input stream for format conversion
            // Our new FileService implementation handles stream closure properly
            InputStream formatStream = fileService.getTemplateInputStream(templatePath);
            
            // Ensure the file is in XLS format
            byte[] fileBytes = fileService.ensureXlsFormat(formatStream);
            
            // Upload the file to the Fineract API
            boolean success = fineractApiService.uploadTemplate(fileBytes, endpoint, templatePath);
            
            if (success) {
                logger.info("Successfully processed template: {}", templatePath);
            } else {
                logger.error("Failed to process template: {}", templatePath);
            }
            
            return success;
        } catch (IOException e) {
            logger.error("Error processing template: {}", templatePath, e);
            return false;
        }
    }
}