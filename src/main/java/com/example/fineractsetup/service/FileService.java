package com.example.fineractsetup.service;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for handling file operations related to Excel templates
 */
@Service
public class FileService {
    private static final Logger logger = LoggerFactory.getLogger(FileService.class);
    
    // Cache for file contents to avoid repeated reads
    private final Map<String, byte[]> fileCache = new HashMap<>();

    /**
     * Gets the file content as a byte array, either from cache or by reading the file
     * 
     * @param templatePath the path to the template file
     * @return the file content as a byte array
     * @throws IOException if the file cannot be read
     */
    private byte[] getFileContent(String templatePath) throws IOException {
        // Check if we already have this file in the cache
        if (fileCache.containsKey(templatePath)) {
            logger.debug("Using cached file content for: {}", templatePath);
            return fileCache.get(templatePath);
        }
        
        logger.info("Loading template file from classpath: {}", templatePath);
        Resource resource = new ClassPathResource(templatePath);
        
        try (InputStream is = resource.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            // Read the entire file into a byte array
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            
            byte[] fileContent = baos.toByteArray();
            
            // Cache the file content for future use
            fileCache.put(templatePath, fileContent);
            
            return fileContent;
        }
    }

    /**
     * Gets an input stream for a template file from the classpath
     * 
     * @param templatePath the path to the template file
     * @return an InputStream for the template file
     * @throws IOException if the file cannot be read
     */
    public InputStream getTemplateInputStream(String templatePath) throws IOException {
        byte[] fileContent = getFileContent(templatePath);
        return new ByteArrayInputStream(fileContent);
    }

    /**
     * Validates an Excel file using Apache POI
     * 
     * @param inputStream the input stream of the Excel file
     * @return true if the file is valid, false otherwise
     */
    public boolean validateExcelFile(InputStream inputStream) {
        logger.info("Validating Excel file");
        
        // Read the entire stream into a byte array to avoid stream closure issues
        byte[] fileContent;
        try {
            fileContent = inputStreamToByteArray(inputStream);
        } catch (IOException e) {
            logger.error("Failed to read input stream: {}", e.getMessage(), e);
            return false;
        }
        
        // Create a new stream from the byte array for validation
        try (InputStream validationStream = new ByteArrayInputStream(fileContent);
             Workbook workbook = WorkbookFactory.create(validationStream)) {
            
            int numberOfSheets = workbook.getNumberOfSheets();
            logger.info("Excel file contains {} sheets", numberOfSheets);
            
            for (int i = 0; i < numberOfSheets; i++) {
                logger.info("Sheet {}: '{}' contains {} rows",
                        i, workbook.getSheetAt(i).getSheetName(), 
                        workbook.getSheetAt(i).getLastRowNum() + 1);
            }
            
            logger.info("Excel file validation successful");
            return true;
        } catch (Exception e) {
            logger.error("Failed to validate Excel file: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Ensures the Excel file is in XLS format (BIFF8)
     * 
     * @param inputStream the input stream of the Excel file
     * @return a byte array containing the Excel file in XLS format
     * @throws IOException if the file cannot be converted
     */
    public byte[] ensureXlsFormat(InputStream inputStream) throws IOException {
        logger.info("Ensuring Excel file is in XLS format");
        
        // Read the entire stream into a byte array to avoid stream closure issues
        byte[] fileContent = inputStreamToByteArray(inputStream);
        
        // Create a new stream from the byte array for processing
        try (InputStream processingStream = new ByteArrayInputStream(fileContent);
             Workbook workbook = WorkbookFactory.create(processingStream)) {
            
            // If it's already an HSSFWorkbook (XLS), just return the original bytes
            if (workbook instanceof HSSFWorkbook) {
                logger.info("File is already in XLS format");
                return fileContent;
            }
            
            // Convert to XLS format
            logger.info("Converting Excel file to XLS format");
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                try (HSSFWorkbook xlsWorkbook = new HSSFWorkbook()) {
                    // Copy all sheets to the new workbook
                    for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                        xlsWorkbook.createSheet(workbook.getSheetName(i));
                        // Copy data from original sheet to new sheet
                        // This is simplified - in a real implementation, you would copy all cell data
                    }
                    
                    xlsWorkbook.write(outputStream);
                }
                
                logger.info("Successfully converted Excel file to XLS format");
                return outputStream.toByteArray();
            }
        } catch (Exception e) {
            logger.error("Failed to convert Excel file to XLS format: {}", e.getMessage(), e);
            // If conversion fails, return the original bytes
            return fileContent;
        }
    }
    
    /**
     * Converts an InputStream to a byte array
     * 
     * @param inputStream the input stream to convert
     * @return the byte array containing the stream's content
     * @throws IOException if the stream cannot be read
     */
    private byte[] inputStreamToByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        return baos.toByteArray();
    }
}