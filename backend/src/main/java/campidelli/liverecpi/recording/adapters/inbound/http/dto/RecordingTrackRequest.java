package campidelli.liverecpi.recording.adapters.inbound.http.dto;

import campidelli.liverecpi.recording.domain.model.RecordingTrack;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record RecordingTrackRequest(int audioSourceIndex, String name) {

    public RecordingTrack toDomain() {
        return new RecordingTrack(audioSourceIndex, name);
    }
}
