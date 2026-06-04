package campidelli.liverecpi.entrypoints.http;

import java.util.List;

import campidelli.liverecpi.application.mixer.SupportedMixerDTO;
import campidelli.liverecpi.application.mixer.ConnectToMixerUseCase;
import campidelli.liverecpi.application.mixer.ConnectedMixerDTO;
import campidelli.liverecpi.application.mixer.GetSupportedMixersUseCase;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;

@Controller("/mixers")
public class MixerController {

    private final GetSupportedMixersUseCase getMixersUseCase;
    private final ConnectToMixerUseCase connectToMixerUseCase;

    public MixerController(GetSupportedMixersUseCase getMixersUseCase,
        ConnectToMixerUseCase connectToMixerUseCase
    ) {
        this.getMixersUseCase = getMixersUseCase;
        this.connectToMixerUseCase = connectToMixerUseCase;
    }

    @Get
    public List<SupportedMixerDTO> getSupportedMixers() {
        return getMixersUseCase.execute();
    }

    @Post("/{mixerId}")
    @Status(HttpStatus.OK)
    public ConnectedMixerDTO connectToMixer(@PathVariable String mixerId) {
        return connectToMixerUseCase.execute(mixerId);
    }
}