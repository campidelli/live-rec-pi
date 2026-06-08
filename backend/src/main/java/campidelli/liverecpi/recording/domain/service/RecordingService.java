package campidelli.liverecpi.recording.domain.service;

import campidelli.liverecpi.recording.domain.model.AudioSource;
import campidelli.liverecpi.recording.domain.model.RecordingOptions;
import campidelli.liverecpi.recording.ports.inbound.GetAudioSourcesUseCase;
import campidelli.liverecpi.recording.ports.inbound.GetRecordingOptionsUseCase;
import campidelli.liverecpi.recording.ports.outbound.GetAudioSourcesPort;
import campidelli.liverecpi.recording.ports.outbound.GetRecordingOptionsPort;
import jakarta.inject.Singleton;

@Singleton
public class RecordingService implements GetRecordingOptionsUseCase, GetAudioSourcesUseCase {

    private final GetRecordingOptionsPort getRecordingOptionsPort;
    private final GetAudioSourcesPort getAudioSourcesPort;

    public RecordingService(
            GetRecordingOptionsPort getRecordingOptionsPort,
            GetAudioSourcesPort getAudioSourcesPort) {
        this.getRecordingOptionsPort = getRecordingOptionsPort;
        this.getAudioSourcesPort = getAudioSourcesPort;
    }

    @Override
    public RecordingOptions execute() {
        return getRecordingOptionsPort.getRecordingOptions();
    }

    @Override
    public AudioSource getAudioSource(String mixerType) {
        return getAudioSourcesPort.getAudioSources(mixerType).stream()
                .findFirst()
                .orElse(new AudioSource(mixerType, mixerType, "No matching audio source found", 0));
    }
}
