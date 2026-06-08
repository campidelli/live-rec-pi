package campidelli.liverecpi.recording.ports.inbound;

public interface StopRecordingUseCase {

    boolean stop(String audioSourceId);
}