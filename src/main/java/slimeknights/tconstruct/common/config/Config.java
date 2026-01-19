package slimeknights.tconstruct.common.config;

/** Minimal config shim for client-side model systems. */
public final class Config {
  public static final Client CLIENT = new Client();
  public static final Common COMMON = new Common();

  private Config() {}

  public static final class Client {
    public final BooleanValue logMissingMaterialTextures = new BooleanValue(false);
    public final BooleanValue logMissingModifierTextures = new BooleanValue(false);
    public final BooleanValue tankFluidModel = new BooleanValue(true);
  }

  public static final class Common {
    public final EnumValue<LogInvalidToolStack> logInvalidToolStack = new EnumValue<>(LogInvalidToolStack.WARNING);
  }

  public enum LogInvalidToolStack {
    NONE,
    WARNING,
    STACKTRACE
  }

  public static final class BooleanValue {
    private final boolean value;

    public BooleanValue(boolean value) {
      this.value = value;
    }

    public boolean get() {
      return value;
    }
  }

  public static final class EnumValue<T extends Enum<T>> {
    private final T value;

    public EnumValue(T value) {
      this.value = value;
    }

    public T get() {
      return value;
    }
  }
}
