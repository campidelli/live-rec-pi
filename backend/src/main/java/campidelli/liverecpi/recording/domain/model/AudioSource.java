package campidelli.liverecpi.recording.domain.model;

public record AudioSource(
        String id,
        String mixerType,
        String name,
        String description,
        int availableInputChannels) {
}
