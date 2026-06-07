package campidelli.liverecpi.mixer.adapters.inbound.http.dto;

import campidelli.liverecpi.mixer.ports.inbound.ListChannelsCommand;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record ListChannelsRequest(
                String type,
                String ipAddress,
                int port) {

        public ListChannelsCommand toCommand() {
                return new ListChannelsCommand(type, ipAddress, port);
        }
}