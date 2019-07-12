package com.trisotech.automation.test;

import static org.fusesource.jansi.Ansi.ansi;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.fusesource.jansi.Ansi.Color;
import org.fusesource.jansi.AnsiConsole;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class CommandLineRunner {

    private static final String USAGE = "java -jar cmdl-service-test-1.0.0.one-jar.jar [options] TestEndpointURL";

    public static void main(String[] args) throws SAXException, ParserConfigurationException {

        CommandLineParser parser = new DefaultParser();

        Options options = new Options();
        options.addOption("f", "folder", true, "folder containing Test Cases XML file(s) with .xml extensions.");
        options.addOption("b", "bearer", true, "bearer token to use for authorization.");

        // Parse command line options
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            AnsiConsole.out.println(ansi().fg(Color.RED).a("Unexpected exception:" + e.getMessage()).reset());

            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(USAGE, options);
            System.exit(1);
        }

        // Extract arguments
        List<String> argList = cmd.getArgList();
        if (argList.size() != 1) {
            AnsiConsole.out.println(ansi().fg(Color.RED).a("Expected a Test Endoint URL").reset());

            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(USAGE, options);
            System.exit(1);
        }
        String testEndpointURL = argList.get(0);

        File folder = new File(cmd.getOptionValue("f", Paths.get(".").toAbsolutePath().normalize().toString()));
        AnsiConsole.out.println(ansi().fg(Color.WHITE).a("Using test case folder: " + folder).reset());
        AnsiConsole.out.println();

        String bearerToken = cmd.getOptionValue("b", null);
        if (bearerToken == null) {
            bearerToken = findBearerTokenFile(folder);
        }
        if (bearerToken == null) {
            AnsiConsole.out.println(ansi().fg(Color.RED).a("Expected a Bearer Token").reset());

            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(USAGE, options);
            System.exit(1);

        }

        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        dbFactory.setNamespaceAware(true);
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

        // For each test case file, submit the test case to the server
        File[] testCaseFiles = folder.listFiles((file) -> file.getName().endsWith(".xml") || file.getName().endsWith(".tc"));
        for (File testCaseFile : testCaseFiles) {
            AnsiConsole.out.print(ansi().fg(Color.WHITE).a(testCaseFile.getName() + ": ").reset());
            AnsiConsole.out.flush();

            // Submit the file via an HTTP POST
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(testEndpointURL).openConnection();
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Authorization", "Bearer " + bearerToken);
                connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8");

                OutputStream output = connection.getOutputStream();
                FileUtils.copyFile(testCaseFile, output);
                output.flush();

                // Evaluate the results from the API
                LinkedHashMap<String, Boolean> results = new LinkedHashMap<String, Boolean>();
                int passed = 0;
                int responseCode = connection.getResponseCode();
                if (responseCode == 200) {
                    try (InputStream is = connection.getInputStream()) {
                        Document xmlTestCases = dBuilder.parse(new InputSource(new InputStreamReader(is, StandardCharsets.UTF_8)));
                        NodeList testCases = xmlTestCases.getElementsByTagName("testCase");
                        for (int i = 0; i < testCases.getLength(); i++) {
                            Element testCase = (Element) testCases.item(i);
                            String id = testCase.getAttribute("id");
                            String name = testCase.getAttribute("name");
                            String identifier = "id: " + id;
                            if (name != null) {
                                identifier = name + " [" + id + "]";
                            }
                            boolean testPassed = "true".equals(testCase.getAttribute("passed"));
                            if (testPassed) {
                                passed += 1;
                            }
                            results.put(identifier, testPassed);
                        }
                    }
                    // Display the results
                    if (results.size() == 0) {
                        AnsiConsole.out.println(ansi().a("[").fg(Color.YELLOW).a("NO TESTS FOUND").reset().a("]"));
                    } else if (results.size() == passed) {
                        AnsiConsole.out.println(ansi().a("[").fg(Color.GREEN).a("PASSED " + passed + "/" + results.size()).reset().a("]"));
                    } else {
                        AnsiConsole.out.println(ansi().a("[").fg(Color.RED).a("FAILED " + passed + "/" + results.size()).reset().a("]"));
                        List<String> failedTests = results.entrySet().stream().filter((e) -> !e.getValue()).map((e) -> e.getKey()).collect(Collectors.toList());
                        AnsiConsole.out.println(ansi().fg(Color.RED).a("FAILED :").reset());
                        failedTests.forEach((t) -> AnsiConsole.out.println(ansi().fg(Color.RED).a(t).reset()));
                    }
                } else {
                    AnsiConsole.out
                            .println(ansi().fg(Color.RED).a("Invalid HTTP Response Code: " + responseCode + " " + connection.getResponseMessage()).reset());
                }

            } catch (Throwable t) {
                AnsiConsole.out.println(ansi().a("[").fg(Color.RED).a("Exception").reset().a("]"));
                t.printStackTrace();
            }
        }
        AnsiConsole.out.println();
        AnsiConsole.out.println(ansi().fg(Color.WHITE).a("Completed " + testCaseFiles.length + " test case files.").reset());

    }

    /**
     * Recursively find (through parents) a file named .bearer or bearer.txt and returns the file content
     *
     * @param folder the folder to look into recursively
     * @return null if not found or the bearer token read from the file
     */
    private static String findBearerTokenFile(File folder) {
        File bearerFile = new File(folder, ".bearer");
        if (!bearerFile.exists()) {
            bearerFile = new File(folder, "bearer.txt");
        }
        if (!bearerFile.exists()) {
            File parent = folder.getParentFile();
            return parent == null ? null : findBearerTokenFile(parent);
        }
        try {
            return FileUtils.readFileToString(bearerFile, StandardCharsets.UTF_8).trim();
        } catch (Throwable t) {
            return null;
        }

    }

}
