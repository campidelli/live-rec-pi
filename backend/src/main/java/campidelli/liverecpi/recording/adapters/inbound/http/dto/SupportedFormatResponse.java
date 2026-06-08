package campidelli.liverecpi.recording.adapters.inbound.http.dto;

import campidelli.liverecpi.recording.domain.model.SupportedFormat;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class SupportedFormatResponse {

    private final String extension;
    private final String description;

    public SupportedFormatResponse(String extension, String description) {
        this.extension = extension;
        this.description = description;
    }

    public String getExtension() {
        return extension;
    }

    public String getDescription() {
        return description;
    }

    public static SupportedFormatResponse fromDomain(SupportedFormat supportedFormat) {
        return new SupportedFormatResponse(supportedFormat.getExtension(), supportedFormat.getDescription());
    }
}
