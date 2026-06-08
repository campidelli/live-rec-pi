package campidelli.liverecpi.mixer.domain.service;

import java.util.List;

import campidelli.liverecpi.mixer.domain.model.Channel;
import campidelli.liverecpi.mixer.domain.model.DiscoveredMixer;
import campidelli.liverecpi.mixer.ports.inbound.GetMixersUseCase;
import campidelli.liverecpi.mixer.ports.inbound.ListChannelsCommand;
import campidelli.liverecpi.mixer.ports.inbound.ListChannelsUseCase;
import campidelli.liverecpi.mixer.ports.outbound.MixerDiscoveryPort;
import campidelli.liverecpi.mixer.ports.outbound.MixerGatewayPort;
import jakarta.inject.Singleton;

@Singleton
public class MixerService implements GetMixersUseCase, ListChannelsUseCase {

    private final List<MixerDiscoveryPort> discoveryPorts;
    private final List<MixerGatewayPort> gatewayPorts;

    public MixerService(List<MixerDiscoveryPort> discoveryPorts, List<MixerGatewayPort> gatewayPorts) {
        this.discoveryPorts = discoveryPorts;
        this.gatewayPorts = gatewayPorts;
    }

    @Override
    public List<DiscoveredMixer> execute() {
        return discoveryPorts.stream()
                .flatMap(port -> port.discover().stream())
                .toList();
    }

    @Override
    public List<Channel> execute(ListChannelsCommand command) {
        MixerGatewayPort gateway = gatewayPorts.stream()
            .filter(candidate -> candidate.getDescriptor().type().equals(command.type()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported mixer type: " + command.type()));

        return gateway.fetchChannels(command.ipAddress(), command.port());
    }
}
