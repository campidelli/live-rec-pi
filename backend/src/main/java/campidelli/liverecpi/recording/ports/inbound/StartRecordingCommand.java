package campidelli.liverecpi.recording.ports.inbound;

import java.util.List;

import campidelli.liverecpi.recording.domain.model.RecordingTrack;
import campidelli.liverecpi.recording.domain.model.SupportedFormat;

public record StartRecordingCommand(
        String audioSourceId,
        String outputDir,
        SupportedFormat format,
        List<RecordingTrack> tracks) {

    public StartRecordingCommand {
        tracks = List.copyOf(tracks);
    }
}
