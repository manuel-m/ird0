# UUID Migration Guide

## Overview

This document describes the migration from Long to UUID primary keys in the Directory Service, along with the implementation of DTOs using MapStruct.

## Summary of Changes

### Database Schema
- **Primary Key**: Changed from `BIGSERIAL` (Long) to `UUID`
- **Generation**: Application-generated via `@PrePersist` lifecycle hook
- **Storage**: PostgreSQL native `uuid` type (128-bit)

### Application Layer
- **DTO Layer**: Introduced `DirectoryEntryDTO` with Bean Validation
- **Mapping**: Implemented MapStruct for automatic DTO/entity conversion
- **CSV Upload**: Added REST endpoint for manual CSV import
- **Validation**: Added field validation (@NotBlank, @Email)

## Breaking Changes

### API Endpoints

**Before (Long ID):**
```bash
GET /api/policyholders/1
PUT /api/policyholders/1
DELETE /api/policyholders/1
```

**After (UUID):**
```bash
GET /api/policyholders/c9088e6f-86a4-4001-9a6a-554510787dd9
PUT /api/policyholders/c9088e6f-86a4-4001-9a6a-554510787dd9
DELETE /api/policyholders/c9088e6f-86a4-4001-9a6a-554510787dd9
```

### Response Format

**Before:**
```json
{
  "id": 1,
  "name": "John Doe",
  "type": "individual",
  "email": "john@example.com",
  "phone": "555-1234",
  "address": "123 Main St",
  "additionalInfo": "Test entry"
}
```

**After:**
```json
{
  "id": "c9088e6f-86a4-4001-9a6a-554510787dd9",
  "name": "John Doe",
  "type": "individual",
  "email": "john@example.com",
  "phone": "555-1234",
  "address": "123 Main St",
  "additionalInfo": "Test entry"
}
```

### Data Migration

All existing data was dropped during migration. The database schema was recreated with UUID primary keys.

**Database Schema Changes:**
```sql
-- Before
CREATE TABLE directory_entry (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    type VARCHAR(50),
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(50),
    address TEXT,
    additional_info TEXT
);

-- After
CREATE TABLE directory_entry (
    id UUID PRIMARY KEY,
    name VARCHAR(255),
    type VARCHAR(50),
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(50),
    address TEXT,
    additional_info TEXT
);
```

## New Features

### 1. CSV Import REST Endpoint

Manual CSV upload via HTTP POST:

```bash
curl -X POST http://localhost:8081/api/policyholders/import \
  -F "file=@policyholders.csv"
```

**Response:**
```json
{
  "totalRows": 100,
  "newRows": 50,
  "updatedRows": 30,
  "unchangedRows": 18,
  "failedRows": 2
}
```

**Features:**
- File validation (must be .csv extension)
- Reuses existing CSV import logic (same as SFTP polling)
- Batch processing (500 rows at a time)
- Change detection (new/updated/unchanged tracking)
- Email-based upsert

### 2. Bean Validation

Request validation with clear error messages:

```bash
curl -X POST http://localhost:8081/api/policyholders \
  -H "Content-Type: application/json" \
  -d '{"name": "Test", "type": "individual", "email": "invalid-email"}'
```

**Response:**
```json
{
  "timestamp": "2026-01-09T...",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "errors": [
    {
      "field": "email",
      "message": "Invalid email format"
    },
    {
      "field": "phone",
      "message": "Phone is required"
    }
  ]
}
```

### 3. DTO Layer

All endpoints now accept/return DTOs instead of entities:

**DirectoryEntryDTO fields:**
- `id` (UUID, nullable) - Present in responses, null in create requests
- `name` (String, required) - Entry name
- `type` (String, required) - Entry type
- `email` (String, required, validated) - Email address
- `phone` (String, required) - Phone number
- `address` (String, optional) - Physical address
- `additionalInfo` (String, optional) - Additional metadata

## Implementation Details

### UUID Generation

UUIDs are generated automatically by the entity:

```java
@Entity
public class DirectoryEntry {
  @Id
  @Column(columnDefinition = "uuid", updatable = false, nullable = false)
  private UUID id;

  @PrePersist
  public void generateId() {
    if (this.id == null) {
      this.id = UUID.randomUUID();
    }
  }
}
```

**When Generated:**
- Before INSERT operations (JPA lifecycle)
- Before CSV upsertByEmail operations (explicit call)
- Not on UPDATE (preserves existing UUID)

### MapStruct Configuration

**Dependencies:**
```xml
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5.Final</version>
</dependency>

<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct-processor</artifactId>
    <version>1.5.5.Final</version>
    <scope>provided</scope>
</dependency>
```

**Annotation Processor:**
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <parameters>true</parameters>
        <annotationProcessorPaths>
            <path>
                <groupId>org.mapstruct</groupId>
                <artifactId>mapstruct-processor</artifactId>
                <version>1.5.5.Final</version>
            </path>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok</artifactId>
                <version>1.18.38</version>
            </path>
            <path>
                <groupId>org.projectlombok</groupId>
                <artifactId>lombok-mapstruct-binding</artifactId>
                <version>0.2.0</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

