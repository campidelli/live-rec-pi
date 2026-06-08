package campidelli.liverecpi.recording.adapters.inbound.http;

import campidelli.liverecpi.recording.adapters.inbound.http.dto.AudioSourceResponse;
import campidelli.liverecpi.recording.adapters.inbound.http.dto.RecordingOptionsResponse;
import campidelli.liverecpi.recording.ports.inbound.GetAudioSourcesUseCase;
import campidelli.liverecpi.recording.ports.inbound.GetRecordingOptionsUseCase;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;

@Controller("/recording")
public class RecordingController {

    private final GetRecordingOptionsUseCase getRecordingOptionsUseCase;
    private final GetAudioSourcesUseCase getAudioSourcesUseCase;

    public RecordingController(
            GetRecordingOptionsUseCase getRecordingOptionsUseCase,
            GetAudioSourcesUseCase getAudioSourcesUseCase) {
        this.getRecordingOptionsUseCase = getRecordingOptionsUseCase;
        this.getAudioSourcesUseCase = getAudioSourcesUseCase;
    }

    @Get("/options")
    public RecordingOptionsResponse getRecordingOptions() {
        return RecordingOptionsResponse.fromDomain(getRecordingOptionsUseCase.execute());
    }

    @Get("/source/{mixerType}")
    public AudioSourceResponse getAudioSource(@PathVariable String mixerType) {
        return AudioSourceResponse.fromDomain(getAudioSourcesUseCase.getAudioSource(mixerType));
    }
}
