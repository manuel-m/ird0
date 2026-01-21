package com.ird0.utilities.datagen;

import com.ird0.directory.model.DirectoryEntry;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * CLI utility to generate fake directory data as CSV.
 *
 * <p>Usage: java -jar directory-data-generator.jar java -jar directory-data-generator.jar 500
 * java -jar directory-data-generator.jar 100 -o custom-output.csv java -jar
 * directory-data-generator.jar 100 -e INSURER
 */
@Slf4j
@Command(
    name = "data-generator",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "Generates fake directory data compatible with DirectoryEntry model")
@SuppressWarnings("java:S106") // CLI tool intentionally uses System.out/System.err
public class DataGeneratorCLI implements Callable<Integer> {

  /** Entity types supported by the data generator. */
  public enum EntityType {
    POLICYHOLDER,
    INSURER
  }

  @Parameters(
      index = "0",
      description = "Number of records to generate (default: 100)",
      paramLabel = "COUNT",
      defaultValue = "100",
      arity = "0..1")
  private int recordCount;

  @Option(
      names = {"-e", "--entity-type"},
      description = "Entity type to generate: ${COMPLETION-CANDIDATES} (default: POLICYHOLDER)",
      paramLabel = "TYPE",
      defaultValue = "POLICYHOLDER")
  private EntityType entityType;

  @Option(
      names = {"-o", "--output"},
      description = "Output CSV file path (default: based on entity type)",
      paramLabel = "FILE")
  private String outputFile;

  @Override
  public Integer call() {
    try {
      // Validate input
      if (recordCount <= 0) {
        System.err.println("Error: Record count must be positive");
        return 1;
      }

      // Determine output file based on entity type if not specified
      String targetFile = outputFile != null ? outputFile : getDefaultOutputFile();

      System.out.println(
          "Generating " + recordCount + " " + entityType.name().toLowerCase() + " records...");

      // Generate data based on entity type
      List<DirectoryEntry> records = generateRecords();

      // Write to CSV
      writeToCSV(records, targetFile);

      System.out.println("Successfully generated " + recordCount + " records");
      System.out.println("Output file: " + targetFile);

      return 0;

    } catch (IOException e) {
      System.err.println("Error writing CSV file: " + e.getMessage());
      return 1;
    } catch (Exception e) {
      log.error("Unexpected error", e);
      return 1;
    }
  }

  private String getDefaultOutputFile() {
    return switch (entityType) {
      case POLICYHOLDER -> "policyholders.csv";
      case INSURER -> "insurers.csv";
    };
  }

  private List<DirectoryEntry> generateRecords() {
    return switch (entityType) {
      case POLICYHOLDER -> new PolicyholderDataGenerator().generateRecords(recordCount);
      case INSURER -> new InsurerDataGenerator().generateRecords(recordCount);
    };
  }

  /** Writes directory entry records to CSV file with proper formatting. */
  private void writeToCSV(List<DirectoryEntry> records, String filePath) throws IOException {

    CSVFormat csvFormat =
        CSVFormat.DEFAULT
            .builder()
            .setHeader("name", "type", "email", "phone", "address", "additionalInfo", "webhookUrl")
            .build();

    try (FileWriter writer = new FileWriter(filePath);
        CSVPrinter csvPrinter = new CSVPrinter(writer, csvFormat)) {

      for (DirectoryEntry entry : records) {
        csvPrinter.printRecord(
            entry.getName(),
            entry.getType(),
            entry.getEmail(),
            entry.getPhone(),
            entry.getAddress(),
            entry.getAdditionalInfo(),
            entry.getWebhookUrl());
      }

      csvPrinter.flush();
    }
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new DataGeneratorCLI()).execute(args);
    System.exit(exitCode);
  }
}
