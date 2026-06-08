package campidelli.liverecpi.recording.adapters.inbound.http.dto;

import java.util.List;

import campidelli.liverecpi.recording.domain.model.RecordingOptions;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class RecordingOptionsResponse {

    private final List<SupportedFormatResponse> supportedFormats;
    private final String sessionNamePrefix;
    private final String outputDir;

    public RecordingOptionsResponse(
            List<SupportedFormatResponse> supportedFormats,
            String sessionNamePrefix,
            String outputDir) {
        this.supportedFormats = supportedFormats;
        this.sessionNamePrefix = sessionNamePrefix;
        this.outputDir = outputDir;
    }

    public List<SupportedFormatResponse> getSupportedFormats() {
        return supportedFormats;
    }

    public String getSessionNamePrefix() {
        return sessionNamePrefix;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public static RecordingOptionsResponse fromDomain(RecordingOptions recordingOptions) {
        List<SupportedFormatResponse> supportedFormats = recordingOptions.supportedFormats().stream()
                .map(SupportedFormatResponse::fromDomain)
                .toList();

        return new RecordingOptionsResponse(
                supportedFormats,
                recordingOptions.sessionNamePrefix(),
                recordingOptions.outputDir());
    }
}
