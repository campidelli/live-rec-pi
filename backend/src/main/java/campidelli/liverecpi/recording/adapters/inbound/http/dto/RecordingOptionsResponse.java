package campidelli.liverecpi.recording.adapters.inbound.http.dto;

import java.util.List;

import campidelli.liverecpi.recording.domain.model.RecordingOptions;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record RecordingOptionsResponse(
        List<SupportedFormatResponse> supportedFormats,
        String sessionNamePrefix,
        String outputDir) {

    public RecordingOptionsResponse {
        supportedFormats = List.copyOf(supportedFormats);
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
