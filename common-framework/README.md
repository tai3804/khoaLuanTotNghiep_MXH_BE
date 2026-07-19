# Common Framework

This is a common framework for Spring Boot microservices based on Clean Architecture and CQRS principles. It contains essential shared components to reduce boilerplate code and ensure consistency across microservices.

## Features

- **BaseEntity**: Abstract JPA entity containing auditing fields (`createdAt`, `updatedAt`, `createdBy`, `updatedBy`, `deleted`) using UUID as a primary key.
- **BaseRepository**: Generic repository extending `JpaRepository` and `JpaSpecificationExecutor`.
- **ApiResponse & Exception Handling**: A unified wrapper for all API responses and centralized error management using `@ControllerAdvice` and standard `ErrorCode` enums.
- **BaseMapper**: A generic MapStruct interface for standardizing DTO <-> Entity mapping, ignoring null values seamlessly.
- **JWT Security (RS256)**: Configured as a Stateless Resource Server. Uses Public Keys (RS256) to safely verify JWT signatures without sharing the private key.
- **Redis Caching**: Centralized Redis configuration utilizing JSON serialization for readable data storage and auto-mapped TTL properties.
- **Secure Filtering**: `BaseFilter` with built-in pagination, `SortDirection` enum, and strong `jakarta.validation` patterns to block Map Key injections.

## Getting Started

1. Include this module in your microservice as a dependency or build upon it directly.
2. Enable JPA Auditing in your main Spring Boot Application class by adding `@EnableJpaAuditing`.
3. Set your environment variables for JWT and Redis, or define them in your `application.yaml`:
   ```yaml
   app:
     security:
       jwt:
         public-key: |
           -----BEGIN PUBLIC KEY-----
           YOUR_PUBLIC_KEY_HERE
           -----END PUBLIC KEY-----

   spring:
     cache:
       redis:
         time-to-live: 60m # Automatically maps to Duration
   ```
4. Define your custom domain entities by extending `BaseEntity`.
5. Map objects between layers easily by creating MapStruct mappers extending `BaseMapper`.
6. Use `BaseFilter` as the request DTO for list APIs to automatically inherit strong validations and pagination fields.
