package campidelli.liverecpi.domain.mixer;

import java.util.List;
import java.util.Optional;

public record Mixer(
    String modelKey,
    String name,
    String firmwareVersion,
    String ipAddress,
    int port,
    Optional<List<Channel>> channels) {

    public String id() {
        return String.join("|", modelKey, ipAddress);
    }
}