I need a tool to check all REST api GET endpoints.
The tool shall only verify is the endpoint response status 
* 2xx is ok else error

The workflow:

* The micro services are already up
* Don't use a fixed endpoint list
* Scan @microservices/*/openApi/*.yaml to determine which the endpoint list to test
* check each endpoint sequentially from the list, stop on first error

Your tasks:

* analyse and propose tech to use (node, java, ...) 
* create a dedicated test tool
* add a dedicated target in @Makefile
