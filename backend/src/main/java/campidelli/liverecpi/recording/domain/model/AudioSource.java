package campidelli.liverecpi.recording.domain.model;

public record AudioSource(
        String mixerType,
        String name,
        String description,
        int availableInputChannels) {
}
