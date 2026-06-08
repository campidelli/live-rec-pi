package campidelli.liverecpi.recording.domain.model;

public record StartedRecording(
        String audioSourceId,
        String outputDir,
        SupportedFormat format,
        int trackCount) {
}
