package com.ird0.utilities.datagen;

import com.ird0.directory.model.DirectoryEntry;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * CLI utility to generate fake policyholder data as CSV.
 *
 * <p>Usage: java -jar policyholder-data-generator.jar java -jar policyholder-data-generator.jar 500
 * java -jar policyholder-data-generator.jar 100 -o custom-output.csv
 */
@Command(
    name = "data-generator",
    mixinStandardHelpOptions = true,
    version = "1.0.0",
    description = "Generates fake policyholder data compatible with DirectoryEntry model")
public class DataGeneratorCLI implements Callable<Integer> {

  @Parameters(
      index = "0",
      description = "Number of records to generate (default: 100)",
      paramLabel = "COUNT",
      defaultValue = "100",
      arity = "0..1")
  private int recordCount;

  @Option(
      names = {"-o", "--output"},
      description = "Output CSV file path (default: policyholders.csv)",
      paramLabel = "FILE",
      defaultValue = "policyholders.csv")
  private String outputFile;

  @Override
  public Integer call() {
    try {
      // Validate input
      if (recordCount <= 0) {
        System.err.println("Error: Record count must be positive");
        return 1;
      }

      System.out.println("Generating " + recordCount + " policyholder records...");

      // Generate data
      PolicyholderDataGenerator generator = new PolicyholderDataGenerator();
      List<DirectoryEntry> records = generator.generateRecords(recordCount);

      // Write to CSV
      writeToCSV(records, outputFile);

      System.out.println("Successfully generated " + recordCount + " records");
      System.out.println("Output file: " + outputFile);

      return 0;

    } catch (IOException e) {
      System.err.println("Error writing CSV file: " + e.getMessage());
      return 1;
    } catch (Exception e) {
      System.err.println("Unexpected error: " + e.getMessage());
      e.printStackTrace();
      return 1;
    }
  }

  /** Writes policyholder records to CSV file with proper formatting. */
  private void writeToCSV(List<DirectoryEntry> records, String filePath) throws IOException {

    CSVFormat csvFormat =
        CSVFormat.DEFAULT
            .builder()
            .setHeader("name", "type", "email", "phone", "address", "additionalInfo")
            .build();

    try (FileWriter writer = new FileWriter(filePath);
        CSVPrinter csvPrinter = new CSVPrinter(writer, csvFormat)) {

      for (DirectoryEntry record : records) {
        csvPrinter.printRecord(
            record.getName(),
            record.getType(),
            record.getEmail(),
            record.getPhone(),
            record.getAddress(),
            record.getAdditionalInfo());
      }

      csvPrinter.flush();
    }
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new DataGeneratorCLI()).execute(args);
    System.exit(exitCode);
  }
}
