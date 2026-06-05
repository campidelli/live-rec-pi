package campidelli.liverecpi.application.mixer;

import java.util.List;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ConnectedMixerDTO(
    String id,
    String modelKey,
    String displayName,
    String ipAddress,
    int port,
    List<ChannelDTO> channels) {
}