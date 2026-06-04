package campidelli.liverecpi.application.mixer;

import jakarta.inject.Singleton;
import java.util.List;

import campidelli.liverecpi.domain.mixer.Channel;
import campidelli.liverecpi.domain.mixer.Mixer;

@Singleton
public class ConnectToMixerUseCase {

  private final List<MixerPort> supportedMixers;
  private final SupportedMixerCache mixerCache;

  public ConnectToMixerUseCase(List<MixerPort> supportedMixers, SupportedMixerCache mixerCache) {
    this.supportedMixers = supportedMixers;
    this.mixerCache = mixerCache;
  }

  public ConnectedMixerDTO execute(String mixerId) {
    SupportedMixerDTO chosenMixer = mixerCache.getById(mixerId)
        .orElseThrow(() -> new IllegalArgumentException("Mixer ID not found or cache expired. Run discovery again."));

    if (!chosenMixer.isOnline()) {
      throw new IllegalStateException("Cannot connect to an offline mixer instance.");
    }

    MixerPort selectedMixerPort = supportedMixers.stream()
        .filter(port -> port.getModelKey().equalsIgnoreCase(chosenMixer.modelKey()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported driver model type: " + chosenMixer.modelKey()));

    String ipAddress = chosenMixer.ipAddress().orElseThrow();
    int port = chosenMixer.port().orElseThrow();

    Mixer connectedMixer = selectedMixerPort.connect(ipAddress, port);

    return toConnectedMixerDTO(mixerId, connectedMixer);
  }

  private ConnectedMixerDTO toConnectedMixerDTO(String mixerId, Mixer mixer) {
    List<ChannelDTO> channelDTOs = mixer.channels().stream()
        .map(this::toChannelDTO)
        .toList();

    return new ConnectedMixerDTO(
        mixerId,
        mixer.modelKey(),
        mixer.displayName(),
        mixer.ipAddress(),
        mixer.port(),
        channelDTOs);
  }

  private ChannelDTO toChannelDTO(Channel channel) {
    return new ChannelDTO(
        channel.index(),
        channel.name(),
        channel.type(),
        channel.color().value().name(),
        channel.color().inverted());
  }
}