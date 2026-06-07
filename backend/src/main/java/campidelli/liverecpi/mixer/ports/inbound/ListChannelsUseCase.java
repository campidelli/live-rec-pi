package campidelli.liverecpi.mixer.ports.inbound;

import java.util.List;

import campidelli.liverecpi.mixer.domain.model.Channel;

public interface ListChannelsUseCase {
    List<Channel> execute(ListChannelsCommand command);
}