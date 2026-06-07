package campidelli.liverecpi.mixer.adapters.inbound.http.dto;

import campidelli.liverecpi.mixer.domain.model.DiscoveredMixer;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record MixerResponse(
    String type,
    String name,
    boolean isOnline,
    String ipAddress,
    int port) {

  public static MixerResponse fromDomain(DiscoveredMixer domainMixer) {
    return switch (domainMixer) {
      case DiscoveredMixer.AvailableMixer online -> new MixerResponse(
          online.descriptor().type(),
          online.descriptor().name(),
          true,
          online.connection().ipAddress(),
          online.connection().port());
      case DiscoveredMixer.UnavailableMixer offline -> new MixerResponse(
          offline.descriptor().type(),
          offline.descriptor().name(),
          false,
          null,
          0);
    };
  }
}