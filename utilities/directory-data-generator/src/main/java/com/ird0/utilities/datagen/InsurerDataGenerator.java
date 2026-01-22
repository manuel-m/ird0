package com.ird0.utilities.datagen;

import com.ird0.directory.model.DirectoryEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.datafaker.Faker;

/** Generates realistic fake insurer data using Datafaker. */
public class InsurerDataGenerator {

  private static final String TYPE_HEALTH = "health";
  private static final String TYPE_AUTO = "auto";
  private static final String TYPE_LIFE = "life";
  private static final String TYPE_PROPERTY = "property";
  private static final String TYPE_MULTI_LINE = "multi-line";
  private static final String[] TYPES = {
    TYPE_HEALTH, TYPE_AUTO, TYPE_LIFE, TYPE_PROPERTY, TYPE_MULTI_LINE
  };

  private static final String[] HEALTH_PREFIXES = {
    "BlueCross", "United", "Aetna", "Cigna", "Humana", "Kaiser", "Anthem", "HealthNet"
  };
  private static final String[] AUTO_PREFIXES = {
    "SafeDrive", "AutoGuard", "RoadShield", "DriveSecure", "AutoProtect", "CarSafe"
  };
  private static final String[] LIFE_PREFIXES = {
    "Evergreen", "Eternal", "Legacy", "Horizon", "LifeGuard", "SecureLife"
  };
  private static final String[] PROPERTY_PREFIXES = {
    "HomeGuard", "PropertyShield", "SafeHome", "EstateProtect", "HomeSafe"
  };
  private static final String[] MULTI_LINE_SUFFIXES = {
    "Insurance Group", "Financial Services", "Insurance Holdings", "Risk Management"
  };

  private static final String[] TOLL_FREE_PREFIXES = {"800", "888", "877", "866", "855", "844"};
  private static final String[] RATINGS = {"A++", "A+", "A", "A-", "B++", "B+"};

  private final Faker faker;
  private final Random random;

  public InsurerDataGenerator() {
    this.faker = new Faker();
    this.random = new Random();
  }

  /**
   * Generates a list of fake insurer records.
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

  /** Generates a single insurer record with realistic data. */
  private DirectoryEntry generateSingleRecord() {
    String type = selectRandomType();
    String name = generateNameByType(type);
    String slug = generateSlug(name);

    DirectoryEntry entry = new DirectoryEntry();
    entry.setName(name);
    entry.setType(type);
    entry.setEmail(generateEmail(slug));
    entry.setPhone(generateTollFreePhone());
    entry.setAddress(generateBusinessAddress());
    entry.setAdditionalInfo(generateAdditionalInfo(type));
    entry.setWebhookUrl(generateWebhookUrl(slug));

    return entry;
  }

  private String selectRandomType() {
    return TYPES[random.nextInt(TYPES.length)];
  }

  private String generateNameByType(String type) {
    return switch (type) {
      case TYPE_HEALTH -> selectRandom(HEALTH_PREFIXES) + " Health Insurance";
      case TYPE_AUTO -> selectRandom(AUTO_PREFIXES) + " Auto Insurance";
      case TYPE_LIFE -> selectRandom(LIFE_PREFIXES) + " Life Assurance";
      case TYPE_PROPERTY -> selectRandom(PROPERTY_PREFIXES) + " Property Insurance";
      case TYPE_MULTI_LINE -> faker.company().name() + " " + selectRandom(MULTI_LINE_SUFFIXES);
      default -> faker.company().name() + " Insurance";
    };
  }

  private String selectRandom(String[] array) {
    return array[random.nextInt(array.length)];
  }

  private String generateSlug(String name) {
    return name.toLowerCase().replace(" ", "-").replaceAll("[^a-z0-9-]", "").replaceAll("-+", "-");
  }

  private String generateEmail(String slug) {
    return "contact@" + slug + ".com";
  }

  private String generateTollFreePhone() {
    String prefix = selectRandom(TOLL_FREE_PREFIXES);
    return String.format("1-%s-%03d-%04d", prefix, random.nextInt(1000), random.nextInt(10000));
  }

  private String generateBusinessAddress() {
    return String.format(
        "%d %s, Suite %d, %s, %s %s",
        faker.number().numberBetween(100, 9999),
        faker.address().streetName(),
        faker.number().numberBetween(100, 999),
        faker.address().city(),
        faker.address().stateAbbr(),
        faker.address().zipCode());
  }

  private String generateAdditionalInfo(String type) {
    String rating = selectRandom(RATINGS);
    return switch (type) {
      case TYPE_HEALTH ->
          String.format(
              "Network: %s, Plans: %d, Rating: %s",
              random.nextBoolean() ? "PPO" : "HMO", random.nextInt(20) + 5, rating);
      case TYPE_AUTO ->
          String.format("States covered: %d, Rating: %s", random.nextInt(40) + 10, rating);
      case TYPE_LIFE ->
          String.format("Founded: %d, Rating: %s", random.nextInt(100) + 1920, rating);
      case TYPE_PROPERTY ->
          String.format("Annual claims: %d, Rating: %s", (random.nextInt(100) + 1) * 1000, rating);
      case TYPE_MULTI_LINE ->
          String.format(
              "Lines: Health, Auto, Life, Employees: %d", (random.nextInt(50) + 1) * 1000);
      default -> "Rating: " + rating;
    };
  }

  private String generateWebhookUrl(String slug) {
    return "https://" + slug + ".insurance-api.com/webhooks/claims";
  }
}
