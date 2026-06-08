package campidelli.liverecpi.mixer.adapters.inbound.http.dto;

import campidelli.liverecpi.mixer.domain.model.Channel;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ChannelResponse(
    int index,
    String name,
    String colorHexCode,
    boolean isColorInverted) {

  public static ChannelResponse fromDomain(Channel channel) {
        return new ChannelResponse(
            channel.index(),
            channel.name().orElse(null),
            channel.color().map(Channel.Color::hexCode).orElse(null),
            channel.color().map(Channel.Color::inverted).orElse(false)
        );
  }
}
