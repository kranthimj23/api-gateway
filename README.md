# API Gateway

API Gateway microservice for the Mobile Banking Platform. Handles request routing, authentication enforcement, rate limiting, and circuit breaker functionality.

## Technology Stack

- Java 17
- Spring Boot 3.2.0
- Spring Cloud Gateway
- Resilience4j for circuit breaker
- Redis for rate limiting
- Micrometer for metrics

## Features

- Request routing to backend services
- JWT authentication enforcement
- Rate limiting (100 requests/minute per user)
- Circuit breaker pattern for fault tolerance
- Request/response logging
- CORS configuration
- Health check endpoints

## Routing Configuration

| Path | Target Service | Description |
|------|----------------|-------------|
| /api/v1/auth/** | auth-service | Authentication endpoints |
| /api/v1/users/** | user-service | User management endpoints |
| /api/v1/loans/** | loan-service | Loan endpoints (future) |
| /api/v1/notifications/** | notification-service | Notification endpoints (future) |

## Project Structure

```
api-gateway/
├── src/
│   └── main/
│       ├── java/com/mobilebanking/gateway/
│       │   ├── config/          # Route and security configuration
│       │   ├── controller/      # Fallback and health controllers
│       │   ├── exception/       # Exception handling
│       │   └── filter/          # Custom gateway filters
│       └── resources/
│           ├── application.yml
│           ├── application-dev.yml
│           └── application-prod.yml
├── helm/                        # Helm chart for Kubernetes deployment
├── Dockerfile                   # Multi-stage Docker build
├── Jenkinsfile                  # CI/CD pipeline
└── pom.xml                      # Maven dependencies
```

## Local Development

### Prerequisites

- Java 17+
- Maven 3.8+
- Redis (for rate limiting)
- Docker (optional)

### Running Locally

1. Start Redis:
```bash
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

2. Run the application:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

3. Access the gateway: http://localhost:8080

### Building Docker Image

```bash
docker build -t api-gateway:latest .
```

## Kubernetes Deployment

### Using Helm

```bash
# Development
helm install api-gateway ./helm -f ./helm/values-dev.yaml -n mobile-banking-dev

# Staging
helm install api-gateway ./helm -f ./helm/values-staging.yaml -n mobile-banking-staging

# Production
helm install api-gateway ./helm -f ./helm/values-prod.yaml -n mobile-banking-prod
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| AUTH_SERVICE_URL | Auth service URL | http://auth-service:8081 |
| USER_SERVICE_URL | User service URL | http://user-service:8082 |
| REDIS_HOST | Redis host for rate limiting | localhost |
| REDIS_PORT | Redis port | 6379 |
| JWT_SECRET | Secret key for JWT validation | - |
| RATE_LIMIT_REQUESTS | Max requests per minute | 100 |

## Rate Limiting

The gateway implements rate limiting using Redis:
- Default: 100 requests per minute per user
- Configurable per environment
- Returns 429 Too Many Requests when exceeded

## Circuit Breaker

Resilience4j circuit breaker configuration:
- Failure rate threshold: 50%
- Wait duration in open state: 60 seconds
- Permitted calls in half-open state: 10
- Sliding window size: 100 requests

## Security Headers

The gateway adds the following security headers:
- X-Content-Type-Options: nosniff
- X-Frame-Options: DENY
- X-XSS-Protection: 1; mode=block
- Strict-Transport-Security: max-age=31536000

## Testing

```bash
# Run unit tests
./mvnw test

# Run integration tests
./mvnw verify -P integration-tests
```

## CI/CD Pipeline

The Jenkinsfile includes the following stages:
1. Build - Compile and package
2. Unit Tests - Run unit tests
3. Integration Tests - Run integration tests
4. Code Quality - SonarQube analysis
5. Docker Build - Build container image
6. Docker Push - Push to registry
7. Helm Lint - Validate Helm chart
8. Deploy - Deploy to Kubernetes
9. Smoke Tests - Verify deployment

## Related Services

- [Auth Service](https://github.com/kranthimj23/auth-service) - Authentication and JWT tokens
- [User Service](https://github.com/kranthimj23/user-service) - User profile management
- [Infrastructure](https://github.com/kranthimj23/mobile-banking-infra) - Terraform and observability
