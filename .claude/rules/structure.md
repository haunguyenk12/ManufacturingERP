You are an expert in Java programming, Spring Boot, Spring Framework, Maven, JUnit, and related Java technologies.

> **Lưu ý:** Đây là style-guide Java/Spring Boot chung, KHÔNG viết riêng cho repo này. Khi mục dưới
> đây mâu thuẫn với quyết định cụ thể của project (đã ghi trong `CLAUDE.md` hoặc các file khác trong
> `.claude/rules/*.md`), **luôn ưu tiên quyết định của project**. Các điểm mâu thuẫn đã biết được sửa
> trực tiếp bên dưới (Testing, Data Access, Async).

Code Style and Structure
- Write clean, efficient, and well-documented Java code with accurate Spring Boot examples.
- Use Spring Boot best practices and conventions throughout your code.
- Implement RESTful API design patterns when creating web services.
- Use descriptive method and variable names following camelCase convention.
- Structure Spring Boot applications: controllers, services, repositories, models, configurations.

Spring Boot Specifics
- Use Spring Boot starters for quick project setup and dependency management.
- Implement proper use of annotations (e.g., @SpringBootApplication, @RestController, @Service).
- Utilize Spring Boot's auto-configuration features effectively.
- Implement proper exception handling using @ControllerAdvice and @ExceptionHandler.

Naming Conventions — xem `CLAUDE.md` §3.1 (đã tách ra đó, không lặp lại ở đây để tránh 2 nơi phải sửa).

Java and Spring Boot Usage
- Use Java 17 or later features when applicable (e.g., records, sealed classes, pattern matching).
- Leverage Spring Boot 3.x features and best practices.
- Use Spring Data JPA for database operations when applicable.
- Implement proper validation using Bean Validation (e.g., @Valid, custom validators).

Configuration and Properties
- Use application.yml for configuration.
- Implement environment-specific configurations using Spring Profiles.
- Use @ConfigurationProperties for type-safe configuration properties.

Dependency Injection and IoC
- Use constructor injection over field injection for better testability.
- Leverage Spring's IoC container for managing bean lifecycles.

Testing
- Write unit tests using JUnit 5 and Spring Boot Test (Mockito, MockMvc).
- **Trong repo này**: hạn chế `@SpringBootTest` (chậm, chưa dùng lần nào — xem
  `.claude/rules/best-practices.md` §8.6 T1). Ưu tiên unit test + `@WebMvcTest`/`@DataJpaTest` có mục
  tiêu rõ, không dựng full context trừ khi thực sự cần integration test.

Performance and Scalability
- Implement caching strategies using Spring Cache abstraction.
- Use async processing with @Async for non-blocking operations.
- Implement proper database indexing and query optimization.

Security
- Implement Spring Security for authentication and authorization.
- Use proper password encoding (e.g., BCrypt).
- Implement CORS configuration when necessary.

Logging and Monitoring
- Use SLF4J with Logback for logging.
- Implement proper log levels (ERROR, WARN, INFO, DEBUG).
- Use Spring Boot Actuator for application monitoring and metrics.

API Documentation
- Use Springdoc OpenAPI (formerly Swagger) for API documentation.

Data Access and ORM
- Use Spring Data JPA for database operations.
- Implement proper entity relationships and cascading.
- Database migration: **Flyway** (project đã chọn, xem `.claude/rules/coding-rules.md` C5 — không dùng
  Liquibase, tránh 2 công cụ migration trong 1 repo).

Build and Deployment
- Use Maven for dependency management and build processes.
- Implement proper profiles for different environments (dev, test, prod).
- Docker + Docker Compose (đã dùng cho local dev, xem `CLAUDE.md` §2).

Follow best practices for:
- RESTful API design (proper use of HTTP methods, status codes, etc.).
- Asynchronous processing using Spring's @Async. **Không** dùng Spring WebFlux — stack repo này là
  Servlet/Spring MVC, không phải reactive.

Adhere to SOLID principles and maintain high cohesion and low coupling in your Spring Boot application design.