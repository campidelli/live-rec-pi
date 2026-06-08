package campidelli.liverecpi.recording.domain.service;

import campidelli.liverecpi.recording.domain.model.RecordingOptions;
import campidelli.liverecpi.recording.ports.inbound.GetRecordingOptionsUseCase;
import campidelli.liverecpi.recording.ports.outbound.GetRecordingOptionsPort;
import jakarta.inject.Singleton;

@Singleton
public class RecordingService implements GetRecordingOptionsUseCase {

    private final GetRecordingOptionsPort getRecordingOptionsPort;

    public RecordingService(GetRecordingOptionsPort getRecordingOptionsPort) {
        this.getRecordingOptionsPort = getRecordingOptionsPort;
    }

    @Override
    public RecordingOptions execute() {
        return getRecordingOptionsPort.getRecordingOptions();
    }
}
