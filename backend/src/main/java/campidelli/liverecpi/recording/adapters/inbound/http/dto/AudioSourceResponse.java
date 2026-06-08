package campidelli.liverecpi.recording.adapters.inbound.http.dto;

import campidelli.liverecpi.recording.domain.model.AudioSource;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class AudioSourceResponse {

    private final String mixerType;
    private final String name;
    private final String description;
    private final int availableInputChannels;

    public AudioSourceResponse(String mixerType, String name, String description, int availableInputChannels) {
        this.mixerType = mixerType;
        this.name = name;
        this.description = description;
        this.availableInputChannels = availableInputChannels;
    }

    public String getMixerType() {
        return mixerType;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getAvailableInputChannels() {
        return availableInputChannels;
    }

    public static AudioSourceResponse fromDomain(AudioSource audioSource) {
        return new AudioSourceResponse(
                audioSource.mixerType(),
                audioSource.name(),
                audioSource.description(),
                audioSource.availableInputChannels());
    }
}