**Key Configuration:**
- `<parameters>true</parameters>` - Required for Spring @PathVariable UUID binding
- MapStruct processor before Lombok (order matters)
- lombok-mapstruct-binding for compatibility

### Native SQL with UUID

The `upsertByEmail` method handles UUID casting:

```sql
INSERT INTO directory_entry (id, name, type, email, phone, address, additional_info)
VALUES (CAST(:#{#entry.id} AS uuid), :#{#entry.name}, :#{#entry.type}, :#{#entry.email},
        :#{#entry.phone}, :#{#entry.address}, :#{#entry.additionalInfo})
ON CONFLICT (email) DO UPDATE SET
    name = EXCLUDED.name,
    type = EXCLUDED.type,
    phone = EXCLUDED.phone,
    address = EXCLUDED.address,
    additional_info = EXCLUDED.additional_info
```

**Key Points:**
- Explicit CAST for UUID parameter
- ID included in INSERT (generated via @PrePersist)
- ON CONFLICT preserves original UUID (ID not updated)

### CSV Import UUID Handling

In `CsvImportService.upsertBatch()`:

```java
for (DirectoryEntry entry : entries) {
    entry.generateId();  // Explicit call before native query
    repository.upsertByEmail(entry);
}
```

**Why Explicit Call:**
- @PrePersist doesn't trigger for native queries
- Must call generateId() manually before upsertByEmail
- Ensures UUID is present for INSERT

## Benefits

### UUID Advantages
- **Global Uniqueness**: No ID conflicts when merging data
- **Security**: No sequential ID enumeration
- **Distributed Systems**: Can generate IDs without database coordination
- **Database Agnostic**: UUIDs work across different databases

### MapStruct Advantages
- **Type Safety**: Compile-time type checking
- **Performance**: No reflection overhead (plain method calls)
- **Maintainability**: Automatic updates when model changes
- **Code Generation**: Eliminates boilerplate mapping code

### DTO Advantages
- **API Stability**: Internal model changes don't affect API
- **Validation**: Centralized request validation
- **Security**: Don't expose internal entity structure
- **Flexibility**: Different DTOs for different use cases

## Testing

### CRUD Operations
```bash
# Create
curl -X POST http://localhost:8081/api/policyholders \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","type":"individual","email":"test@example.com","phone":"555-0000"}'

# Get by UUID
curl http://localhost:8081/api/policyholders/c9088e6f-86a4-4001-9a6a-554510787dd9

# Update
curl -X PUT http://localhost:8081/api/policyholders/c9088e6f-86a4-4001-9a6a-554510787dd9 \
  -H "Content-Type: application/json" \
  -d '{"name":"Updated","type":"individual","email":"updated@example.com","phone":"555-1111"}'

# Delete
curl -X DELETE http://localhost:8081/api/policyholders/c9088e6f-86a4-4001-9a6a-554510787dd9
```

### CSV Upload
```bash
curl -X POST http://localhost:8081/api/policyholders/import \
  -F "file=@policyholders.csv"
```

### Validation
```bash
# Missing required field
curl -X POST http://localhost:8081/api/policyholders \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","type":"individual","email":"test@example.com"}'
# Returns 400: Phone is required

# Invalid email
curl -X POST http://localhost:8081/api/policyholders \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","type":"individual","email":"not-an-email","phone":"555-0000"}'
# Returns 400: Invalid email format
```

## Rollback Plan

If rollback is needed:

1. Revert code changes to previous commit
2. Drop PostgreSQL databases
3. Rebuild and redeploy with Long ID version
4. Restore data from backup (if available)

Since data loss was acceptable for this migration, rollback is straightforward.

## Files Modified

**New Files:**
- `microservices/directory/src/main/java/com/ird0/directory/dto/DirectoryEntryDTO.java`
- `microservices/directory/src/main/java/com/ird0/directory/mapper/DirectoryEntryMapper.java`

**Modified Files:**
- `microservices/directory/pom.xml`
- `microservices/directory/src/main/java/com/ird0/directory/model/DirectoryEntry.java`
- `microservices/directory/src/main/java/com/ird0/directory/repository/DirectoryEntryRepository.java`
- `microservices/directory/src/main/java/com/ird0/directory/service/DirectoryEntryService.java`
- `microservices/directory/src/main/java/com/ird0/directory/service/CsvImportService.java`
- `microservices/directory/src/main/java/com/ird0/directory/controller/DirectoryEntryController.java`

## Performance Considerations

### UUID vs Long Storage
- **Size**: UUID is 16 bytes vs Long 8 bytes
- **Impact**: Minimal for typical dataset sizes
- **PostgreSQL**: Native uuid type is optimized

### Index Performance
- **UUIDs**: Non-sequential (random distribution)
- **Impact**: Slight B-tree index performance impact vs sequential
- **Scale**: Negligible for <1M entries

### MapStruct Performance
- **Compile Time**: Adds ~2-5 seconds (code generation)
- **Runtime**: Zero reflection overhead
- **Faster Than**: Manual mapping, reflection-based mappers

## Conclusion

The UUID migration and DTO implementation provide a robust, scalable foundation for the Directory Service. All CRUD operations work correctly, CSV import (both SFTP and REST) functions properly, and the code is production-ready.
