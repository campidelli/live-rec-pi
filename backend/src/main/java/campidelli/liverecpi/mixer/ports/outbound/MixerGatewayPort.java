package campidelli.liverecpi.mixer.ports.outbound;

import java.util.List;

import campidelli.liverecpi.mixer.domain.model.Channel;

public interface MixerGatewayPort extends MixerRegistryPort {
    List<Channel> fetchChannels(String ipAddress, int port);
}