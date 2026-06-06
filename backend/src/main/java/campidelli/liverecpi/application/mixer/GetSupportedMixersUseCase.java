package campidelli.liverecpi.application.mixer;

import campidelli.liverecpi.domain.mixer.Mixer;
import campidelli.liverecpi.domain.mixer.OnlineDevice;
import campidelli.liverecpi.domain.mixer.ProbeSpecification;
import jakarta.inject.Singleton;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Singleton
public class GetSupportedMixersUseCase {

    private final DeviceDiscoveryPort discoveryPort;
    private final List<MixerPort> mixerPorts;
    private final OnlineMixerCache onlineMixerCache;

    public GetSupportedMixersUseCase(
            DeviceDiscoveryPort discoveryPort,
            List<MixerPort> mixerPorts,
            OnlineMixerCache onlineMixerCache) {
        this.discoveryPort = discoveryPort;
        this.mixerPorts = mixerPorts;
        this.onlineMixerCache = onlineMixerCache;
    }

    public List<SupportedMixerDTO> execute() {
        List<ProbeSpecification> mixerSpecs = mixerPorts.stream()
                .map(MixerPort::getProbeSpecification)
                .toList();
        List<OnlineDevice> onlineDevices = discoveryPort.discoverOnlineDevices(mixerSpecs);

        List<Mixer> onlineMixers = onlineDevices.stream()
                .flatMap(device -> mixerPorts.stream()
                        .map(mixerPort -> mixerPort.supports(device))
                        .filter(Optional::isPresent)
                        .map(Optional::get))
                .toList();
        onlineMixerCache.update(onlineMixers);

        List<SupportedMixerDTO> onlineMixersDTO = onlineMixers.stream()
                .map(mixer -> toOnlineDTO(mixer))
                .toList();

        List<SupportedMixerDTO> offlineMixersDTO = mixerPorts.stream()
                .filter(mixer -> onlineDevices.stream()
                        .noneMatch(device -> mixer.supports(device).isPresent()))
                .map(this::toOfflineDTO)
                .toList();

        return Stream.concat(onlineMixersDTO.stream(), offlineMixersDTO.stream())
                .sorted(Comparator.comparing(SupportedMixerDTO::name))
                .toList();
    }

    private SupportedMixerDTO toOnlineDTO(Mixer mixer) {
        return new SupportedMixerDTO(
                mixer.id(),
                mixer.name(),
                Optional.of(mixer.ipAddress()),
                Optional.of(mixer.port()));
    }

    private SupportedMixerDTO toOfflineDTO(MixerPort mixerPort) {
        return new SupportedMixerDTO(
                mixerPort.getDefaultModelKey(),
                mixerPort.getDefaultName(),
                Optional.empty(),
                Optional.empty());
    }
}