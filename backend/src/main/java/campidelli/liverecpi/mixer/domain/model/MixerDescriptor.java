package campidelli.liverecpi.mixer.domain.model;

public record MixerDescriptor(
        String type,
        String vendor,
        String name,
        int numberOfChannels) {

    public MixerDescriptor {
        if (numberOfChannels <= 0) {
            throw new IllegalArgumentException("numberOfChannels must be positive.");
        }
    }
}
