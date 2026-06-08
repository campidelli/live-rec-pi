package campidelli.liverecpi.recording.ports.outbound;

import campidelli.liverecpi.recording.domain.model.StartedRecording;
import campidelli.liverecpi.recording.ports.inbound.StartRecordingCommand;

public interface StartRecordingPort {

    StartedRecording startRecording(StartRecordingCommand command);
}
