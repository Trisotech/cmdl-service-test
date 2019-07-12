# Trisotech Automation Solution Command Line Test Runner

This project contains sample code to execute external test cases in the Trisotech Automation Solutions.

###### Building with Maven

```
$ mvn clean install
```

This will build an executable jar. The executable jar will be called cmnl-service-test-[version].one-jar in your target subdirectory.


###### Running test cases
You can then use this jar to run the tests cases using the console

```
java -jar cmdl-service-test-1.0.0.one-jar.jar [options] TestEndpointURL
 -b,--bearer <arg>   bearer token to use for authorization.
 -f,--folder <arg>   folder containing Test Cases XML file(s) with .xml
                     extensions.
```

If unspecified, the current working directory will be used.

The bearer token could also be provided in a file called either _.bearer_ or _bearer.txt_ in the test folder or one of its parent folder.
