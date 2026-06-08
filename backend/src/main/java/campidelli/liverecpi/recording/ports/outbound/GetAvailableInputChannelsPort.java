package campidelli.liverecpi.recording.ports.outbound;

public interface GetAvailableInputChannelsPort {

    int getAvailableInputChannels(String mixerType);
}
