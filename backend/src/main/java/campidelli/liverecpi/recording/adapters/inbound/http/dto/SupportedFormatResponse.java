package campidelli.liverecpi.recording.adapters.inbound.http.dto;

import campidelli.liverecpi.recording.domain.model.SupportedFormat;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record SupportedFormatResponse(String extension, String description) {

    public static SupportedFormatResponse fromDomain(SupportedFormat supportedFormat) {
        return new SupportedFormatResponse(supportedFormat.getExtension(), supportedFormat.getDescription());
    }
}
