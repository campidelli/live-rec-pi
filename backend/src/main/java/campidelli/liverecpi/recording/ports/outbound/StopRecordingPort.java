package campidelli.liverecpi.recording.ports.outbound;

public interface StopRecordingPort {

    boolean stopRecording(String audioSourceId);
}