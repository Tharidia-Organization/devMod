package slimeknights.mantle.client.render;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.Direction;
import net.minecraft.util.GsonHelper;
import org.joml.Vector3f;
import slimeknights.mantle.client.model.util.ModelHelper;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;
import java.util.Map.Entry;

/** Simple fluid cuboid used by tank-like models. */
public class FluidCuboid {
  private static final Map<Direction, FluidFace> DEFAULT_FACES;
  static {
    DEFAULT_FACES = new EnumMap<>(Direction.class);
    for (Direction direction : Direction.values()) {
      DEFAULT_FACES.put(direction, FluidFace.NORMAL);
    }
  }

  private final Vector3f from;
  private final Vector3f to;
  private final Map<Direction, FluidFace> faces;

  @Nullable
  private Vector3f fromScaled;
  @Nullable
  private Vector3f toScaled;

  public FluidCuboid(Vector3f from, Vector3f to, Map<Direction, FluidFace> faces) {
    this.from = from;
    this.to = to;
    this.faces = faces;
  }

  public Vector3f getFrom() {
    return from;
  }

  public Vector3f getTo() {
    return to;
  }

  public Map<Direction, FluidFace> getFaces() {
    return faces;
  }

  @Nullable
  public FluidFace getFace(Direction face) {
    return faces.get(face);
  }

  public Vector3f getFromScaled() {
    if (fromScaled == null) {
      fromScaled = new Vector3f(from);
      fromScaled.mul(1 / 16f);
    }
    return fromScaled;
  }

  public Vector3f getToScaled() {
    if (toScaled == null) {
      toScaled = new Vector3f(to);
      toScaled.mul(1 / 16f);
    }
    return toScaled;
  }

  /** Represents a single fluid face in the model. */
  public record FluidFace(boolean isFlowing, int rotation) {
    public static final FluidFace NORMAL = new FluidFace(false, 0);

    public FluidFace {
      if (!ModelHelper.checkRotation(rotation)) {
        throw new IllegalArgumentException("Rotation must be 0/90/180/270");
      }
    }
  }

  /** Parses a fluid cuboid from JSON. */
  public static FluidCuboid fromJson(JsonObject json) {
    Vector3f from = readVec3(json, "from");
    Vector3f to = readVec3(json, "to");

    Map<Direction, FluidFace> faces = new EnumMap<>(Direction.class);
    if (json.has("faces")) {
      JsonObject facesJson = GsonHelper.getAsJsonObject(json, "faces");
      for (Entry<String, JsonElement> entry : facesJson.entrySet()) {
        Direction direction = Direction.byName(entry.getKey());
        if (direction == null) {
          continue;
        }
        JsonObject faceJson = GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey());
        boolean flowing = GsonHelper.getAsBoolean(faceJson, "flowing", false);
        int rotation = GsonHelper.getAsInt(faceJson, "rotation", 0);
        faces.put(direction, new FluidFace(flowing, rotation));
      }
    }
    if (faces.isEmpty()) {
      faces = DEFAULT_FACES;
    }
    return new FluidCuboid(from, to, faces);
  }

  private static Vector3f readVec3(JsonObject json, String key) {
    var array = GsonHelper.getAsJsonArray(json, key);
    if (array.size() != 3) {
      throw new IllegalArgumentException("Expected " + key + " to have 3 elements");
    }
    float x = GsonHelper.convertToFloat(array.get(0), key + "[0]");
    float y = GsonHelper.convertToFloat(array.get(1), key + "[1]");
    float z = GsonHelper.convertToFloat(array.get(2), key + "[2]");
    return new Vector3f(x, y, z);
  }
}
