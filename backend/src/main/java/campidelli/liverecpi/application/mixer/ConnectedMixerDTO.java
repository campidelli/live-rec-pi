package campidelli.liverecpi.application.mixer;

import java.util.List;

import io.micronaut.core.annotation.Introspected;

@Introspected
public record ConnectedMixerDTO(
    String id,
    String modelKey,
    String displayName,
    String ipAddress,
    int port,
    List<ChannelDTO> channels) {
}