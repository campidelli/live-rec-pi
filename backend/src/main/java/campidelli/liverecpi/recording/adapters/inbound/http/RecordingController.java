package campidelli.liverecpi.recording.adapters.inbound.http;

import campidelli.liverecpi.recording.adapters.inbound.http.dto.AudioSourceResponse;
import campidelli.liverecpi.recording.adapters.inbound.http.dto.RecordingRequest;
import campidelli.liverecpi.recording.adapters.inbound.http.dto.RecordingOptionsResponse;
import campidelli.liverecpi.recording.adapters.inbound.http.dto.RecordingResponse;
import campidelli.liverecpi.recording.ports.inbound.GetAudioSourcesUseCase;
import campidelli.liverecpi.recording.ports.inbound.GetRecordingOptionsUseCase;
import campidelli.liverecpi.recording.ports.inbound.StartRecordingUseCase;
import campidelli.liverecpi.recording.ports.inbound.StopRecordingUseCase;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;

@Controller("/recording")
public class RecordingController {

    private final GetRecordingOptionsUseCase getRecordingOptionsUseCase;
    private final GetAudioSourcesUseCase getAudioSourcesUseCase;
    private final StartRecordingUseCase startRecordingUseCase;
    private final StopRecordingUseCase stopRecordingUseCase;

    public RecordingController(
            GetRecordingOptionsUseCase getRecordingOptionsUseCase,
            GetAudioSourcesUseCase getAudioSourcesUseCase,
            StartRecordingUseCase startRecordingUseCase,
            StopRecordingUseCase stopRecordingUseCase) {
        this.getRecordingOptionsUseCase = getRecordingOptionsUseCase;
        this.getAudioSourcesUseCase = getAudioSourcesUseCase;
        this.startRecordingUseCase = startRecordingUseCase;
        this.stopRecordingUseCase = stopRecordingUseCase;
    }

    @Post
    public RecordingResponse startRecording(@Body RecordingRequest request) {
        return RecordingResponse.fromDomain(startRecordingUseCase.execute(request.toCommand()));
    }

    @Delete("/{audioSourceId}")
    public HttpResponse<?> stopRecording(@PathVariable String audioSourceId) {
        if (!stopRecordingUseCase.stop(audioSourceId)) {
            return HttpResponse.notFound();
        }
        return HttpResponse.noContent();
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
