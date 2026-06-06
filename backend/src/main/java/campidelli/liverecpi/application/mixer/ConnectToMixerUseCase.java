package campidelli.liverecpi.application.mixer;

import jakarta.inject.Singleton;
import java.util.List;

import campidelli.liverecpi.domain.mixer.Channel;
import campidelli.liverecpi.domain.mixer.Mixer;

@Singleton
public class ConnectToMixerUseCase {

  private final List<MixerPort> mixerPorts;
  private final OnlineMixerCache mixerCache;

  public ConnectToMixerUseCase(List<MixerPort> mixerPorts, OnlineMixerCache mixerCache) {
    this.mixerPorts = mixerPorts;
    this.mixerCache = mixerCache;
  }

  public ConnectedMixerDTO execute(String mixerId) {
    Mixer chosenMixer = mixerCache.getById(mixerId)
        .orElseThrow(() -> new IllegalArgumentException("Mixer ID not found or cache expired. Run discovery again."));

    MixerPort mixerPort = mixerPorts.stream()
        .filter(port -> port.getDefaultModelKey().equals(chosenMixer.modelKey()))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Unsupported driver model type: " + chosenMixer.modelKey()));

    Mixer connectedMixer = mixerPort.connect(chosenMixer);

    return toConnectedMixerDTO(mixerId, connectedMixer);
  }

  private ConnectedMixerDTO toConnectedMixerDTO(String mixerId, Mixer mixer) {
    List<ChannelDTO> channelDTOs = mixer.channels().orElse(List.of()).stream()
        .map(this::toChannelDTO)
        .toList();

    return new ConnectedMixerDTO(
        mixerId,
        mixer.modelKey(),
        mixer.name(),
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