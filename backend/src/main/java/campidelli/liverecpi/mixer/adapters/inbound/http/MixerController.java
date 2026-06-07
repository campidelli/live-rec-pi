package campidelli.liverecpi.mixer.adapters.inbound.http;

import java.util.List;

import campidelli.liverecpi.mixer.adapters.inbound.http.dto.MixerResponse;
import campidelli.liverecpi.mixer.ports.inbound.GetMixersUseCase;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/mixers")
public class MixerController {

    private final GetMixersUseCase getMixersUseCase;

    public MixerController(GetMixersUseCase getMixersUseCase) {
        this.getMixersUseCase = getMixersUseCase;
    }

    @Get
    public List<MixerResponse> listMixers() {
        return getMixersUseCase.execute().stream()
            .map(MixerResponse::fromDomain)
            .toList();
    }
}
