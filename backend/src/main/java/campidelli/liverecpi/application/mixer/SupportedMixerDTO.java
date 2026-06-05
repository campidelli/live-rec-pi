package campidelli.liverecpi.application.mixer;

import java.util.Optional;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record SupportedMixerDTO(
        String id,
        String modelKey,
        String displayName,
        Optional<String> ipAddress,
        Optional<Integer> port) {

    public boolean isOnline() {
        return ipAddress.isPresent() && port.isPresent();
    }
}