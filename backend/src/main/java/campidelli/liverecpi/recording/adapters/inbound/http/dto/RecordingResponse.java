package campidelli.liverecpi.recording.adapters.inbound.http.dto;

import campidelli.liverecpi.recording.domain.model.StartedRecording;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record RecordingResponse(String audioSourceId, String outputDir, String format, int trackCount) {

    public static RecordingResponse fromDomain(StartedRecording startedRecording) {
        return new RecordingResponse(
                startedRecording.audioSourceId(),
                startedRecording.outputDir(),
                startedRecording.format().name(),
                startedRecording.trackCount());
    }
}
