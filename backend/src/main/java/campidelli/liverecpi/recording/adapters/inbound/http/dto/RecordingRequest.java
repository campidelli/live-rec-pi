package campidelli.liverecpi.recording.adapters.inbound.http.dto;

import java.util.List;

import campidelli.liverecpi.recording.domain.model.SupportedFormat;
import campidelli.liverecpi.recording.ports.inbound.StartRecordingCommand;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record RecordingRequest(
        String audioSourceId,
        String outputDir,
        SupportedFormat format,
        List<RecordingTrackRequest> tracks) {

    public RecordingRequest {
        tracks = List.copyOf(tracks);
    }

    public StartRecordingCommand toCommand() {
        return new StartRecordingCommand(
                audioSourceId,
                outputDir,
                format,
                tracks.stream().map(RecordingTrackRequest::toDomain).toList());
    }
}
