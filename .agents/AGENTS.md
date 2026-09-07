# Custom Rules for KLTN_BE

The following rules apply to all tasks within this workspace.

## Lombok Usage Rule

- **@FieldDefaults**: You MUST always add `@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)` and `@RequiredArgsConstructor` (if the class has dependencies) to ALL Service, Controller, Handler, RepositoryImpl, and Component classes.
- **Why**: This ensures dependencies and fields are inherently private and immutable by default, keeping the code clean and avoiding boilerplate `private final` declarations on every single field.

## Strict Clean Architecture

- **No Command/Query in Controllers**: You MUST NEVER use Application-layer `Command` or `Query` objects directly in Controller endpoints (e.g., as `@RequestBody`).
- **Use Presentation DTOs**: Controllers must use their own Presentation-layer Request and Response DTOs (e.g., `RegisterUserRequest`, `LoginUserWebResponse`).
- **Mapping**: Controllers must use a Mapper (e.g., `AuthPresentationMapper`) to convert Presentation Requests into Application Commands/Queries before passing them to the handlers. Validation annotations (`@Valid`, `@NotBlank`, etc.) MUST ONLY be placed on the Presentation Request DTOs, not on the Commands.

## Kafka Configuration

- **Auto-configuration over Manual**: You MUST use `spring-boot-starter-kafka` and configure Kafka entirely via `application.yaml` auto-configuration.
- **No Custom Config Classes**: Do NOT create custom `@Configuration` classes for Kafka producers/consumers/topics unless absolutely necessary for complex scenarios that `application.yaml` cannot handle.

## Unified Domain Entity & Infrastructure Repository Rule

- **Direct Database Mapping**: Domain Entities in `domain/entities` MUST extend `BaseEntity` and be annotated directly with JPA annotations (`@Entity`, `@Table`, etc.).
- **Infrastructure Repositories**: Repository interfaces (`PostRepository`, `CommentRepository`, etc.) reside in `infrastructure/persistence/repository/`, extending `BaseJpaRepository<Entity, UUID>` from `common-framework` and annotated with `@Repository`.
- **No Extra Layers**: Do NOT create separate `DbModel` classes, `ModelMapper` instances, or `RepositoryImpl` wrapper classes. Handlers import repository interfaces directly from `infrastructure/persistence/repository` for maximum simplicity and efficiency.

## MapStruct Feature Mapping Rule

- **Use MapStruct for Mappings**: All object transformations between Commands $\leftrightarrow$ Entities, Entities $\rightarrow$ Results, and Results $\rightarrow$ Presentation Responses MUST use MapStruct mappers annotated with `@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)`.
- **No Manual Builders in Handlers**: Handlers MUST NOT manually assign fields line-by-line using Lombok `.builder()` when mapping Entity to Result or Command to Entity. Inject MapStruct mappers into Handlers instead.
