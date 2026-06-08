package campidelli.liverecpi.recording.ports.inbound;

import campidelli.liverecpi.recording.domain.model.AudioSource;

public interface GetAudioSourcesUseCase {

    AudioSource getAudioSource(String mixerType);
}
