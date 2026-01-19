package slimeknights.mantle.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.GsonHelper;
import slimeknights.mantle.Mantle;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Minimal JSON helpers needed by Mantle/TConstruct model utilities.
 */
public final class JsonHelper {
  private JsonHelper() {}

  /** Default GSON instance used by loaders. */
  public static final Gson DEFAULT_GSON = new GsonBuilder()
    .registerTypeAdapter(ResourceLocation.class, new ResourceLocation.Serializer())
    .setPrettyPrinting()
    .disableHtmlEscaping()
    .create();

  /** Gets an element from JSON, throwing if missing. */
  public static JsonElement getElement(JsonObject json, String memberName) {
    if (json.has(memberName)) {
      return json.get(memberName);
    }
    throw new JsonSyntaxException("Missing " + memberName);
  }

  /** Parses a list from a JSON array. */
  public static <T> List<T> parseList(JsonArray array, String name, BiFunction<JsonElement, String, T> mapper) {
    if (array.isEmpty()) {
      throw new JsonSyntaxException(name + " must have at least 1 element");
    }
    List<T> builder = new ArrayList<>(array.size());
    for (int i = 0; i < array.size(); i++) {
      builder.add(mapper.apply(array.get(i), name + "[" + i + "]"));
    }
    return List.copyOf(builder);
  }

  /** Parses a list from a JSON array in the given object. */
  public static <T> List<T> parseList(JsonObject parent, String name, BiFunction<JsonElement, String, T> mapper) {
    return parseList(GsonHelper.getAsJsonArray(parent, name), name, mapper);
  }

  /** Convenience overload for mapping objects. */
  public static <T> List<T> parseList(JsonArray array, String name, Function<JsonObject, T> mapper) {
    return parseList(array, name, (element, key) -> mapper.apply(GsonHelper.convertToJsonObject(element, key)));
  }

  /** Convenience overload for mapping objects. */
  public static <T> List<T> parseList(JsonObject parent, String name, Function<JsonObject, T> mapper) {
    return parseList(GsonHelper.getAsJsonArray(parent, name), name, mapper);
  }

  /** Parses a resource location from JSON. */
  public static ResourceLocation getResourceLocation(JsonObject json, String key) {
    String text = GsonHelper.getAsString(json, key);
    ResourceLocation location = ResourceLocation.tryParse(text);
    if (location == null) {
      throw new JsonSyntaxException("Expected " + key + " to be a resource location, was '" + text + "'");
    }
    return location;
  }

  /** Parses a resource location from JSON, returning fallback if missing. */
  @Nullable
  public static ResourceLocation getResourceLocation(JsonObject json, String key, @Nullable ResourceLocation fallback) {
    if (json.has(key)) {
      return getResourceLocation(json, key);
    }
    return fallback;
  }

  /** Converts a JSON element to a resource location. */
  public static ResourceLocation convertToResourceLocation(JsonElement json, String key) {
    String text = GsonHelper.convertToString(json, key);
    ResourceLocation location = ResourceLocation.tryParse(text);
    if (location == null) {
      throw new JsonSyntaxException("Expected " + key + " to be a resource location, was '" + text + "'");
    }
    return location;
  }

  /** Converts the resource into a JSON object, logging failures. */
  @Nullable
  public static JsonObject getJson(Resource resource, ResourceLocation location) {
    try (Reader reader = resource.openAsReader()) {
      return GsonHelper.parse(reader);
    } catch (JsonParseException | IOException e) {
      Mantle.logger.error("Failed to load JSON from resource {} from pack '{}'", location, resource.sourcePackId(), e);
      return null;
    }
  }

  /** Loads a JSON file from all namespaces and packs, ordered by pack priority. */
  public static List<JsonObject> getFileInAllDomainsAndPacks(ResourceManager manager, String path, @Nullable String preferredPath) {
    return manager.getNamespaces().stream()
      .filter(ResourceLocation::isValidNamespace)
      .flatMap(namespace -> {
        ResourceLocation location = ResourceLocation.fromNamespaceAndPath(namespace, path);
        return manager.getResourceStack(location).stream()
          .map(preferredPath != null ? resource -> {
            Mantle.logger.warn("Using deprecated path {} in pack {} - use {}:{} instead", location, resource.sourcePackId(), location.getNamespace(), preferredPath);
            return getJson(resource, location);
          } : resource -> getJson(resource, location));
      }).filter(Objects::nonNull).toList();
  }

  /** Localizes a resource path into a folder/extension pair. */
  public static String localize(String path, String folder, String extension) {
    return path.substring(folder.length() + 1, path.length() - extension.length());
  }

  /** Localizes a resource location into a folder/extension pair. */
  public static ResourceLocation localize(ResourceLocation location, String folder, String extension) {
    return location.withPath(localize(location.getPath(), folder, extension));
  }

  /** Wraps the given resource location in the given prefix and suffix. */
  public static ResourceLocation wrap(ResourceLocation location, String prefix, String suffix) {
    return location.withPath(prefix + location.getPath() + suffix);
  }
}
