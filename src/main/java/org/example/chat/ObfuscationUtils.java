package org.example.chat;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class ObfuscationUtils {

  // Символы, которые не должны искажаться (пунктуация и пробелы)
  private static final Set<Character> PRESERVED_CHARS =
      Set.of(' ', '.', ',', '!', '?', ':', ';', '-', '"', '\'', '(', ')', '[', ']', '{', '}', '\n');

  /**
   * Искажает текст сообщения в зависимости от расстояния.
   *
   * @param originalText Исходный текст сообщения.
   * @param distance Текущее расстояние до слушателя.
   * @param radius Радиус 100% четкой слышимости.
   * @param falloff Зона неразборчивости после радиуса.
   * @param obfuscationChars Символы для замены (помехи).
   * @return Искаженный текст или null, если игрок слишком далеко.
   */
  public static String obfuscate(
      String originalText,
      double distance,
      double radius,
      double falloff,
      List<String> obfuscationChars) {

    if (distance <= radius) {
      return originalText;
    }

    if (distance >= radius + falloff) {
      return null; // Сообщение не слышно
    }

    if (obfuscationChars == null || obfuscationChars.isEmpty()) {
      return originalText;
    }

    // Вероятность замены символа (от 0.0 на границе radius до 1.0 на границе falloff)
    double obfuscationChance = (distance - radius) / falloff;
    StringBuilder result = new StringBuilder(originalText.length());
    ThreadLocalRandom random = ThreadLocalRandom.current();

    for (int i = 0; i < originalText.length(); i++) {
      char c = originalText.charAt(i);

      if (PRESERVED_CHARS.contains(c)) {
        result.append(c);
      } else if (random.nextDouble() < obfuscationChance) {
        String randomObfChar = obfuscationChars.get(random.nextInt(obfuscationChars.size()));
        result.append(randomObfChar);
      } else {
        result.append(c);
      }
    }

    return result.toString();
  }
}
