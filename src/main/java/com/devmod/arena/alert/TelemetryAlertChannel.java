package com.devmod.arena.alert;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.devmod.arena.telemetry.ArenaTelemetry;

public class TelemetryAlertChannel implements AlertRouter.AlertChannel {

    private final ArenaTelemetry telemetry;
    private final String id;
    private final boolean critical;

    public TelemetryAlertChannel(ArenaTelemetry telemetry) {
        this(telemetry, "telemetry", true);
    }

    public TelemetryAlertChannel(ArenaTelemetry telemetry, String id, boolean critical) {
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.id = Objects.requireNonNull(id, "id");
        this.critical = critical;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isCritical() {
        return critical;
    }

    @Override
    public boolean deliver(ErrorContext context) {
        if (context == null) {
            return false;
        }
        Map<String, Object> data = new HashMap<>(context.toMap());
        data.put("channelId", id);
        data.put("channelType", getType());
        telemetry.emit("arena.alert", data);
        return true;
    }
}
