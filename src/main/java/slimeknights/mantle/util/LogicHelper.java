package slimeknights.mantle.util;

import java.util.List;

/**
 * Minimal logic helpers used by model utilities.
 */
public final class LogicHelper {
  private LogicHelper() {}

  public static <E> E getOrDefault(List<E> list, int index, E defaultValue) {
    if (index < 0 || index >= list.size()) {
      return defaultValue;
    }
    return list.get(index);
  }
}
