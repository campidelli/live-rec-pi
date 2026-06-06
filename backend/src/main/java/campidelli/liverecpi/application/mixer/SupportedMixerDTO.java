package campidelli.liverecpi.application.mixer;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record SupportedMixerDTO(
        String id,
        String name,
        Optional<String> ipAddress,
        Optional<Integer> port) {

    @JsonProperty("online")
    public boolean online() {
        return ipAddress.isPresent() && port.isPresent();
    }
}