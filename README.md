# Fineract Setup Application

This application automates the setup of a Fineract instance by uploading template files directly from the classpath.

## Overview

The Fineract Setup Application is a simple utility that uploads Excel template files to a Fineract API instance to initialize a microfinance system. It's designed to be easy to understand and maintain, with clear separation of concerns.

## Architecture

The application follows a clean, service-oriented architecture:

### MicrofinanceInit
- Main entry point that implements CommandLineRunner
- Coordinates the overall process of uploading template files
- Handles application lifecycle (graceful shutdown)
- Tracks success/failure counts and reports results

### TemplateService
- Maps template files to their corresponding API endpoints
- Manages the list of templates to be processed
- Coordinates the template processing workflow

### FileService
- Loads template files directly from the classpath
- Validates Excel files using Apache POI
- Converts Excel files to proper XLS format when needed

### FineractApiService
- Handles all communication with the Fineract API
- Manages authentication and request headers
- Implements retry logic for resilient API communication
- Handles different types of HTTP errors appropriately

### RestTemplateConfig
- Configures the RestTemplate with proper timeouts
- Sets up SSL to accept all certificates (for development)
- Configures error handling for HTTP requests

## Configuration

The application is configured via `application.yml`:

```yaml
fineract:
  api:
    url: https://localhost/fineract-provider/api/v1  # Fineract API URL
    tenant: default                                  # Tenant ID
    username: mifos                                  # API username
    password: password                               # API password
    locale: en                                       # Locale for templates
    dateFormat: dd MMMM yyyy                         # Date format for templates

# RestTemplate configuration
rest:
  connection:
    timeout: 30000  # Connection timeout in milliseconds
  read:
    timeout: 60000  # Read timeout in milliseconds

# Retry configuration
retry:
  max-attempts: 3                # Maximum retry attempts
  initial-interval: 1000         # Initial backoff interval in milliseconds
  multiplier: 2.0                # Backoff multiplier
  max-interval: 10000            # Maximum backoff interval in milliseconds
```

## Usage

1. Ensure your Fineract instance is running
2. Configure the application.yml with your Fineract API details
3. Run the application:
   ```
   java -jar fineract-setup-app.jar
   ```

The application will automatically upload all template files to initialize your Fineract instance.