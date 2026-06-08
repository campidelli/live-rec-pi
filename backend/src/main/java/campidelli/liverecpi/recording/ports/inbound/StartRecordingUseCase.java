package campidelli.liverecpi.recording.ports.inbound;

import campidelli.liverecpi.recording.domain.model.StartedRecording;

public interface StartRecordingUseCase {

    StartedRecording execute(StartRecordingCommand command);
}
