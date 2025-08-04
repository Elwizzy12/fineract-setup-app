package com.example.fineractsetup.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;

@Configuration
public class RestTemplateConfig {
    private static final Logger logger = LoggerFactory.getLogger(RestTemplateConfig.class);
    
    @Value("${rest.connection.timeout:30000}")
    private int connectionTimeout;
    
    @Value("${rest.read.timeout:60000}")
    private int readTimeout;
    
    @Bean 
    public RestTemplate restTemplate() throws KeyStoreException, NoSuchAlgorithmException, KeyManagementException {
        logger.info("Configuring RestTemplate with connection timeout: {}ms, read timeout: {}ms, SSL validation disabled", 
                connectionTimeout, readTimeout);
        
        // Create a trust strategy that accepts all certificates
        TrustStrategy acceptingTrustStrategy = (X509Certificate[] chain, String authType) -> true;
        
        // Create SSL context with trust strategy
        SSLContext sslContext = org.apache.http.ssl.SSLContexts.custom()
                .loadTrustMaterial(null, acceptingTrustStrategy)
                .build();
        
        // Create SSL connection socket factory
        SSLConnectionSocketFactory csf = new SSLConnectionSocketFactory(sslContext);
        
        // Create HTTP client with SSL socket factory
        CloseableHttpClient httpClient = HttpClients.custom()
                .setSSLSocketFactory(csf)
                .build();
        
        // Create request factory with HTTP client
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setHttpClient(httpClient);
        requestFactory.setConnectTimeout(connectionTimeout);
        requestFactory.setReadTimeout(readTimeout);
        
        // Create RestTemplate with request factory
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        
        // Configure error handler
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler());
        
        logger.info("RestTemplate configured to ignore SSL certificate validation");
        
        return restTemplate;
    }
}
