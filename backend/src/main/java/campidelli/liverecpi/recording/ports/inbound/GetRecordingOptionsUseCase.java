package campidelli.liverecpi.recording.ports.inbound;

import campidelli.liverecpi.recording.domain.model.RecordingOptions;

public interface GetRecordingOptionsUseCase {

    RecordingOptions execute();
}
