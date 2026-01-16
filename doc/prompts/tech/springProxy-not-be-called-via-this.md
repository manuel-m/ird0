@microservices/directory/src/main/java/com/ird0/directory/service/CsvImportService.java

triggers
“Methods with Spring proxy should not be called via this”


The issue comes from calling @Transactional methods internally within the same Spring bean, which bypasses Spring AOP proxies.

Your tasks:

Analyze the provided class.
Identify all internal method calls that bypass Spring proxies (e.g. @Transactional, @Async, etc.).
Refactor the code to eliminate these issues without using hacks such as ApplicationContext or AopContext.
Apply clean Spring best practices, preferably:
separating read/query logic from write/command logic, or
restructuring transaction boundaries properly.
Keep the behavior unchanged from a business perspective.
Improve the code where relevant (transaction usage, unnecessary save() calls in JPA, clearer intent).
Provide the final corrected code with explanations of the changes.

If multiple valid refactorings are possible, choose the cleanest and most idiomatic Spring solution and explain why.