package campidelli.liverecpi.mixer.ports.inbound;

import java.util.List;

import campidelli.liverecpi.mixer.domain.model.DiscoveredMixer;

public interface GetMixersUseCase {
    List<DiscoveredMixer> execute();
}