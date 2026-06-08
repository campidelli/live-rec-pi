package campidelli.liverecpi.mixer.domain.service;

import java.util.List;

import campidelli.liverecpi.mixer.domain.model.Channel;
import campidelli.liverecpi.mixer.domain.model.ChannelMeterLevel;
import campidelli.liverecpi.mixer.domain.model.DiscoveredMixer;
import campidelli.liverecpi.mixer.ports.inbound.GetMixersUseCase;
import campidelli.liverecpi.mixer.ports.inbound.GetMeterLevelsCommand;
import campidelli.liverecpi.mixer.ports.inbound.GetMeterLevelsUseCase;
import campidelli.liverecpi.mixer.ports.inbound.ListChannelsCommand;
import campidelli.liverecpi.mixer.ports.inbound.ListChannelsUseCase;
import campidelli.liverecpi.mixer.ports.outbound.FetchMixerChannelsPort;
import campidelli.liverecpi.mixer.ports.outbound.FetchMixerMeterLevelsPort;
import campidelli.liverecpi.mixer.ports.outbound.DiscoverMixerPort;
import jakarta.inject.Singleton;

@Singleton
public class MixerService implements GetMixersUseCase, ListChannelsUseCase, GetMeterLevelsUseCase {

    private final List<DiscoverMixerPort> discoveryPorts;
    private final List<FetchMixerChannelsPort> channelPorts;
    private final List<FetchMixerMeterLevelsPort> meterPorts;

    public MixerService(
            List<DiscoverMixerPort> discoveryPorts,
            List<FetchMixerChannelsPort> channelPorts,
            List<FetchMixerMeterLevelsPort> meterPorts) {
        this.discoveryPorts = discoveryPorts;
        this.channelPorts = channelPorts;
        this.meterPorts = meterPorts;
    }

    @Override
    public List<DiscoveredMixer> execute() {
        return discoveryPorts.stream()
                .flatMap(port -> port.discover().stream())
                .toList();
    }

    @Override
    public List<Channel> execute(ListChannelsCommand command) {
        FetchMixerChannelsPort channelPort = getChannelPortByType(command.type());
        return channelPort.fetchChannels(command.ipAddress(), command.port());
    }

    @Override
    public List<ChannelMeterLevel> execute(GetMeterLevelsCommand command) {
        FetchMixerMeterLevelsPort meterPort = getMeterPortByType(command.type());
        return meterPort.fetchMeterLevels(command.ipAddress(), command.port());
    }

    private FetchMixerChannelsPort getChannelPortByType(String type) {
        return channelPorts.stream()
                .filter(candidate -> candidate.mixerType().equals(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported mixer type for channels: " + type));
    }

    private FetchMixerMeterLevelsPort getMeterPortByType(String type) {
        return meterPorts.stream()
                .filter(candidate -> candidate.mixerType().equals(type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported mixer type for meter levels: " + type));
    }
}
