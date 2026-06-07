package campidelli.liverecpi.mixer.domain.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Mixer {

    private final MixerDescriptor descriptor;
    private final MixerConnection connection;
    private final List<Channel> channels;

    public Mixer(MixerDescriptor descriptor, MixerConnection connection, List<Channel> channels) {
        this.descriptor = requireDescriptor(descriptor);
        this.connection = requireConnection(connection);
        if (channels == null) {
            throw new IllegalArgumentException("Channels list cannot be null.");
        }
        this.channels = List.copyOf(channels);
    }

    public Mixer(MixerDescriptor descriptor, MixerConnection connection) {
        this(descriptor, connection, createChannels(requireDescriptor(descriptor).numberOfChannels()));
    }

    public MixerDescriptor descriptor() {
        return descriptor;
    }

    public MixerConnection connection() {
        return connection;
    }

    public List<Channel> channels() {
        return channels;
    }

    public String id() {
        return String.join("|", descriptor.type(), connection.ipAddress());
    }

    public Channel channelAt(int index) {
        return channels.stream()
                .filter(c -> c.index() == index)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Channel index %d does not exist on mixer type '%s'.", index, descriptor.type())));
    }

    private static List<Channel> createChannels(int numberOfChannels) {
        if (numberOfChannels < 0) {
            throw new IllegalArgumentException("numberOfChannels cannot be negative.");
        }

        List<Channel> channels = new ArrayList<>(numberOfChannels);
        for (int index = 1; index <= numberOfChannels; index++) {
            channels.add(new Channel(index));
        }
        return channels;
    }

    private static MixerDescriptor requireDescriptor(MixerDescriptor descriptor) {
        return Objects.requireNonNull(descriptor, "descriptor cannot be null.");
    }

    private static MixerConnection requireConnection(MixerConnection connection) {
        return Objects.requireNonNull(connection, "connection cannot be null.");
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof Mixer other)) {
            return false;
        }
        return descriptor.equals(other.descriptor)
                && connection.equals(other.connection)
                && channels.equals(other.channels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(descriptor, connection, channels);
    }

    @Override
    public String toString() {
        return "Mixer[descriptor=" + descriptor + ", connection=" + connection + ", channels=" + channels + "]";
    }
}