package campidelli.liverecpi.recording.domain.service;

import campidelli.liverecpi.recording.domain.model.AudioSource;
import campidelli.liverecpi.recording.domain.model.StartedRecording;
import campidelli.liverecpi.recording.domain.model.RecordingOptions;
import campidelli.liverecpi.recording.ports.inbound.GetAudioSourcesUseCase;
import campidelli.liverecpi.recording.ports.inbound.GetRecordingOptionsUseCase;
import campidelli.liverecpi.recording.ports.inbound.StartRecordingCommand;
import campidelli.liverecpi.recording.ports.inbound.StartRecordingUseCase;
import campidelli.liverecpi.recording.ports.inbound.StopRecordingUseCase;
import campidelli.liverecpi.recording.ports.outbound.GetAudioSourcesPort;
import campidelli.liverecpi.recording.ports.outbound.GetRecordingOptionsPort;
import campidelli.liverecpi.recording.ports.outbound.StartRecordingPort;
import campidelli.liverecpi.recording.ports.outbound.StopRecordingPort;
import jakarta.inject.Singleton;

@Singleton
public class RecordingService implements GetRecordingOptionsUseCase, GetAudioSourcesUseCase, StartRecordingUseCase, StopRecordingUseCase {

    private final GetRecordingOptionsPort getRecordingOptionsPort;
    private final GetAudioSourcesPort getAudioSourcesPort;
    private final StartRecordingPort startRecordingPort;
    private final StopRecordingPort stopRecordingPort;

    public RecordingService(
            GetRecordingOptionsPort getRecordingOptionsPort,
            GetAudioSourcesPort getAudioSourcesPort,
            StartRecordingPort startRecordingPort,
            StopRecordingPort stopRecordingPort) {
        this.getRecordingOptionsPort = getRecordingOptionsPort;
        this.getAudioSourcesPort = getAudioSourcesPort;
        this.startRecordingPort = startRecordingPort;
        this.stopRecordingPort = stopRecordingPort;
    }

    @Override
    public RecordingOptions execute() {
        return getRecordingOptionsPort.getRecordingOptions();
    }

    @Override
    public AudioSource getAudioSource(String mixerType) {
        return getAudioSourcesPort.getAudioSources(mixerType).stream()
                .findFirst()
                .orElse(new AudioSource(mixerType, mixerType, mixerType, "No matching audio source found", 0));
    }

    @Override
    public StartedRecording execute(StartRecordingCommand command) {
        AudioSource audioSource = getAudioSourcesPort.getAudioSources(command.audioSourceId()).stream()
            .filter(source -> source.id().equals(command.audioSourceId()))
                .findFirst()
                .orElse(new AudioSource(
                command.audioSourceId(),
                command.audioSourceId(),
                command.audioSourceId(),
                        "No matching audio source found",
                        0));

        if (command.tracks().size() > audioSource.availableInputChannels()) {
            throw new IllegalArgumentException("Requested tracks exceed available input channels for audio source: "
                    + audioSource.id());
        }

        return startRecordingPort.startRecording(command);
    }

    @Override
    public boolean stop(String audioSourceId) {
        return stopRecordingPort.stopRecording(audioSourceId);
    }
}
