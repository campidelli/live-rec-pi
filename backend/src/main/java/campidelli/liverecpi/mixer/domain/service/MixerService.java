package campidelli.liverecpi.mixer.domain.service;

import java.util.List;

import campidelli.liverecpi.mixer.domain.model.DiscoveredMixer;
import campidelli.liverecpi.mixer.ports.inbound.GetMixersUseCase;
import campidelli.liverecpi.mixer.ports.outbound.MixerDiscoveryPort;
import jakarta.inject.Singleton;

@Singleton
public class MixerService implements GetMixersUseCase {
      private final List<MixerDiscoveryPort> discoveryPorts;

    public MixerService(List<MixerDiscoveryPort> discoveryPorts) {
        this.discoveryPorts = discoveryPorts;
    } 

    @Override
    public List<DiscoveredMixer> execute() {
        return discoveryPorts.stream()
                .flatMap(port -> port.discover().stream())
                .toList();
    }
}
