package com.devmod.client.vfx.effekseer.loader;

public class EffekLoadException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public EffekLoadException() {
    }

    public EffekLoadException(String message) {
        super(message);
    }

    public EffekLoadException(String message, Throwable cause) {
        super(message, cause);
    }

    public EffekLoadException(Throwable cause) {
        super(cause);
    }
}
