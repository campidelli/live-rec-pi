package campidelli.liverecpi.application.mixer;

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
    private final List<MixerPort> supportedMixers;
    private final SupportedMixerCache mixerCache;

    public GetSupportedMixersUseCase(
            DeviceDiscoveryPort discoveryPort, 
            List<MixerPort> supportedMixers, 
            SupportedMixerCache mixerCache) {
        this.discoveryPort = discoveryPort;
        this.supportedMixers = supportedMixers;
        this.mixerCache = mixerCache;
    }

  public List<SupportedMixerDTO> execute() {
    List<ProbeSpecification> specs = supportedMixers.stream()
            .map(MixerPort::getProbeSpecification)
            .toList();
    List<OnlineDevice> onlineDevices = discoveryPort.discoverOnlineDevices(specs);

    List<SupportedMixerDTO> onlineMixers = onlineDevices.stream()
        .flatMap(device -> supportedMixers.stream()
            .filter(mixer -> mixer.supports(device.modelSignature()))
            .map(mixer -> toOnlineDTO(mixer, device)))
        .toList();

    List<SupportedMixerDTO> offlineMixers = supportedMixers.stream()
        .filter(mixer -> onlineDevices.stream()
            .noneMatch(device -> mixer.supports(device.modelSignature())))
        .map(this::toOfflineDTO)
        .toList();

    List<SupportedMixerDTO> sortedFinalList = Stream.concat(onlineMixers.stream(), offlineMixers.stream())
        .sorted(Comparator.comparing(SupportedMixerDTO::displayName))
        .toList();
  
    mixerCache.update(sortedFinalList);

    return sortedFinalList;
  }

  private SupportedMixerDTO toOnlineDTO(MixerPort mixer, OnlineDevice device) {
    String uniqueId = String.join("|", mixer.getModelKey(), device.ipAddress());
    return new SupportedMixerDTO(
        uniqueId,
        mixer.getModelKey(),
        mixer.getDisplayName(),
        Optional.of(device.ipAddress()),
        Optional.of(device.port()));
  }

  private SupportedMixerDTO toOfflineDTO(MixerPort mixer) {
    String uniqueId = String.join("|", mixer.getModelKey(), "offline");
    return new SupportedMixerDTO(
        uniqueId,
        mixer.getModelKey(),
        mixer.getDisplayName(),
        Optional.empty(),
        Optional.empty());
  }
}