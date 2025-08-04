package com.example.fineractsetup.config;

import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.File;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

@Configuration
public class RestTemplateConfig {
    private static final Logger logger = LoggerFactory.getLogger(RestTemplateConfig.class);
    
    @Bean
    public RestTemplate restTemplate() {
        try {
            // Create a trust-all SSL context that doesn't validate certificates
            // Note: This is a temporary solution for development purposes only
            // In production, proper certificate validation should be implemented
            logger.info("Creating trust-all SSL context for development");
            
            SSLContext sslContext = SSLContextBuilder
                    .create()
                    .loadTrustMaterial(null, (chain, authType) -> true)
                    .build();
            
            SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(
                    sslContext, 
                    NoopHostnameVerifier.INSTANCE);
            
            CloseableHttpClient httpClient = HttpClients.custom()
                    .setSSLSocketFactory(socketFactory)
                    .build();
            
            HttpComponentsClientHttpRequestFactory factory = 
                    new HttpComponentsClientHttpRequestFactory(httpClient);
            
            logger.info("SSL configuration successful");
            return new RestTemplate(factory);
        } catch (Exception e) {
            // Log the error but fall back to default RestTemplate if certificate loading fails
            logger.error("Failed to configure SSL with certificate: {}", e.getMessage(), e);
            logger.warn("Falling back to default RestTemplate without SSL configuration");
            return new RestTemplate();
        }
    }
}
