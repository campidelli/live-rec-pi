package campidelli.liverecpi.domain.mixer;

import java.util.List;

public record Mixer(
    String modelKey,
    String displayName,
    String ipAddress,
    int port,
    List<Channel> channels) {
}