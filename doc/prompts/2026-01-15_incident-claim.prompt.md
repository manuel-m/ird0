
You are a senior software architect specialized in insurance systems, Java Spring Boot, and event-driven architectures.
I am working on an insurance portal that allows policyholders to declare incidents. Before starting the implementation, I need a **detailed design document** describing:
* The involved services
* Their responsibilities and interactions
* The REST APIs
* The data model (conceptual and relational)
### Context
The insurance portal involves the following actors:
* **Policyholders (Insured users)**
* **Insurers**
* **Experts**
* **Providers**
Catalogs for policyholders, insurers, experts, and providers already exist.
### Feature to design: Incident Declaration
* A policyholder declares an incident through a **web interface**
* The declaration is sent via a **REST API**
* The system creates an **Incident** entity
* The corresponding **Insurer is notified**
* The insurer reviews and qualifies the incident
* The insurer can then:
  >   * Abandon / close the incident
  * Continue processing and move to the next phase (e.g. request an expert contribution)
### Technical constraints
* Backend: **Java 21, Spring Boot**
* Integration: **Spring Integration**
* Architecture: **REST-based**, event-driven where relevant
* Database: **PostgreSQL**
* Existing separate databases for:
  >   * Users
  * Insurers
  * Policyholders
### What I need from you
Please produce a **clear and structured design document** including:
1. **High-level architecture**
   >
   >    * Services involved (e.g. Incident Service, Notification Service, etc.)
   * Responsibilities of each service
   * Interaction flow between actors and services
2. **Incident lifecycle**
   >
   >    * States (e.g. DECLARED, QUALIFIED, ABANDONED, IN_PROGRESS, CLOSED)
   * State transitions and who triggers them
3. **REST API design**
   >    * Main endpoints for incident declaration and qualification
   * Example request/response payloads (JSON)
4. **Event and integration design**
   >
   >    * Events emitted (e.g. IncidentDeclared, IncidentQualified)
   * How Spring Integration is used
5. **Data model**
   >    * Conceptual data model (entities and relationships)
   * Relational PostgreSQL schema (tables, key fields)
   * How the Incident entity references insurers and policyholders
6. **Non-functional considerations**
   >    * Security (authentication/authorization assumptions)
   * Auditability and traceability
   * Scalability considerations

Analyse the current code base before processing 
The output should be written as a **technical design document**, suitable for developers to start implementation immediately.
