package campidelli.liverecpi.recording.domain.model;

public enum SupportedFormat {
    WAV("wav", "Waveform Audio File Format");

    private final String extension;
    private final String description;

    SupportedFormat(String extension, String description) {
        this.extension = extension;
        this.description = description;
    }

    public String getExtension() {
        return extension;
    }

    public String getDescription() {
        return description;
    }
}
