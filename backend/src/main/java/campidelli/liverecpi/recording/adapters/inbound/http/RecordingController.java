package campidelli.liverecpi.recording.adapters.inbound.http;

import campidelli.liverecpi.recording.adapters.inbound.http.dto.RecordingOptionsResponse;
import campidelli.liverecpi.recording.ports.inbound.GetRecordingOptionsUseCase;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/recordings")
public class RecordingController {

    private final GetRecordingOptionsUseCase getRecordingOptionsUseCase;

    public RecordingController(GetRecordingOptionsUseCase getRecordingOptionsUseCase) {
        this.getRecordingOptionsUseCase = getRecordingOptionsUseCase;
    }

    @Get("/options")
    public RecordingOptionsResponse getRecordingOptions() {
        return RecordingOptionsResponse.fromDomain(getRecordingOptionsUseCase.execute());
    }
}
