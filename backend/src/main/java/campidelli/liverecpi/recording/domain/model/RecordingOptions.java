package campidelli.liverecpi.recording.domain.model;

import java.util.List;

public record RecordingOptions(
        List<SupportedFormat> supportedFormats,
        String sessionNamePrefix,
        String outputDir) {

    public RecordingOptions {
        supportedFormats = List.copyOf(supportedFormats);
    }
}
