package campidelli.liverecpi.mixer.ports.outbound;

import java.util.List;

import campidelli.liverecpi.mixer.domain.model.Channel;
import campidelli.liverecpi.mixer.domain.model.MixerDescriptor;

public interface MixerGatewayPort {
    MixerDescriptor getDescriptor();

    List<Channel> fetchChannels(String ipAddress, int port);
}