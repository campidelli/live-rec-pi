package campidelli.liverecpi.recording.ports.outbound;

import java.util.List;

import campidelli.liverecpi.recording.domain.model.AudioSource;

public interface GetAudioSourcesPort {

    List<AudioSource> getAudioSources(String mixerType);
}
