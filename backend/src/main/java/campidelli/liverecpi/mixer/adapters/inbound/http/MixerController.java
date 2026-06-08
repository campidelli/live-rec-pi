package campidelli.liverecpi.mixer.adapters.inbound.http;

import java.util.List;

import campidelli.liverecpi.mixer.adapters.inbound.http.dto.ChannelResponse;
import campidelli.liverecpi.mixer.adapters.inbound.http.dto.ListChannelsRequest;
import campidelli.liverecpi.mixer.adapters.inbound.http.dto.MixerResponse;
import campidelli.liverecpi.mixer.ports.inbound.GetMixersUseCase;
import campidelli.liverecpi.mixer.ports.inbound.ListChannelsUseCase;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;

@Controller("/mixers")
public class MixerController {

    private final GetMixersUseCase getMixersUseCase;
    private final ListChannelsUseCase listChannelsUseCase;

    public MixerController(GetMixersUseCase getMixersUseCase, ListChannelsUseCase listChannelsUseCase) {
        this.getMixersUseCase = getMixersUseCase;
        this.listChannelsUseCase = listChannelsUseCase;
    }

    @Get
    public List<MixerResponse> listMixers() {
        return getMixersUseCase.execute().stream()
            .map(MixerResponse::fromDomain)
            .toList();
    }

    @Post("/channels")
    public List<ChannelResponse> listChannels(@Body ListChannelsRequest request) {
        return listChannelsUseCase.execute(request.toCommand()).stream()
                .map(ChannelResponse::fromDomain)
                .toList();
    }
}
