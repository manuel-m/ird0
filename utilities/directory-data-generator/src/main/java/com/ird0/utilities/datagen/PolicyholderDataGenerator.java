package com.ird0.utilities.datagen;

import com.ird0.directory.model.DirectoryEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.datafaker.Faker;

/** Generates realistic fake policyholder data using Datafaker. */
public class PolicyholderDataGenerator {

  private static final String TYPE_INDIVIDUAL = "individual";
  private static final String TYPE_FAMILY = "family";
  private static final String TYPE_CORPORATE = "corporate";
  private static final String[] TYPES = {TYPE_INDIVIDUAL, TYPE_FAMILY, TYPE_CORPORATE};
  private final Faker faker;
  private final Random random;

  public PolicyholderDataGenerator() {
    this.faker = new Faker();
    this.random = new Random();
  }

  /**
   * Generates a list of fake policyholder records.
   *
   * @param count Number of records to generate
   * @return List of DirectoryEntry objects
   */
  public List<DirectoryEntry> generateRecords(int count) {
    List<DirectoryEntry> records = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      records.add(generateSingleRecord());
    }
    return records;
  }

  /** Generates a single policyholder record with realistic data. */
  private DirectoryEntry generateSingleRecord() {
    String type = selectRandomType();
    String name = generateNameByType(type);

    DirectoryEntry entry = new DirectoryEntry();
    entry.setName(name);
    entry.setType(type);
    entry.setEmail(generateEmail(name));
    entry.setPhone(faker.phoneNumber().cellPhone());
    entry.setAddress(faker.address().fullAddress());
    entry.setAdditionalInfo(generateAdditionalInfo(type));

    return entry;
  }

  private String selectRandomType() {
    return TYPES[random.nextInt(TYPES.length)];
  }

  private String generateNameByType(String type) {
    return switch (type) {
      case TYPE_INDIVIDUAL -> faker.name().fullName();
      case TYPE_FAMILY -> faker.name().lastName() + " Family";
      case TYPE_CORPORATE -> faker.company().name();
      default -> faker.name().fullName();
    };
  }

  private String generateEmail(String name) {
    // Generate email based on name (lowercase, no spaces)
    String emailName = name.toLowerCase().replace(" family", "").replace(" ", ".");
    return emailName + "@" + faker.internet().domainName();
  }

  private String generateAdditionalInfo(String type) {
    return switch (type) {
      case TYPE_INDIVIDUAL -> "Account since " + faker.date().birthday().toString();
      case TYPE_FAMILY -> "Members: " + (random.nextInt(5) + 2);
      case TYPE_CORPORATE ->
          "Industry: " + faker.company().industry() + ", Employees: " + (random.nextInt(500) + 10);
      default -> "";
    };
  }
}
