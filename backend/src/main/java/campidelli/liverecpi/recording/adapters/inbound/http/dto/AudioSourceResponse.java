package campidelli.liverecpi.recording.adapters.inbound.http.dto;

import campidelli.liverecpi.recording.domain.model.AudioSource;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record AudioSourceResponse(
        String id,
        String mixerType,
        String name,
        String description,
        int availableInputChannels) {

    public static AudioSourceResponse fromDomain(AudioSource audioSource) {
        return new AudioSourceResponse(
                audioSource.id(),
                audioSource.mixerType(),
                audioSource.name(),
                audioSource.description(),
                audioSource.availableInputChannels());
    }
}
