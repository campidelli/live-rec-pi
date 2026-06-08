package campidelli.liverecpi.mixer.ports.outbound;

import java.util.List;

import campidelli.liverecpi.mixer.domain.model.Channel;

public interface FetchMixerChannelsPort {
    String mixerType();

    List<Channel> fetchChannels(String ipAddress, int port);
}
