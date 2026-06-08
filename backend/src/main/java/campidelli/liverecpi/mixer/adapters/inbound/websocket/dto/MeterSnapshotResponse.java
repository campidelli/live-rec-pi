package campidelli.liverecpi.mixer.adapters.inbound.websocket.dto;

import java.time.Instant;
import java.util.List;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record MeterSnapshotResponse(
        String mixerType,
        String ipAddress,
        int port,
        Instant capturedAt,
        List<MeterLevelResponse> levels) {
}